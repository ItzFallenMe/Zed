package dev.zed.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.ui.theme.ZedGlassBorder
import dev.zed.assistant.ui.theme.ZedMuted
import dev.zed.assistant.ui.theme.ZedWhite
import kotlinx.coroutines.delay

/**
 * First run. Zed needs two free keys and a name to call you by — this screen collects all
 * three before any permission is requested, so the app never opens with an unexplained
 * system dialog.
 */
@Composable
fun KeysScreen(
    prefs: ZedPrefs,
    onDone: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var gemini by remember { mutableStateOf(prefs.apiKey) }
    var groq by remember { mutableStateOf(prefs.groqKey) }
    var name by remember { mutableStateOf(prefs.userName) }
    var showHelp by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { entered = true }
    val rise by animateFloatAsState(
        targetValue = if (entered) 0f else 24f,
        animationSpec = tween(600),
        label = "rise"
    )

    val ready = gemini.isNotBlank() && groq.isNotBlank()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp)
                .graphicsLayer { translationY = rise; alpha = 1f - rise / 48f }
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            ZedAvatar(mood = AvatarMood.IDLE, size = 96)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Meet Zed",
                style = MaterialTheme.typography.headlineLarge,
                color = ZedWhite
            )
            Text(
                text = "Your phone's other half. It listens, sees your screen, runs your apps " +
                    "and talks back like a person.",
                style = MaterialTheme.typography.bodyMedium,
                color = ZedMuted,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle("What should Zed call you?")
            GlassField(
                value = name,
                onValueChange = { name = it },
                label = "Your name",
                hint = "e.g. Fallen"
            )

            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle("Two free keys, both needed")
            Text(
                text = "Gemini is the ears and voice. Groq is the quick thinking. Both have free " +
                    "tiers and both stay on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = ZedMuted
            )

            Spacer(modifier = Modifier.height(14.dp))
            KeyCard(
                title = "Gemini",
                subtitle = "Realtime voice + screen understanding",
                value = gemini,
                onValueChange = { gemini = it },
                linkLabel = "aistudio.google.com/apikey"
            ) { uriHandler.openUri("https://aistudio.google.com/apikey") }

            Spacer(modifier = Modifier.height(12.dp))
            KeyCard(
                title = "Groq",
                subtitle = "Fast replies and multi-step actions",
                value = groq,
                onValueChange = { groq = it },
                linkLabel = "console.groq.com/keys"
            ) { uriHandler.openUri("https://console.groq.com/keys") }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    prefs.apiKey = gemini
                    prefs.groqKey = groq
                    prefs.userName = name
                    prefs.keysConfigured = ready
                    onDone()
                },
                enabled = ready,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZedWhite,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.10f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    text = if (ready) "Continue" else "Add both keys to continue",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            TextButton(
                onClick = { showHelp = !showHelp },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Where do I get them?",
                        style = MaterialTheme.typography.labelMedium,
                        color = ZedMuted
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = ZedMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = showHelp,
                enter = fadeIn() + expandVertically()
            ) {
                SectionCard {
                    HelpLine(
                        "1",
                        "Open aistudio.google.com/apikey, sign in with a Google account and " +
                            "press \"Create API key\". Copy the AIza… string."
                    )
                    HelpLine(
                        "2",
                        "Open console.groq.com/keys, sign in and create a key. It starts with " +
                            "gsk_."
                    )
                    HelpLine(
                        "3",
                        "Paste both above. You can change them later in Settings."
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No account, no analytics, no training on your conversations. " +
                    "Created by ItzFallenMe.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.35f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun KeyCard(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    linkLabel: String,
    onGetKey: () -> Unit
) {
    val filled = value.isNotBlank()
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            1.dp,
            if (filled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ZedWhite,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedMuted
                )
            }
            if (filled) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(ZedWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Paste key", color = Color.White.copy(alpha = 0.3f)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = 0.65f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                cursorColor = ZedWhite,
                focusedTextColor = ZedWhite,
                unfocusedTextColor = ZedWhite
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = linkLabel,
                style = MaterialTheme.typography.labelSmall,
                color = ZedWhite.copy(alpha = 0.75f),
                modifier = Modifier
                    .clickable(onClick = onGetKey)
                    .padding(8.dp)
            )
        }
        }
    }
}

@Composable
private fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint, color = Color.White.copy(alpha = 0.3f)) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White.copy(alpha = 0.6f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
            cursorColor = ZedWhite,
            focusedTextColor = ZedWhite,
            unfocusedTextColor = ZedWhite,
            focusedLabelColor = ZedMuted,
            unfocusedLabelColor = ZedMuted
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun HelpLine(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                color = ZedWhite
            )
        }
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = ZedMuted
        )
    }
}
