package com.zed.assistant.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import android.app.role.RoleManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zed.assistant.BuildConfig
import com.zed.assistant.ZedWakeWordService
import com.zed.assistant.ai.PermissionGateway
import com.zed.assistant.ai.ZedEngine
import com.zed.assistant.overlay.ZedOverlayService
import com.zed.assistant.screen.ScreenCaptureService
import com.zed.assistant.data.ZedPreferences
import com.zed.assistant.data.ZedVoiceProfiles
import com.zed.assistant.ui.components.GlassCard
import com.zed.assistant.ui.components.GlassChip
import com.zed.assistant.ui.components.GlassIconButton
import com.zed.assistant.ui.components.LiquidBackground
import com.zed.assistant.ui.components.SectionLabel
import com.zed.assistant.ui.components.StatusDot
import com.zed.assistant.ui.theme.LocalZedPalette
import com.zed.assistant.ui.theme.ZedThemeMode
import com.zed.assistant.ui.theme.palette
import com.zed.assistant.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Settings grouped around operator identity, voice, presence, device access,
 * data safety, and appearance. Every row opens a focused dialog rather than
 * scattering inputs across the page.
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  val context = LocalContext.current
  val palette = LocalZedPalette.current
  val prefs = remember {
    context.getSharedPreferences(ZedEngine.PREFS, Context.MODE_PRIVATE)
  }

  var apiKey by remember { mutableStateOf(prefs.getString(ZedEngine.KEY_API_KEY, "").orEmpty()) }
  var userName by remember { mutableStateOf(prefs.getString(ZedEngine.KEY_USER_NAME, "").orEmpty()) }
  var model by remember {
    mutableStateOf(prefs.getString(ZedEngine.KEY_MODEL, ZedEngine.DEFAULT_MODEL).orEmpty().ifBlank { ZedEngine.DEFAULT_MODEL })
  }
  var voice by remember {
    mutableStateOf(prefs.getString(ZedEngine.KEY_VOICE, ZedEngine.DEFAULT_VOICE).orEmpty().ifBlank { ZedEngine.DEFAULT_VOICE })
  }
  var voicePreset by remember {
    mutableStateOf(prefs.getString(ZedPreferences.KEY_VOICE_PRESET, "").orEmpty())
  }
  val voiceProfile = ZedVoiceProfiles.resolve(voicePreset, voice)

  val zedPrefs = remember { ZedPreferences(context) }
  val scope = rememberCoroutineScope()
  var wakeWordEnabled by remember { mutableStateOf(zedPrefs.wakeWordEnabled) }
  var overlayEnabled by remember { mutableStateOf(zedPrefs.overlayEnabled) }
  var ownerVoiceEnabled by remember { mutableStateOf(zedPrefs.isOwnerVoiceEnabled) }
  var overlayToy by remember { mutableStateOf(zedPrefs.overlayToy) }
  var soundEffectsEnabled by remember { mutableStateOf(zedPrefs.soundEffectsEnabled) }
  var screenContextEnabled by remember { mutableStateOf(zedPrefs.screenContextEnabled) }
  val screenCaptureLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val data = result.data
    if (result.resultCode == Activity.RESULT_OK && data != null) {
      ScreenCaptureService.start(context, result.resultCode, data)
      screenContextEnabled = true
      zedPrefs.screenContextEnabled = true
      viewModel.setScreenContext(true)
    } else {
      screenContextEnabled = false
      zedPrefs.screenContextEnabled = false
      viewModel.setScreenContext(false)
    }
  }

  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val liquidGlass by viewModel.liquidGlass.collectAsStateWithLifecycle()
  var activeDialog by rememberSaveable { mutableStateOf<String?>(null) }

  BackHandler { onBack() }

  LiquidBackground {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
      SettingsTopBar(onBack = onBack)

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(start = 18.dp, end = 18.dp, bottom = 104.dp)
      ) {
        ProfileCard(
          userName = userName,
          hasApiKey = apiKey.isNotBlank() || BuildConfig.GEMINI_API_KEY.isNotBlank(),
          model = model,
          onEditName = { activeDialog = DIALOG_PERSONA }
        )

        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("VOICE & NEURAL INTELLIGENCE")
        SettingsGroup {
          SettingRow(
            icon = Icons.Rounded.Key,
            title = "Gemini Live API & model",
            subtitle = if (apiKey.isNotBlank() || BuildConfig.GEMINI_API_KEY.isNotBlank()) {
              "$model · key configured"
            } else {
              "$model · add your API key"
            },
            onClick = { activeDialog = DIALOG_API }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.Rounded.RecordVoiceOver,
            title = "AI persona & voice",
            subtitle = "${userName.ifBlank { "Operator" }} · ${voiceProfile.label}",
            onClick = { activeDialog = DIALOG_PERSONA }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.Rounded.Language,
            title = "Conversation language",
            subtitle = "Auto detect · reply in your language",
            onClick = { activeDialog = DIALOG_LANGUAGE }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            title = "Speaker diagnostic",
            subtitle = "Play a 440 Hz tone through the Gemini output path",
            onClick = { viewModel.testSpeaker() },
            trailing = {
              GlassChip(
                label = "TEST",
                contentColor = palette.accent,
                background = palette.accentSoft
              )
            }
          )
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("HANDS-FREE & PRESENCE")
        SettingsGroup {
          SettingRow(
            icon = Icons.Rounded.Phone,
            title = "Default system assistant",
            subtitle = if (android.os.Build.VERSION.SDK_INT >= 29 &&
              context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
            ) "Zed is selected · system assistant panel enabled"
            else "Choose Zed for the system assistant overlay",
            onClick = {
              if (android.os.Build.VERSION.SDK_INT >= 29) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true) {
                  context.startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                } else {
                  Toast.makeText(context, "This Android version does not expose the assistant role.", Toast.LENGTH_LONG).show()
                }
              } else {
                Toast.makeText(context, "System assistant role selection requires Android 10 or newer.", Toast.LENGTH_LONG).show()
              }
            }
          )
          GroupDivider()
          SettingToggleRow(
            icon = Icons.Rounded.RecordVoiceOver,
            title = "Wake word",
            subtitle = "Say “Zed” · processed offline",
            checked = wakeWordEnabled,
            onCheckedChange = { enabled ->
              if (!enabled) {
                zedPrefs.wakeWordEnabled = false
                wakeWordEnabled = false
                ZedWakeWordService.stop(context)
              } else {
                scope.launch {
                  val granted = PermissionGateway.ensure(
                    context,
                    Manifest.permission.RECORD_AUDIO
                  )
                  if (granted) {
                    zedPrefs.wakeWordEnabled = true
                    wakeWordEnabled = true
                    ZedWakeWordService.sync(context)
                  } else {
                    Toast.makeText(
                      context,
                      "Microphone permission is needed for the wake word.",
                      Toast.LENGTH_SHORT
                    ).show()
                  }
                }
              }
            }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.Rounded.Lock,
            title = "Owner voice",
            subtitle = if (ownerVoiceEnabled) {
              "Wake word only opens for your enrolled voice"
            } else {
              "Optional on-device wake-word speaker check"
            },
            onClick = { activeDialog = DIALOG_OWNER_VOICE },
            trailing = {
              GlassChip(
                label = if (ownerVoiceEnabled) "ACTIVE" else "SET UP",
                contentColor = if (ownerVoiceEnabled) palette.success else palette.accent,
                background = palette.accentSoft
              )
            }
          )
          GroupDivider()
          SettingToggleRow(
            icon = Icons.Rounded.Palette,
            title = "Floating avatar",
            subtitle = "Robot head on top of other apps, with live state",
            checked = overlayEnabled,
            onCheckedChange = { enabled ->
              if (!enabled) {
                zedPrefs.overlayEnabled = false
                overlayEnabled = false
                ZedOverlayService.stop(context)
              } else {
                zedPrefs.overlayEnabled = true
                overlayEnabled = true
                if (!Settings.canDrawOverlays(context)) {
                  Toast.makeText(
                    context,
                    "Allow display over other apps, then return to Zed.",
                    Toast.LENGTH_LONG
                  ).show()
                  context.startActivity(
                    Intent(
                      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                      Uri.parse("package:${context.packageName}")
                    )
                  )
                } else {
                  ZedOverlayService.sync(context)
                }
              }
            }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.Rounded.Favorite,
            title = "Overlay avatar style",
            subtitle = overlayToy.replaceFirstChar { it.uppercase() },
            onClick = { activeDialog = DIALOG_OVERLAY_STYLE }
          )
          GroupDivider()
          SettingToggleRow(
            icon = Icons.Rounded.Visibility,
            title = "Live screen context",
            subtitle = "Sends a downscaled frame about every 2.5 s while talking",
            checked = screenContextEnabled,
            onCheckedChange = { enabled ->
              if (!enabled) {
                screenContextEnabled = false
                zedPrefs.screenContextEnabled = false
                viewModel.setScreenContext(false)
              } else {
                val manager = context.getSystemService(MediaProjectionManager::class.java)
                if (manager == null) {
                  Toast.makeText(context, "Screen capture is unavailable", Toast.LENGTH_SHORT).show()
                } else {
                  screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                }
              }
            }
          )
          GroupDivider()
          SettingToggleRow(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            title = "Wake chime",
            subtitle = "Soft confirmation when the wake word fires",
            checked = soundEffectsEnabled,
            onCheckedChange = { enabled ->
              soundEffectsEnabled = enabled
              zedPrefs.soundEffectsEnabled = enabled
            }
          )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("SYSTEM & DATA ENGINE")
        SettingsGroup {
          SettingRow(
            icon = Icons.Rounded.Lock,
            title = "Data & privacy",
            subtitle = "Local key, no backups, scoped permissions",
            onClick = { activeDialog = DIALOG_PRIVACY }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.Rounded.Phone,
            title = "App control & device actions",
            subtitle = "Installed apps, safe links, contacts and calls",
            onClick = { activeDialog = DIALOG_ACTIONS }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.Rounded.Lock,
            title = "Permissions & access",
            subtitle = "Microphone, contacts, overlay, accessibility",
            onClick = { activeDialog = DIALOG_PERMISSIONS }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.Rounded.Edit,
            title = "Memory vault",
            subtitle = "Review or remove facts Zed saved",
            onClick = { activeDialog = DIALOG_MEMORY }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.Rounded.Lock,
            title = "App identity & integrity",
            subtitle = "Zed ${BuildConfig.VERSION_NAME} · ${BuildConfig.APPLICATION_ID}",
            onClick = { activeDialog = DIALOG_IDENTITY }
          )
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("APPEARANCE & SECURITY")
        SettingsGroup {
          SettingToggleRow(
            icon = Icons.Rounded.Palette,
            title = "Liquid glass effects",
            subtitle = if (liquidGlass) {
              "Enabled · translucent surfaces and ambient glow"
            } else {
              "Off · Material 3 surfaces save GPU and battery"
            },
            checked = liquidGlass,
            onCheckedChange = { viewModel.setLiquidGlass(it) }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.Rounded.Palette,
            title = "Theme & avatar colors",
            subtitle = themeMode.label,
            onClick = { activeDialog = DIALOG_THEME },
            trailing = {
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeSwatch(ZedThemeMode.LIGHT)
                ThemeSwatch(ZedThemeMode.DARK)
                ThemeSwatch(ZedThemeMode.SUNSET)
              }
            }
          )
          GroupDivider()
          SettingRow(
            icon = Icons.Rounded.Favorite,
            title = "About Zed",
            subtitle = "Open source · crafted by ItzFallenMe",
            onClick = { activeDialog = DIALOG_ABOUT }
          )
        }

        Text(
          text = "ZED INTELLIGENCE OS · v${BuildConfig.VERSION_NAME}\nCrafted by ItzFallenMe · MIT licensed",
          color = palette.textMuted,
          fontSize = 11.sp,
          lineHeight = 17.sp,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 112.dp)
        )
      }
    }
  }

  when (activeDialog) {
    DIALOG_API -> ApiDialog(
      initialKey = apiKey,
      initialModel = model,
      onDismiss = { activeDialog = null },
      onSave = { key, selectedModel ->
        apiKey = key
        model = selectedModel
        prefs.edit {
          putString(ZedEngine.KEY_API_KEY, key)
          putString(ZedEngine.KEY_MODEL, selectedModel)
        }
        activeDialog = null
      }
    )
    DIALOG_PERSONA -> PersonaDialog(
      initialName = userName,
      initialVoice = voice,
      initialPreset = voicePreset,
      onDismiss = { activeDialog = null },
      onSave = { name, selectedVoice, selectedPreset ->
        userName = name
        voice = selectedVoice
        voicePreset = selectedPreset
        prefs.edit {
          putString(ZedEngine.KEY_USER_NAME, name)
          putString(ZedEngine.KEY_VOICE, selectedVoice)
          putString(ZedPreferences.KEY_VOICE_PRESET, selectedPreset)
        }
        activeDialog = null
      }
    )
    DIALOG_OWNER_VOICE -> OwnerVoiceDialog(
      preferences = zedPrefs,
      onDismiss = { activeDialog = null },
      onProfileChanged = { enabled ->
        ownerVoiceEnabled = enabled
        activeDialog = if (enabled) null else activeDialog
      }
    )
    DIALOG_OVERLAY_STYLE -> OverlayStyleDialog(
      current = overlayToy,
      onSelect = { selected ->
        overlayToy = selected
        zedPrefs.overlayToy = selected
        if (overlayEnabled) ZedOverlayService.sync(context)
        activeDialog = null
      },
      onDismiss = { activeDialog = null }
    )
    DIALOG_MEMORY -> MemoryDialog(onDismiss = { activeDialog = null })
    DIALOG_PERMISSIONS -> PermissionsDialog(onDismiss = { activeDialog = null })
    DIALOG_THEME -> ThemeDialog(
      current = themeMode,
      onSelect = { mode ->
        viewModel.setThemeMode(mode)
        activeDialog = null
      },
      onDismiss = { activeDialog = null }
    )
    DIALOG_LANGUAGE -> ZedInfoDialog(
      title = "Automatic language detection",
      message = "There is no language picker. Zed follows the language you speak and replies in it, including English, Hindi, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, and every other language Gemini supports.",
      onDismiss = { activeDialog = null }
    )
    DIALOG_PRIVACY -> ZedInfoDialog(
      title = "Data & privacy",
      message = "Your Gemini API key is stored in this app's private preferences, encrypted with keys held by the Android Keystore. The key is sent only to Google's Live endpoint when a session starts. Android backup is disabled, microphone audio is streamed only during an active session, and contacts are read only when you ask Zed to call someone by name.",
      onDismiss = { activeDialog = null }
    )
    DIALOG_ACTIONS -> ZedInfoDialog(
      title = "Device actions",
      message = "Zed can open any installed launcher app by name, open WhatsApp, prefill a message, search YouTube, open safe https links, control media and volume, change brightness, use the flashlight, start a call, or operate the current screen through Android accessibility. Ambiguous contacts and apps are never guessed; Zed asks you to choose. A prefilled WhatsApp message still needs your tap to send.",
      onDismiss = { activeDialog = null }
    )
    DIALOG_IDENTITY -> ZedInfoDialog(
      title = "App identity",
      message = "Z.E.D. ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\nPackage: ${BuildConfig.APPLICATION_ID}\nCreator: Vedant, ItzFallenMe\nLicense: MIT",
      onDismiss = { activeDialog = null }
    )
    DIALOG_ABOUT -> ZedInfoDialog(
      title = "About Zed",
      message = "Z.E.D. — Zeta Evolved Device Intelligence — is a native Android voice partner built with Kotlin and Jetpack Compose. It streams microphone PCM to Gemini Live and plays Gemini's native audio through a persistent AudioTrack. Wake-word detection and speaker vectors are processed on the device.\n\nCreated and maintained by Vedant, ItzFallenMe.",
      onDismiss = { activeDialog = null }
    )
  }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
  val palette = LocalZedPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 18.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    GlassIconButton(
      icon = Icons.AutoMirrored.Rounded.ArrowBack,
      contentDescription = "Back to home",
      onClick = onBack
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = "Settings",
        color = palette.textPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "OPERATOR CONTROL",
        color = palette.textMuted,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 1.dp)
      )
    }
    Spacer(modifier = Modifier.size(46.dp))
  }
}

