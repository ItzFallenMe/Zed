package dev.zed.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.ui.theme.ZedCyan
import dev.zed.assistant.ui.theme.ZedMuted
import kotlinx.coroutines.delay

@Composable
fun SetupScreen(
    onCapabilityClick: (ZedPermissions.Capability) -> Unit,
    onDeviceAdmin: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    val statuses = remember(tick) {
        ZedPermissions.Capability.entries.associateWith { ZedPermissions.isGranted(context, it) }
    }
    val coreReady = statuses[ZedPermissions.Capability.MICROPHONE] == true &&
        statuses[ZedPermissions.Capability.ACCESSIBILITY] == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "INITIALISE",
            color = ZedCyan,
            fontSize = 12.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Grant the systems Zed needs to control this phone.",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Everything you allow stays on this device. No audio is streamed in the " +
                "background; the wake word is recognised locally.",
            color = ZedMuted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(18.dp))

        ZedPermissions.Capability.entries.forEach { capability ->
            val granted = statuses[capability] == true
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                CapabilityRow(
                    title = capability.title,
                    description = capability.why,
                    granted = granted,
                    onGrant = {
                        when (capability) {
                            ZedPermissions.Capability.DEVICE_ADMIN -> onDeviceAdmin()
                            ZedPermissions.Capability.ACCESSIBILITY ->
                                ZedPermissions.openSettingsScreen(context, capability)
                            ZedPermissions.Capability.NOTIFICATION_ACCESS ->
                                ZedPermissions.openSettingsScreen(context, capability)
                            ZedPermissions.Capability.WRITE_SETTINGS ->
                                ZedPermissions.openSettingsScreen(context, capability)
                            else -> onCapabilityClick(capability)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (coreReady) ZedCyan else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (coreReady) Color(0xFF04121A) else MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text(
                text = if (coreReady) "BRING ZED ONLINE" else "CONTINUE ANYWAY",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onDone) {
                Text("Skip for now", color = ZedMuted, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}
