package com.itsazni.notificationforwarder.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itsazni.notificationforwarder.data.ApplicationItem
import com.itsazni.notificationforwarder.data.ApplicationListBuilder
import com.itsazni.notificationforwarder.data.PackageDiscoveryStore
import com.itsazni.notificationforwarder.data.PackageManagerAppSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FilterPackagePicker(
    selectedPackages: Set<String>,
    onSelectedPackagesChange: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val discoveryStore = remember { PackageDiscoveryStore(context) }
    val packageManagerSource = remember { PackageManagerAppSource(context) }
    val latestSelectedPackages by rememberUpdatedState(selectedPackages)
    var applications by remember { mutableStateOf<List<ApplicationItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showManualDialog by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }

    LaunchedEffect(refreshKey) {
        applications = withContext(Dispatchers.IO) {
            ApplicationListBuilder.build(
                visibleApplications = packageManagerSource.getVisibleApplications(),
                discoveredPackages = discoveryStore.getDiscoveredPackages(),
                configuredPackages = latestSelectedPackages
            )
        }
    }

    val filtered = remember(applications, query) {
        ApplicationListBuilder.search(applications, query)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = query,
            onValueChange = { query = it },
            label = { Text("Search applications") },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(modifier = Modifier.weight(1f), onClick = { showManualDialog = true }) {
                Text("Add package manually")
            }
            Button(onClick = { refreshKey++ }) {
                Text("Refresh")
            }
        }

        Text("Selected: ${selectedPackages.size} applications")

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = app.packageName in selectedPackages,
                        onCheckedChange = { checked ->
                            val updated = if (checked) selectedPackages + app.packageName
                            else selectedPackages - app.packageName
                            onSelectedPackagesChange(updated)
                        }
                    )
                    Column {
                        Text(app.displayLabel, fontWeight = FontWeight.SemiBold)
                        Text(app.packageName)
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Add package manually") },
            text = {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    label = { Text("Package name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = manualInput.isNotBlank(),
                    onClick = {
                        val normalized = manualInput.trim()
                        if (normalized.isNotEmpty()) {
                            runCatching { discoveryStore.recordPackage(normalized) }
                                .onFailure { error ->
                                    Log.w(TAG, "Failed to persist manually discovered package: $normalized", error)
                                }
                            applications = ApplicationListBuilder.ensurePackage(applications, normalized)
                            onSelectedPackagesChange(
                                ApplicationListBuilder.addManualPackage(selectedPackages, normalized)
                            )
                            manualInput = ""
                            showManualDialog = false
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    manualInput = ""
                    showManualDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

private const val TAG = "FilterPackagePicker"
