package dev.zed.assistant.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.zed.assistant.core.ZedPermissions
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
        ZedPermissions.Capability.entries.associateWith {
            ZedPermissions.isGranted(context, it)
        }
    }
    val coreReady = statuses[ZedPermissions.Capability.MICROPHONE] == true &&
        statuses[ZedPermissions.Capability.ACCESSIBILITY] == true
    val grantedCount = statuses.count { it.value }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Set up Zed",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "$grantedCount of ${statuses.size} capabilities ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(label = if (coreReady) "Core ready" else "Needs access", active = coreReady)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Zed works through Android's own permission system. Grant only what you want to " +
                "use; every capability is independent and can be revoked later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        SectionCard {
            ZedPermissions.Capability.entries.forEachIndexed { index, capability ->
                CapabilityRow(
                    title = capability.title,
                    description = capability.why,
                    granted = statuses[capability] == true,
                    onGrant = {
                        when (capability) {
                            ZedPermissions.Capability.DEVICE_ADMIN -> onDeviceAdmin()
                            ZedPermissions.Capability.ACCESSIBILITY,
                            ZedPermissions.Capability.NOTIFICATION_ACCESS,
                            ZedPermissions.Capability.WRITE_SETTINGS ->
                                ZedPermissions.openSettingsScreen(context, capability)
                            else -> onCapabilityClick(capability)
                        }
                    }
                )
                if (index != ZedPermissions.Capability.entries.lastIndex) DividerLine()
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = if (coreReady) "Start using Zed" else "Continue anyway",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for now", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = { ZedPermissions.openSettingsScreen(context, ZedPermissions.Capability.ACCESSIBILITY) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Open accessibility settings",
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
