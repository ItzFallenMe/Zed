package com.zed.assistant.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zed.assistant.BuildConfig
import com.zed.assistant.ai.ZedEngine
import com.zed.assistant.ui.theme.Background
import com.zed.assistant.ui.theme.Cards
import com.zed.assistant.ui.theme.Hint
import com.zed.assistant.ui.theme.PrimaryRed
import com.zed.assistant.ui.theme.PrimaryText
import com.zed.assistant.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  val context = LocalContext.current
  val prefs = remember {
    context.getSharedPreferences(ZedEngine.PREFS, Context.MODE_PRIVATE)
  }

  var apiKey by remember {
    mutableStateOf(prefs.getString(ZedEngine.KEY_API_KEY, "").orEmpty())
  }
  var userName by remember {
    mutableStateOf(prefs.getString(ZedEngine.KEY_USER_NAME, "").orEmpty())
  }
  var model by remember {
    mutableStateOf(
      prefs.getString(ZedEngine.KEY_MODEL, ZedEngine.DEFAULT_MODEL) ?: ZedEngine.DEFAULT_MODEL
    )
  }
  var voice by remember {
    mutableStateOf(
      prefs.getString(ZedEngine.KEY_VOICE, ZedEngine.DEFAULT_VOICE) ?: ZedEngine.DEFAULT_VOICE
    )
  }

  BackHandler { onBack() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Settings",
            color = PrimaryText,
            fontWeight = FontWeight.Medium
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.Rounded.ArrowBack,
              contentDescription = "Back",
              tint = PrimaryText
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
      )
    },
    containerColor = Background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      SectionLabel("GEMINI LIVE")

      OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        label = { Text("API key", color = Hint) },
        placeholder = { Text("Paste your Gemini API key", color = Hint) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors()
      )

      if (apiKey.isBlank() && BuildConfig.GEMINI_API_KEY.isNotBlank()) {
        Text(
          text = "A build-time API key is configured. Leave this blank to use it.",
          color = Hint,
          fontSize = 11.sp
        )
      } else {
        Text(
          text = "Stored only on this device. For a public release, use ephemeral tokens or a backend instead of shipping a key.",
          color = Hint,
          fontSize = 11.sp
        )
      }

      OutlinedTextField(
        value = userName,
        onValueChange = { userName = it },
        label = { Text("Your name (optional)", color = Hint) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors()
      )

      DropdownSelector(
        label = "Model",
        options = ZedEngine.AVAILABLE_MODELS,
        selected = model,
        onSelected = { model = it }
      )
      Text(
        text = "gemini-3.8-live is the current low-latency Live model. 3.1 Flash Live is kept for compatibility.",
        color = Hint,
        fontSize = 11.sp
      )

      DropdownSelector(
        label = "Voice",
        options = ZedEngine.AVAILABLE_VOICES,
        selected = voice,
        onSelected = { voice = it }
      )

      Spacer(modifier = Modifier.height(2.dp))
      SectionLabel("AUDIO DIAGNOSTIC")

      OutlinedButton(
        onClick = { viewModel.testSpeaker() },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText)
      ) {
        Icon(
          imageVector = Icons.Rounded.VolumeUp,
          contentDescription = null,
          modifier = Modifier.padding(end = 8.dp)
        )
        Text("Test speaker")
      }
      Text(
        text = "Plays a 440 Hz tone through the same AudioTrack used for Zed's Gemini voice. If the tone is audible but Zed is silent, the problem is in the Live response/audio queue, not the speaker path.",
        color = Hint,
        fontSize = 11.sp
      )

      Spacer(modifier = Modifier.height(2.dp))
      SectionLabel("DEVICE ACTIONS")
      Text(
        text = "Zed can open an allowlist of installed apps, open WhatsApp, open safe https links, and start a call or dialer intent. Contacts are read only when you ask Zed to call someone by name, and multiple matches are never guessed.",
        color = Hint,
        fontSize = 11.sp
      )
      Text(
        text = "Allowlisted apps: WhatsApp, YouTube, Instagram, Facebook, Chrome, Gmail, Maps, Spotify, Netflix, Telegram.",
        color = Hint,
        fontSize = 11.sp
      )

      Spacer(modifier = Modifier.height(6.dp))

      Button(
        onClick = {
          prefs.edit()
            .putString(ZedEngine.KEY_API_KEY, apiKey.trim())
            .putString(ZedEngine.KEY_USER_NAME, userName.trim())
            .putString(ZedEngine.KEY_MODEL, model)
            .putString(ZedEngine.KEY_VOICE, voice)
            .apply()
          onBack()
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = PrimaryRed,
          contentColor = PrimaryText
        )
      ) {
        Text("Save", fontWeight = FontWeight.Bold)
      }

      Text(
        text = "Changes apply the next time you start a session.",
        color = Hint,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    color = PrimaryRed,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 2.sp
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = PrimaryText,
  unfocusedTextColor = PrimaryText,
  focusedContainerColor = Cards,
  unfocusedContainerColor = Cards,
  focusedBorderColor = PrimaryRed,
  unfocusedBorderColor = Cards,
  cursorColor = PrimaryRed
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
  label: String,
  options: List<String>,
  selected: String,
  onSelected: (String) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded }
  ) {
    OutlinedTextField(
      value = selected,
      onValueChange = {},
      readOnly = true,
      label = { Text(label, color = Hint) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .menuAnchor()
        .fillMaxWidth(),
      colors = fieldColors()
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(option, color = PrimaryText) },
          onClick = {
            onSelected(option)
            expanded = false
          }
        )
      }
    }
  }
}
