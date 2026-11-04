#!/usr/bin/env python3
"""
Antigravity Live Quota & Session Sync Service (sync_usage.py)
Queries the local Antigravity Language Server ConnectRPC endpoints:
  - /exa.language_server_pb.LanguageServerService/RetrieveUserQuotaSummary
  - /exa.language_server_pb.LanguageServerService/GetUserStatus
Reads active model selection from antigravity_state.pbtxt and active conversation
tokens/steps from conversation_summaries.db + transcript.jsonl.
Writes /storage/emulated/10/Documents/Projects/.gemini/antigravity/usage_limits.json
and optionally serves HTTP JSON on 127.0.0.1:18765/quota for the Android app.
"""

import glob
import http.server
import json
import os
import re
import socketserver
import sqlite3
import sys
import threading
import time
import urllib.request
from datetime import datetime, timezone

AGY_DIR = "/storage/emulated/10/Documents/Projects/.gemini/antigravity"
OUTPUT_JSON = os.path.join(AGY_DIR, "usage_limits.json")
STATE_PBTXT = os.path.join(AGY_DIR, "antigravity_state.pbtxt")
DB_PATH = os.path.join(AGY_DIR, "conversation_summaries.db")
BRAIN_DIR = os.path.join(AGY_DIR, "brain")
HTTP_PORT = 18765


def discover_ls_credentials():
    """Find ANTIGRAVITY_LS_ADDRESS and ANTIGRAVITY_CSRF_TOKEN from env or /proc."""
    addr = os.environ.get("ANTIGRAVITY_LS_ADDRESS", "")
    csrf = os.environ.get("ANTIGRAVITY_CSRF_TOKEN", "")
    if addr and csrf:
        return addr, csrf

    for env_path in glob.glob("/proc/[0-9]*/environ"):
        try:
            with open(env_path, "rb") as f:
                raw = f.read().decode("utf-8", errors="ignore")
            if "ANTIGRAVITY_LS_ADDRESS=" in raw:
                found_addr = ""
                found_csrf = ""
                for item in raw.split("\0"):
                    if item.startswith("ANTIGRAVITY_LS_ADDRESS="):
                        found_addr = item.split("=", 1)[1]
                    elif item.startswith("ANTIGRAVITY_CSRF_TOKEN="):
                        found_csrf = item.split("=", 1)[1]
                if found_addr and found_csrf:
                    return found_addr, found_csrf
        except Exception:
            continue
    return "", ""


def call_ls_rpc(addr, csrf, method, payload=None):
    if payload is None:
        payload = {}
    url = f"http://{addr}/exa.language_server_pb.LanguageServerService/{method}"
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "x-codeium-csrf-token": csrf,
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=5) as resp:
        return json.loads(resp.read().decode("utf-8"))


def format_countdown(reset_iso):
    if not reset_iso:
        return "—"
    try:
        iso_clean = reset_iso.replace("Z", "+00:00")
        dt = datetime.fromisoformat(iso_clean)
        now = datetime.now(timezone.utc)
        diff_sec = int((dt - now).total_seconds())
        if diff_sec <= 0:
            return "Ahora"
        days = diff_sec // 86400
        hours = (diff_sec % 86400) // 3600
        mins = (diff_sec % 3600) // 60
        if days > 0:
            return f"{days}d {hours}h"
        if hours > 0:
            return f"{hours}h {mins}m"
        return f"{mins}m"
    except Exception:
        return "—"


