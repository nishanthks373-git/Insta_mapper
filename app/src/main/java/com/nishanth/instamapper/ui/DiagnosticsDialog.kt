package com.nishanth.instamapper.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nishanth.instamapper.MainViewModel

/** Opens this app's page in Android settings (where network access can be allowed). */
fun openAppSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun DiagnosticsDialog(vm: MainViewModel) {
    val ctx = LocalContext.current
    val report = vm.diagnostics
    AlertDialog(
        onDismissRequest = { vm.showDiagnostics = false },
        title = { Text("Connection check", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (vm.diagnosticsRunning || report == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Testing your connection…")
                    }
                } else {
                    report.checks.forEach { c ->
                        Column {
                            Text((if (c.ok) "✅ " else "❌ ") + c.name, fontWeight = FontWeight.SemiBold)
                            Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(report.advice, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.showDiagnostics = false
                if (vm.input.isNotBlank()) vm.analyze()
            }) { Text("Try again") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { openAppSettings(ctx) }) { Text("App settings") }
                TextButton(onClick = { vm.runDiagnostics() }, enabled = !vm.diagnosticsRunning) { Text("Re-test") }
            }
        },
    )
}
