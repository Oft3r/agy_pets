package com.droid.agypets.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.navigation3.runtime.NavKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droid.agypets.data.AgyUsageData
import com.droid.agypets.data.AgyUsageRepository
import com.droid.agypets.data.DefaultPetSettingsRepository
import com.droid.agypets.data.PetPreferences
import com.droid.agypets.data.PetSettings
import com.droid.agypets.engine.Direction
import com.droid.agypets.engine.PetState
import com.droid.agypets.service.FloatingPetService
import com.droid.agypets.ui.view.PixelPetView
import com.droid.agypets.util.AntigravityDetector
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  onItemClick: ((NavKey) -> Unit)? = null,
  context: Context = LocalContext.current,
  viewModel: MainScreenViewModel = viewModel {
    val prefs = PetPreferences(context)
    val usageRepo = AgyUsageRepository(context)
    MainScreenViewModel(DefaultPetSettingsRepository(prefs), usageRepo)
  }
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val usageData by viewModel.usageState.collectAsStateWithLifecycle()

  // Periodically re-check permissions when screen is active
  LaunchedEffect(Unit) {
    while (true) {
      val canDraw = Settings.canDrawOverlays(context)
      viewModel.updateOverlayPermission(canDraw)

      val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
      } else {
        true
      }
      viewModel.updateStoragePermission(hasStorage)

      val hasUsageAccess = AntigravityDetector.hasUsageAccessPermission(context)
      viewModel.updateUsageStatsPermission(hasUsageAccess)

      delay(1500)
    }
  }

  when (val state = uiState) {
    is MainScreenUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFF00E5FF))
      }
    }
    is MainScreenUiState.Success -> {
      MainScreenContent(
        settings = state.settings,
        usageData = usageData,
        onTogglePet = { enable ->
          viewModel.togglePet(enable)
          if (enable) {
            FloatingPetService.start(context)
          } else {
            FloatingPetService.stop(context)
          }
        },
        onResetPosition = {
          FloatingPetService.reset(context)
        },
        onScaleChange = { scale ->
          viewModel.updateScale(scale)
          FloatingPetService.reload(context)
        },
        onSpeedChange = { speed ->
          viewModel.updateSpeed(speed)
          FloatingPetService.reload(context)
        },
        onZeroGravityToggle = { enabled ->
          viewModel.toggleZeroGravity(enabled)
          FloatingPetService.reload(context)
        },
        onToggleUsageBadge = { show ->
          viewModel.toggleUsageBadge(show)
          FloatingPetService.reload(context)
        },
        onToggleStartOnBoot = { enable ->
          viewModel.toggleStartOnBoot(enable)
        },
        onToggleAutoShowAntigravity = { enable ->
          viewModel.toggleAutoShowWithAntigravity(enable)
          FloatingPetService.reload(context)
        },
        onRequestUsageStatsPermission = {
          val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
          context.startActivity(intent)
        },
        onSelectContextLimit = { limit ->
          viewModel.updateContextLimit(limit)
        },
        onRefreshUsage = {
          viewModel.refreshUsage()
        },
        onRequestOverlayPermission = {
          val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
          )
          context.startActivity(intent)
        },
        onRequestStoragePermission = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
              val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
              }
              context.startActivity(intent)
            } catch (e: Exception) {
              val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
              context.startActivity(intent)
            }
          }
        },
        modifier = modifier
      )
    }
    is MainScreenUiState.Error -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Error: ${state.throwable.message}")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreenContent(
  settings: PetSettings,
  usageData: AgyUsageData,
  onTogglePet: (Boolean) -> Unit,
  onResetPosition: () -> Unit,
  onScaleChange: (Float) -> Unit,
  onSpeedChange: (Float) -> Unit,
  onZeroGravityToggle: (Boolean) -> Unit,
  onToggleUsageBadge: (Boolean) -> Unit,
  onToggleStartOnBoot: (Boolean) -> Unit,
  onToggleAutoShowAntigravity: (Boolean) -> Unit,
  onSelectContextLimit: (Long) -> Unit,
  onRefreshUsage: () -> Unit,
  onRequestOverlayPermission: () -> Unit,
  onRequestStoragePermission: () -> Unit,
  onRequestUsageStatsPermission: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()
  var showAllModels by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "✦ AntiGravity Pets & Quotas",
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = Color(0xFF00E5FF)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color(0xFF0A0E27)
        )
      )
    },
    containerColor = Color(0xFF060919)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .verticalScroll(scrollState),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

      // Permission Warning Card if overlay not granted
      if (!settings.hasOverlayPermission) {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1826)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF43F5E), RoundedCornerShape(12.dp))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(text = "⚠️", fontSize = 18.sp)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Screen Overlay Permission Required",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFDA4AF),
                fontSize = 15.sp
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "To let the AntiGravity mascot walk across your screen on GoogleBook OS, please grant the 'Display over other apps' permission.",
              color = Color(0xFFFECDD3),
              fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = onRequestOverlayPermission,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
            ) {
              Text("Grant Overlay Permission")
            }
          }
        }
      }

      // Sync & Storage Status Card
      val isSynced = settings.hasStoragePermission || usageData.isLiveConnected
      Card(
        colors = CardDefaults.cardColors(
          containerColor = if (isSynced) Color(0xFF0F1E2E) else Color(0xFF261D10)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(
            1.dp,
            if (isSynced) Color(0xFF38BDF8) else Color(0xFFFBBF24),
            RoundedCornerShape(12.dp)
          )
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = if (isSynced)
                "⚡ Connected to Antigravity LanguageServer"
              else
                "⚠️ Storage Access for .gemini Data",
              fontWeight = FontWeight.Bold,
              color = if (isSynced) Color(0xFF38BDF8) else Color(0xFFFBBF24),
              fontSize = 15.sp
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = if (isSynced)
              "Reading live quotas by model group and active sessions directly from Antigravity (${usageData.planName} Plan)."
            else
              "Grant storage access to read active session tokens and local summaries from the .gemini directory.",
            color = Color(0xFFCBD5E1),
            fontSize = 12.sp
          )
          if (!settings.hasStoragePermission) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = onRequestStoragePermission,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
            ) {
              Text("Grant Full Storage Access")
            }
          }
        }
      }

      // ANTIGRAVITY QUOTA GROUPS DASHBOARD
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C132E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "QUOTAS BY MODEL GROUP (${usageData.planName.uppercase(Locale.US)} PLAN)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = if (usageData.isAgentRunning) "⚡ Agent Working" else "💤 Agent Idle",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (usageData.isAgentRunning) Color(0xFF4ADE80) else Color(0xFF94A3B8)
              )
            }
            OutlinedButton(
              onClick = onRefreshUsage,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
            ) {
              Text("Refresh 🔄", fontSize = 12.sp)
            }
          }

          Spacer(modifier = Modifier.height(6.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFF141D42), RoundedCornerShape(8.dp))
              .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Column {
              Text(
                text = "🎯 Selected Model: ${usageData.activeModelLabel}",
                fontSize = 12.sp,
                color = Color(0xFFA78BFA),
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Session: ${usageData.activeSessionTitle} (${usageData.activeSessionSteps} steps)",
                fontSize = 11.sp,
                color = Color(0xFFCBD5E1)
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Render each Antigravity Quota Group card
          usageData.quotaGroups.forEach { group ->
            val isActiveGroup = group.displayName.equals(usageData.activeGroupName, ignoreCase = true)
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .background(
                  if (isActiveGroup) Color(0xFF10193A) else Color(0xFF070B1E),
                  RoundedCornerShape(10.dp)
                )
                .border(
                  1.dp,
                  if (isActiveGroup) Color(0xFF38BDF8) else Color(0xFF1E293B),
                  RoundedCornerShape(10.dp)
                )
                .padding(12.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = if (isActiveGroup) "★ ${group.displayName}" else "◆ ${group.displayName}",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isActiveGroup) Color(0xFF38BDF8) else Color.White
                )
                if (isActiveGroup) {
                  Text(
                    text = "ACTIVE GROUP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4ADE80),
                    fontFamily = FontFamily.Monospace
                  )
                }
              }

              if (group.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = group.description,
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8)
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              group.buckets.forEachIndexed { idx, bucket ->
                val barColor = when {
                  bucket.usedPercent < 60 -> Color(0xFF4ADE80)
                  bucket.usedPercent < 85 -> Color(0xFFFBBF24)
                  else -> Color(0xFFF87171)
                }
                val icon = if (bucket.window == "5h") "⏱️" else "📅"

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "$icon ${bucket.displayName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0)
                  )
                  Text(
                    text = "${bucket.usedPercent}% used (${bucket.remainingPercent}% left)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                  )
                }

                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                  progress = { (bucket.usedPercent / 100f).coerceIn(0f, 1f) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                  color = barColor,
                  trackColor = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = if (bucket.window == "5h") "5-Hour Reset:" else "Weekly Reset:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                  )
                  Text(
                    text = "Resets in ${bucket.resetCountdown}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                  )
                }

                if (idx < group.buckets.size - 1) {
                  Spacer(modifier = Modifier.height(10.dp))
                }
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

          // Toggleable per-model list (all 13 models)
          if (usageData.modelQuotas.isNotEmpty()) {
            OutlinedButton(
              onClick = { showAllModels = !showAllModels },
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1))
            ) {
              Text(
                text = if (showAllModels)
                  "▲ Hide individual models list (${usageData.modelQuotas.size})"
                else
                  "▼ View all ${usageData.modelQuotas.size} Antigravity models individually",
                fontSize = 12.sp
              )
            }

            if (showAllModels) {
              Spacer(modifier = Modifier.height(8.dp))
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(Color(0xFF070B1E), RoundedCornerShape(8.dp))
                  .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                usageData.modelQuotas.forEach { m ->
                  val isCurrent = m.name.equals(usageData.activeModelLabel, ignoreCase = true)
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = if (isCurrent) "★ ${m.name}" else "• ${m.name}",
                      fontSize = 11.sp,
                      fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                      color = if (isCurrent) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                      modifier = Modifier.weight(1f)
                    )
                    Text(
                      text = "${m.usedPercent}% used | ↻ ${m.resetCountdown}",
                      fontSize = 11.sp,
                      fontFamily = FontFamily.Monospace,
                      color = if (m.usedPercent >= 85) Color(0xFFF87171) else Color(0xFF4ADE80)
                    )
                  }
                }
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

          // Context Window Quota Progress
          val quotaColor = when {
            usageData.contextPercent < 60 -> Color(0xFF38BDF8)
            usageData.contextPercent < 85 -> Color(0xFFFBBF24)
            else -> Color(0xFFF87171)
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Context Window (Tokens):",
              fontSize = 12.sp,
              color = Color(0xFF94A3B8)
            )
            Text(
              text = String.format(
                Locale.US,
                "%.1fk / %.0fk (%d%%)",
                usageData.contextTokens / 1000.0,
                usageData.contextLimit / 1000.0,
                usageData.contextPercent
              ),
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = quotaColor
            )
          }

          Spacer(modifier = Modifier.height(4.dp))
          LinearProgressIndicator(
            progress = { (usageData.contextPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
              .fillMaxWidth()
              .height(7.dp),
            color = quotaColor,
            trackColor = Color(0xFF1E293B)
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Token Breakdown
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFF090D21), RoundedCornerShape(8.dp))
              .padding(8.dp)
          ) {
            Text(
              text = String.format(
                Locale.US,
                "📥 In: %.1fk   |   📤 Out: %.1fk   |   ⚡ Cache: %.1fM",
                usageData.inputTokens / 1000.0,
                usageData.outputTokens / 1000.0,
                usageData.cacheTokens / 1_000_000.0
              ),
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              color = Color(0xFFCBD5E1)
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Service Status
          Text(
            text = "Status: ${usageData.cloudStatusText}",
            fontSize = 12.sp,
            color = if (usageData.cloudStatusOk) Color(0xFF4ADE80) else Color(0xFFF87171)
          )
        }
      }

      // Live Mascot Interactive Preview Card
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1236)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "LIVE MASCOT PREVIEW",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF38BDF8),
            fontFamily = FontFamily.Monospace
          )
          Spacer(modifier = Modifier.height(8.dp))

          Box(
            modifier = Modifier
              .size(160.dp, 100.dp)
              .background(Color(0xFF060919), RoundedCornerShape(8.dp))
              .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
          ) {
            var previewTicks by remember { mutableStateOf(0L) }
            var previewFrame by remember { mutableStateOf(0) }

            LaunchedEffect(Unit) {
              while (true) {
                delay(100)
                previewTicks++
                previewFrame = (previewFrame + 1) % 4
              }
            }

            AndroidView(
              factory = { ctx ->
                PixelPetView(ctx).apply {
                  currentState = if (usageData.isAgentRunning) PetState.WORKING else PetState.WALK
                  currentDirection = Direction.RIGHT
                }
              },
              update = { view ->
                val previewState = if (usageData.isAgentRunning) {
                  PetState.WORKING
                } else if (previewTicks % 40 < 20) {
                  PetState.WALK
                } else {
                  PetState.FLOAT
                }

                view.updateAnimation(
                  state = previewState,
                  direction = Direction.RIGHT,
                  frame = previewFrame,
                  ticks = previewTicks,
                  quotaPercent = usageData.activePrimaryUsedPercent
                )
              },
              modifier = Modifier.size(72.dp)
            )
          }

          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = if (usageData.isAgentRunning) "⚡ Working Mode Active" else "AntiGravity Official Pixel Art Mascot",
            fontSize = 13.sp,
            color = if (usageData.isAgentRunning) Color(0xFF4ADE80) else Color(0xFF94A3B8)
          )
        }
      }

      // Master Controls & Visibility Card
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111843)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Desktop Mascot",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
              )
              Text(
                text = if (settings.isPetEnabled) "Active on Screen" else "Disabled",
                fontSize = 13.sp,
                color = if (settings.isPetEnabled) Color(0xFF34D399) else Color(0xFF94A3B8)
              )
            }
            Switch(
              checked = settings.isPetEnabled,
              onCheckedChange = { enable ->
                if (!settings.hasOverlayPermission) {
                  onRequestOverlayPermission()
                } else {
                  onTogglePet(enable)
                  if (enable) onResetPosition()
                }
              }
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Floating Status Pill Toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Floating Quota Pill",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFFCBD5E1)
              )
              Text(
                text = "Shows [⚡ ${usageData.activeGroupShortName} ${usageData.activePrimaryUsedPercent}% (${usageData.activePrimaryResetCountdown})] above pet",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
              )
            }
            Switch(
              checked = settings.showUsageBadge,
              onCheckedChange = onToggleUsageBadge
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Start on Boot Toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Start on System Boot",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFFCBD5E1)
              )
              Text(
                text = "Launch pet companion service automatically when PC turns on",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
              )
            }
            Switch(
              checked = settings.startOnBoot,
              onCheckedChange = onToggleStartOnBoot
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Auto-show when Antigravity is open Toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Auto-show / Auto-hide with Antigravity",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFFCBD5E1)
              )
              Text(
                text = "Automatically appear when com.google.android.apps.antigravity is open, and hide when closed",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
              )
            }
            Switch(
              checked = settings.autoShowWithAntigravity,
              onCheckedChange = onToggleAutoShowAntigravity
            )
          }

          if (settings.autoShowWithAntigravity && !settings.hasUsageStatsPermission) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF261D10), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(8.dp)
            ) {
              Column {
                Text(
                  text = "💡 Optional: Grant Usage Access",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFFBBF24)
                )
                Text(
                  text = "For instantaneous sub-second detection when switching app windows, grant 'Usage access'.",
                  fontSize = 10.5.sp,
                  color = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                  onClick = onRequestUsageStatsPermission,
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                  modifier = Modifier.height(32.dp)
                ) {
                  Text("Enable Usage Access in Settings", fontSize = 11.sp)
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Button(
            onClick = {
              if (!settings.hasOverlayPermission) {
                onRequestOverlayPermission()
              } else {
                onTogglePet(true)
                onResetPosition()
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (!settings.hasOverlayPermission) Color(0xFFF43F5E) else Color(0xFF00E5FF),
              contentColor = if (!settings.hasOverlayPermission) Color.White else Color(0xFF0A0E27)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
          ) {
            Text(
              text = if (!settings.hasOverlayPermission) "⚠️ Grant Screen Overlay Permission" else "🚀 Show Mascot on Desktop Now!",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
          }

          if (settings.isPetEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = onResetPosition,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("Reset Position to Bottom", color = Color(0xFF38BDF8))
            }
          }
        }
      }

      // Settings: Size & Gravity
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1336)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "MASCOT SETTINGS",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF38BDF8),
            fontFamily = FontFamily.Monospace
          )

          // Size Chips
          Text(text = "Mascot Size", fontSize = 14.sp, color = Color.White)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.5f to "Small", 2.0f to "Normal", 3.0f to "Large").forEach { (sc, label) ->
              FilterChip(
                selected = Math.abs(settings.petScale - sc) < 0.1f,
                onClick = { onScaleChange(sc) },
                label = { Text(label) }
              )
            }
          }

          // Zero-Gravity Toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(text = "Zero-Gravity Physics", color = Color.White, fontSize = 14.sp)
              Text(text = "Float gently in space when tossed", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            Switch(
              checked = settings.zeroGravity,
              onCheckedChange = onZeroGravityToggle
            )
          }

          // Context Limit Selector
          Text(text = "Context Window Token Limit", fontSize = 14.sp, color = Color.White)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(200_000L to "200k", 1_000_000L to "1M", 2_000_000L to "2M").forEach { (lim, label) ->
              FilterChip(
                selected = settings.contextWindowLimitTokens == lim,
                onClick = { onSelectContextLimit(lim) },
                label = { Text(label) }
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
