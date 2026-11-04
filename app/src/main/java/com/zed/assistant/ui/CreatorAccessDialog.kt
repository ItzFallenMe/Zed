package com.zed.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zed.assistant.ai.CreatorPrompt
import com.zed.assistant.data.CreatorGate
import com.zed.assistant.ui.theme.LocalZedPalette

/**
 * The code never leaves the device. This dialog is the fallback when the local
 * speech recogniser is unavailable, and it is also the only place a creator
 * code should ever be typed.
 */
@Composable
fun CreatorAccessDialog(
  prompt: CreatorPrompt,
  onVerify: (String) -> Unit,
  onUseVoice: () -> Unit,
  onDismiss: () -> Unit
) {
  val palette = LocalZedPalette.current
  var code by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        "Creator access",
        color = palette.textPrimary,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          if (prompt.listening) {
            "Listening for your code. You can also type it below."
          } else {
            "Say or type your creator code to unlock master control. Verification happens only on this device."
          },
          color = palette.textMuted,
          fontSize = 12.sp,
          lineHeight = 18.sp
        )
        OutlinedTextField(
          value = code,
          onValueChange = { value ->
            code = value.filter(Char::isDigit).take(CreatorGate.MAX_CODE_LENGTH)
          },
          label = { Text("Creator code", color = palette.textMuted) },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
          ),
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.cardBorder,
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary,
            cursorColor = palette.accent
          )
        )
        if (!prompt.error.isNullOrBlank()) {
          Text(
            prompt.error,
            color = palette.error,
            fontSize = 12.sp,
            lineHeight = 17.sp
          )
        } else if (prompt.listening) {
          Text("Microphone is listening locally.", color = palette.accent, fontSize = 11.sp)
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = code.length >= CreatorGate.MIN_CODE_LENGTH,
        onClick = { onVerify(code) }
      ) {
        Text("VERIFY", color = palette.accent, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
        if (!prompt.listening) {
          TextButton(onClick = onUseVoice) {
            Text("USE VOICE", color = palette.textSecondary, fontWeight = FontWeight.Bold)
          }
        }
        TextButton(onClick = onDismiss) {
          Text("CANCEL", color = palette.textMuted, fontWeight = FontWeight.Bold)
        }
      }
    },
    containerColor = palette.card,
    shape = RoundedCornerShape(22.dp)
  )
}