@Composable
private fun ProfileCard(
  userName: String,
  hasApiKey: Boolean,
  model: String,
  onEditName: () -> Unit
) {
  val palette = LocalZedPalette.current
  GlassCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    contentPadding = PaddingValues(17.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box {
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(
                listOf(palette.accent, palette.accentSecondary, palette.accentTertiary)
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = userName.trim().firstOrNull()?.uppercase() ?: "Z",
            color = palette.specular,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
          )
        }
        StatusDot(
          color = palette.success,
          size = 13,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .border(2.dp, palette.background, CircleShape)
        )
      }
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(start = 13.dp)
      ) {
        Text(
          text = userName.ifBlank { "Operator" },
          color = palette.textPrimary,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "ZED OPERATOR · PRIMARY USER",
          color = palette.textMuted,
          fontSize = 9.sp,
          letterSpacing = 0.7.sp,
          modifier = Modifier.padding(top = 3.dp)
        )
      }
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(12.dp))
          .clickable(role = Role.Button, onClick = onEditName),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.Edit,
          contentDescription = "Edit operator name",
          tint = palette.textMuted,
          modifier = Modifier.size(19.dp)
        )
      }
    }

    Row(
      modifier = Modifier.padding(top = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
      GlassChip(
        label = if (hasApiKey) "GEMINI LIVE" else "API KEY NEEDED",
        contentColor = if (hasApiKey) palette.success else palette.error,
        background = if (hasApiKey) {
          palette.success.copy(alpha = 0.10f)
        } else {
          palette.error.copy(alpha = 0.10f)
        }
      )
      GlassChip(
        label = model.substringBefore("-live").uppercase(),
        contentColor = palette.accent,
        background = palette.accentSoft
      )
    }
  }
}