def read_selected_model_alias():
    try:
        if os.path.exists(STATE_PBTXT):
            with open(STATE_PBTXT, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
            m = re.search(r"last_selected_agent_model:\s*(\S+)", content)
            if m:
                return m.group(1).strip()
    except Exception:
        pass
    return ""


def read_active_session_info():
    info = {
        "conversationId": "",
        "title": "Antigravity Workspace",
        "runStatus": "IDLE",
        "stepCount": 0,
        "totalStepsAll": 0,
        "contextTokens": 0,
        "inputTokens": 0,
        "outputTokens": 0,
        "cacheReadTokens": 0,
        "recentSessions": [],
    }
    try:
        if os.path.exists(DB_PATH):
            conn = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
            cur = conn.cursor()
            cur.execute(
                """
                SELECT conversation_id, title, step_count, status, not_fully_idle, last_modified_time
                FROM conversation_summaries
                ORDER BY last_modified_time DESC LIMIT 10
                """
            )
            rows = cur.fetchall()
            conn.close()
            total_steps = 0
            recent = []
            for idx, row in enumerate(rows):
                cid = row[0] or ""
                title = row[1] or "Antigravity Session"
                steps = int(row[2] or 0)
                raw_status = row[3] or ""
                not_idle = bool(row[4])
                last_mod = row[5] or ""
                total_steps += steps
                is_run = ("RUNNING" in raw_status) or not_idle
                if idx == 0:
                    info["conversationId"] = cid
                    info["title"] = title
                    info["stepCount"] = steps
                    info["runStatus"] = "RUNNING" if is_run else "IDLE"
                recent.append(
                    {
                        "id": cid,
                        "title": title,
                        "stepCount": steps,
                        "isRunning": is_run,
                        "lastModified": last_mod,
                    }
                )
            info["totalStepsAll"] = total_steps
            info["recentSessions"] = recent
    except Exception:
        pass

    try:
        cid = info["conversationId"]
        transcript_path = ""
        if cid:
            cand = os.path.join(BRAIN_DIR, cid, ".system_generated", "logs", "transcript.jsonl")
            if os.path.exists(cand):
                transcript_path = cand
        if not transcript_path and os.path.isdir(BRAIN_DIR):
            candidates = glob.glob(
                os.path.join(BRAIN_DIR, "*", ".system_generated", "logs", "transcript.jsonl")
            )
            if candidates:
                transcript_path = max(candidates, key=os.path.getmtime)

        if transcript_path and os.path.exists(transcript_path):
            in_tok = 0
            out_tok = 0
            cache_tok = 0
            active_ctx = 0
            steps = 0
            with open(transcript_path, "r", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    if not line.strip():
                        continue
                    steps += 1
                    try:
                        obj = json.loads(line)
                        it = int(obj.get("input_tokens", 0) or 0)
                        ot = int(obj.get("output_tokens", 0) or 0)
                        ct = int(obj.get("cache_read_tokens", 0) or 0)
                        if it == 0 and ot == 0 and ct == 0:
                            usage = obj.get("usage") or obj.get("token_usage") or {}
                            if isinstance(usage, dict):
                                it = int(usage.get("input_tokens", 0) or 0)
                                ot = int(usage.get("output_tokens", 0) or 0)
                                ct = int(usage.get("cache_read_tokens", 0) or 0)
                        in_tok += it
                        out_tok += ot
                        cache_tok += ct
                        if it > 0 or ct > 0:
                            active_ctx = it + ct
                    except Exception:
                        pass
            if steps > info["stepCount"]:
                info["stepCount"] = steps
            if in_tok == 0 and out_tok == 0:
                size_bytes = os.path.getsize(transcript_path)
                in_tok = max(1200, int(size_bytes / 3.8))
                out_tok = max(400, steps * 145)
                active_ctx = in_tok
            info["inputTokens"] = in_tok
            info["outputTokens"] = out_tok
            info["cacheReadTokens"] = cache_tok
            info["contextTokens"] = active_ctx if active_ctx > 0 else in_tok
            if time.time() - os.path.getmtime(transcript_path) < 12:
                info["runStatus"] = "RUNNING"
    except Exception:
        pass

    return info


def infer_group_for_model(model_label):
    lbl = (model_label or "").lower()
    if "argon" in lbl:
        return "Gemini 4 Argon"
    if "claude" in lbl or "gpt" in lbl or "sonnet" in lbl or "opus" in lbl:
        return "Claude and GPT models"
    return "Gemini Models"


def is_ls_alive(addr):
    """The LS port is a tunnel opened by the Antigravity Android app; it disappears when the app closes."""
    if not addr:
        return False
    try:
        import socket
        host, port = addr.rsplit(":", 1)
        with socket.create_connection((host, int(port)), timeout=1.0):
            return True
    except Exception:
        return False


def build_usage_snapshot():
    addr, csrf = discover_ls_credentials()
    ls_alive = is_ls_alive(addr)
    if not ls_alive:
        addr, csrf = "", ""
    selected_alias = read_selected_model_alias()

    existing = {}
    if os.path.exists(OUTPUT_JSON):
        try:
            with open(OUTPUT_JSON, "r", encoding="utf-8") as f:
                existing = json.load(f)
        except Exception:
            existing = {}

    groups_out = []
    models_out = []
    active_model_label = existing.get("activeModelLabel", "Gemini 4 Argon (Medium)")
    plan_name = existing.get("planName", "Pro")

    if addr and csrf:
        try:
            quota_resp = call_ls_rpc(addr, csrf, "RetrieveUserQuotaSummary", {})
            raw_groups = (
                quota_resp.get("response", {}).get("groups", [])
                or quota_resp.get("groups", [])
            )
            for g in raw_groups:
                buckets_out = []
                for b in g.get("buckets", []):
                    rem_frac = float(b.get("remainingFraction", 1.0))
                    rem_pct = max(0, min(100, int(round(rem_frac * 100))))
                    used_pct = max(0, min(100, 100 - rem_pct))
                    reset_iso = b.get("resetTime", "")
                    buckets_out.append(
                        {
                            "bucketId": b.get("bucketId", ""),
                            "displayName": b.get("displayName", ""),
                            "window": b.get("window", ""),
                            "remainingFraction": rem_frac,
                            "remainingPercent": rem_pct,
                            "usedPercent": used_pct,
                            "resetTime": reset_iso,
                            "resetCountdown": format_countdown(reset_iso),
                        }
                    )
                groups_out.append(
                    {
                        "displayName": g.get("displayName", "Model Group"),
                        "description": g.get("description", ""),
                        "buckets": buckets_out,
                    }
                )
        except Exception:
            groups_out = existing.get("groups", [])

        try:
            status_resp = call_ls_rpc(addr, csrf, "GetUserStatus", {})
            user_status = status_resp.get("userStatus", {})
            plan_info = (
                user_status.get("planStatus", {}).get("planInfo", {})
            )
            if plan_info.get("planName"):
                plan_name = plan_info["planName"]
            configs = (
                user_status.get("cascadeModelConfigData", {}).get(
                    "clientModelConfigs", []
                )
            )
            for c in configs:
                alias = c.get("modelOrAlias", {}).get("model", "")
                label = c.get("label", "")
                model_id = c.get("modelId", "")
                q_info = c.get("quotaInfo", {})
                rem_frac = float(q_info.get("remainingFraction", 1.0))
                rem_pct = max(0, min(100, int(round(rem_frac * 100))))
                reset_iso = q_info.get("resetTime", "")
                if selected_alias and alias == selected_alias and label:
                    active_model_label = label
                models_out.append(
                    {
                        "label": label,
                        "modelId": model_id,
                        "alias": alias,
                        "remainingFraction": rem_frac,
                        "remainingPercent": rem_pct,
                        "usedPercent": 100 - rem_pct,
                        "resetTime": reset_iso,
                        "resetCountdown": format_countdown(reset_iso),
                    }
                )
        except Exception:
            models_out = existing.get("models", [])
    else:
        groups_out = existing.get("groups", [])
        models_out = existing.get("models", [])

    active_group_name = infer_group_for_model(active_model_label)
    session_info = read_active_session_info()
    if not ls_alive:
        session_info["runStatus"] = "IDLE"

    snapshot = {
        "updatedAtEpochMs": int(time.time() * 1000),
        "updatedAtIso": datetime.now(timezone.utc).isoformat(),
        "lsAlive": ls_alive,
        "planName": plan_name,
        "activeModelAlias": selected_alias,
        "activeModelLabel": active_model_label,
        "activeGroupName": active_group_name,
        "groups": groups_out,
        "models": models_out,
        "session": session_info,
    }

    try:
        tmp_path = OUTPUT_JSON + ".tmp"
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(snapshot, f, indent=2)
        os.replace(tmp_path, OUTPUT_JSON)
    except Exception:
        pass

    return snapshot


LATEST_SNAPSHOT = {}


class QuotaHttpHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/quota") or self.path == "/":
            try:
                data = build_usage_snapshot()
            except Exception:
                data = LATEST_SNAPSHOT
            body = json.dumps(data).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        return


def background_sync_loop(interval_sec=10):
    global LATEST_SNAPSHOT
    while True:
        try:
            LATEST_SNAPSHOT = build_usage_snapshot()
        except Exception:
            pass
        time.sleep(interval_sec)


class ReusableTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    global LATEST_SNAPSHOT
    LATEST_SNAPSHOT = build_usage_snapshot()
    if "--once" in sys.argv:
        print(json.dumps(LATEST_SNAPSHOT, indent=2))
        return

    t = threading.Thread(target=background_sync_loop, args=(3,), daemon=True)
    t.start()

    try:
        with ReusableTCPServer(("0.0.0.0", HTTP_PORT), QuotaHttpHandler) as httpd:
            httpd.serve_forever()
    except OSError:
        # Port already bound by another sync_usage instance; stay in sync loop
        while True:
            time.sleep(3)
            try:
                LATEST_SNAPSHOT = build_usage_snapshot()
            except Exception:
                pass


if __name__ == "__main__":
    main()