private const val DIALOG_API = "api"
private const val DIALOG_PERSONA = "persona"
private const val DIALOG_LANGUAGE = "language"
private const val DIALOG_PRIVACY = "privacy"
private const val DIALOG_ACTIONS = "actions"
private const val DIALOG_IDENTITY = "identity"
private const val DIALOG_THEME = "theme"
private const val DIALOG_ABOUT = "about"
private const val DIALOG_OWNER_VOICE = "owner_voice"
private const val DIALOG_OVERLAY_STYLE = "overlay_style"
private const val DIALOG_MEMORY = "memory"
private const val DIALOG_PERMISSIONS = "permissions"

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
  GlassCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 3.dp)
  ) {
    content()
  }
}

@Composable
private fun GroupDivider() {
  val palette = LocalZedPalette.current
  Spacer(
    modifier = Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(palette.cardBorder.copy(alpha = 0.55f))
  )
}

@Composable
private fun SettingToggleRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  val palette = LocalZedPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(role = Role.Switch) { onCheckedChange(!checked) }
      .padding(vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(13.dp))
        .background(palette.accentSoft),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = palette.accent,
        modifier = Modifier.size(21.dp)
      )
    }
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 12.dp, end = 8.dp)
    ) {
      Text(
        text = title,
        color = palette.textPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
      )
      Text(
        text = subtitle,
        color = palette.textMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 2.dp)
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = palette.background,
        checkedTrackColor = palette.accent,
        uncheckedThumbColor = palette.textMuted,
        uncheckedTrackColor = palette.accentSoft,
        uncheckedBorderColor = palette.cardBorder
      )
    )
  }
}

@Composable
private fun SettingRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  trailing: (@Composable () -> Unit)? = null
) {
  val palette = LocalZedPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(13.dp))
        .background(palette.accentSoft),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = palette.accent,
        modifier = Modifier.size(21.dp)
      )
    }
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 12.dp, end = 8.dp)
    ) {
      Text(
        text = title,
        color = palette.textPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
      )
      Text(
        text = subtitle,
        color = palette.textMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 2.dp)
      )
    }
    if (trailing != null) {
      trailing()
    } else {
      Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = palette.textMuted,
        modifier = Modifier.size(19.dp)
      )
    }
  }
}

@Composable
private fun ThemeSwatch(mode: ZedThemeMode) {
  val palette = LocalZedPalette.current
  val swatch = mode.palette()
  Box(
    modifier = Modifier
      .size(18.dp)
      .clip(CircleShape)
      .background(
        Brush.linearGradient(listOf(swatch.accent, swatch.accentSecondary))
      )
      .border(1.dp, palette.cardBorder.copy(alpha = 0.8f), CircleShape)
  )
}

@Composable
private fun ThemeDialog(
  current: ZedThemeMode,
  onSelect: (ZedThemeMode) -> Unit,
  onDismiss: () -> Unit
) {
  val palette = LocalZedPalette.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text("Theme & palette", color = palette.textPrimary, fontWeight = FontWeight.Bold)
        Text(
          text = "The robot stage and accent colours follow your selection.",
          color = palette.textMuted,
          fontSize = 12.sp,
          fontWeight = FontWeight.Normal,
          modifier = Modifier.padding(top = 3.dp)
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ZedThemeMode.entries.forEach { mode ->
          val swatch = mode.palette()
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(
                if (mode == current) palette.accentSoft else palette.card.copy(alpha = 0.4f)
              )
              .clickable { onSelect(mode) }
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(
                    listOf(swatch.accent, swatch.accentSecondary, swatch.accentTertiary)
                  )
                )
            )
            Text(
              text = mode.label,
              color = palette.textPrimary,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
            )
            if (mode == current) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = palette.accent,
                modifier = Modifier.size(19.dp)
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("CLOSE", color = palette.accent, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = palette.card,
    shape = RoundedCornerShape(22.dp)
  )
}

@Composable
private fun ApiDialog(
  initialKey: String,
  initialModel: String,
  onDismiss: () -> Unit,
  onSave: (String, String) -> Unit
) {
  val palette = LocalZedPalette.current
  var key by remember { mutableStateOf(initialKey) }
  var model by remember { mutableStateOf(initialModel) }
  var modelMenu by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Gemini Live API", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = key,
          onValueChange = { key = it },
          label = { Text("API key", color = palette.textMuted) },
          placeholder = { Text("Paste your Gemini API key", color = palette.textMuted) },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
          ),
          modifier = Modifier.fillMaxWidth(),
          colors = zedFieldColors()
        )
        Text(
          text = "Use an ephemeral token or a backend proxy for public builds. The key is stored only in this app's private preferences.",
          color = palette.textMuted,
          fontSize = 11.sp,
          lineHeight = 16.sp
        )
        Text("Model", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Box {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(palette.card)
              .border(1.dp, palette.cardBorder, RoundedCornerShape(12.dp))
              .clickable { modelMenu = true }
              .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = model,
              color = palette.textPrimary,
              fontSize = 13.sp,
              modifier = Modifier.weight(1f)
            )
            Icon(
              imageVector = Icons.Rounded.ChevronRight,
              contentDescription = null,
              tint = palette.textMuted,
              modifier = Modifier.size(18.dp)
            )
          }
          DropdownMenu(
            expanded = modelMenu,
            onDismissRequest = { modelMenu = false },
            containerColor = palette.card
          ) {
            ZedEngine.AVAILABLE_MODELS.forEach { option ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = option,
                    color = if (option == model) palette.accent else palette.textPrimary,
                    fontSize = 13.sp
                  )
                },
                onClick = {
                  model = option
                  modelMenu = false
                }
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onSave(key.trim(), model) }) {
        Text("SAVE", color = palette.accent, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("CANCEL", color = palette.textMuted, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = palette.card,
    shape = RoundedCornerShape(22.dp)
  )
}

@Composable
private fun PersonaDialog(
  initialName: String,
  initialVoice: String,
  initialPreset: String,
  onDismiss: () -> Unit,
  onSave: (String, String, String) -> Unit
) {
  val palette = LocalZedPalette.current
  var name by remember { mutableStateOf(initialName) }
  var presetId by remember {
    mutableStateOf(ZedVoiceProfiles.resolve(initialPreset, initialVoice).id)
  }
  val selectedProfile = ZedVoiceProfiles.byId(presetId)
  var voiceMenu by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("AI persona & voice", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Your name (optional)", color = palette.textMuted) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          modifier = Modifier.fillMaxWidth(),
          colors = zedFieldColors()
        )
        Text("Voice", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Box {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(palette.card)
              .border(1.dp, palette.cardBorder, RoundedCornerShape(12.dp))
              .clickable { voiceMenu = true }
              .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = selectedProfile.label,
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = selectedProfile.description,
                color = palette.textMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
              )
            }
            Icon(
              imageVector = Icons.Rounded.ChevronRight,
              contentDescription = null,
              tint = palette.textMuted,
              modifier = Modifier.size(18.dp)
            )
          }
          DropdownMenu(
            expanded = voiceMenu,
            onDismissRequest = { voiceMenu = false },
            containerColor = palette.card
          ) {
            ZedVoiceProfiles.ALL.forEach { option ->
              DropdownMenuItem(
                text = {
                  Column {
                    Text(
                      text = option.label,
                      color = if (option.id == presetId) palette.accent else palette.textPrimary,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.SemiBold
                    )
                    Text(
                      text = option.description,
                      color = palette.textMuted,
                      fontSize = 10.sp,
                      lineHeight = 14.sp
                    )
                  }
                },
                onClick = {
                  presetId = option.id
                  voiceMenu = false
                }
              )
            }
          }
        }
        Text(
          text = "Each preset uses a different Gemini voice and matching delivery style. Changes apply the next time you start a session.",
          color = palette.textMuted,
          fontSize = 11.sp,
          lineHeight = 16.sp
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onSave(name.trim(), selectedProfile.geminiVoice, selectedProfile.id) }) {
        Text("SAVE", color = palette.accent, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("CANCEL", color = palette.textMuted, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = palette.card,
    shape = RoundedCornerShape(22.dp)
  )
}

@Composable
private fun zedFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = LocalZedPalette.current.textPrimary,
  unfocusedTextColor = LocalZedPalette.current.textPrimary,
  focusedContainerColor = LocalZedPalette.current.card.copy(alpha = 0.4f),
  unfocusedContainerColor = LocalZedPalette.current.card.copy(alpha = 0.4f),
  focusedBorderColor = LocalZedPalette.current.accent,
  unfocusedBorderColor = LocalZedPalette.current.cardBorder,
  cursorColor = LocalZedPalette.current.accent
)
