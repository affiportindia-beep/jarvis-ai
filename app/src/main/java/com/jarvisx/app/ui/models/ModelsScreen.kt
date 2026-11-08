package com.jarvisx.app.ui.models

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvisx.app.JarvisApp
import com.jarvisx.app.models.DownloadCenter
import com.jarvisx.app.models.DownloadState
import com.jarvisx.app.models.ModelDownloadService
import com.jarvisx.app.ui.common.Card
import com.jarvisx.app.ui.common.SectionTitle
import com.jarvisx.app.ui.theme.Jx
import com.jarvisx.core.model.ModelCatalog
import com.jarvisx.core.model.ModelSelector
import com.jarvisx.core.model.ModelSpec
import kotlinx.coroutines.launch

class ModelsViewModel(app: Application) : AndroidViewModel(app) {
    val graph = (app as JarvisApp).graph
    val downloads = DownloadCenter.states

    fun profile() = graph.profiler.profile()
    fun recommended() = ModelSelector.recommend(profile())
    fun isInstalled(spec: ModelSpec) = graph.modelStore.isInstalled(spec)
    fun partialMb(spec: ModelSpec) = graph.modelStore.sizeOnDiskMb(spec)
    fun selectedId(): String = graph.modelStore.selectedId ?: recommended().model.id

    fun select(spec: ModelSpec) {
        graph.modelStore.selectedId = spec.id
        viewModelScope.launch { graph.brain.unload() } // next question loads the newly chosen model
    }

    private val importer = com.jarvisx.app.models.ModelImporter(app, graph.modelStore)
    val importStatus = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val importing = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun importModel(uri: android.net.Uri) {
        if (importing.value) return
        importing.value = true
        importStatus.value = "Checking file…"
        viewModelScope.launch {
            try {
                val spec = importer.import(uri) { p ->
                    importStatus.value = "Copying and verifying: ${p.bytes / (1024 * 1024)}" +
                        (if (p.total > 0) " / ${p.total / (1024 * 1024)} MB" else " MB")
                }
                DownloadCenter.set(spec.id, DownloadState.Done)
                importStatus.value = "✓ ${spec.displayName} imported and verified. You can delete the copy in Downloads."
            } catch (e: Exception) {
                importStatus.value = "Import failed: ${e.message}"
            } finally {
                importing.value = false
            }
        }
    }

    fun delete(spec: ModelSpec) {
        viewModelScope.launch {
            if (graph.brain.loaded == spec) graph.brain.unload()
            graph.modelStore.delete(spec)
            DownloadCenter.set(spec.id, DownloadState.Idle)
        }
    }
}

@Composable
fun ModelsScreen(vm: ModelsViewModel = viewModel()) {
    val context = LocalContext.current
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf<ModelSpec?>(null) }
    var pendingDownload by remember { mutableStateOf<ModelSpec?>(null) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Download works either way; without permission Android just hides the progress notification.
        pendingDownload?.let { ModelDownloadService.start(context, it) }
        pendingDownload = null
    }

    fun startDownload(spec: ModelSpec) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) ModelDownloadService.start(context, spec)
        else { pendingDownload = spec; notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    val importStatus by vm.importStatus.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    val profile = remember(refresh, downloads, importing) { vm.profile() }
    val rec = remember(refresh) { vm.recommended() }
    val selected = remember(refresh, downloads) { vm.selectedId() }
    val anyActive = downloads.values.any { it is DownloadState.Checking || it is DownloadState.Downloading || it is DownloadState.Verifying }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Models", style = MaterialTheme.typography.headlineSmall, color = Jx.Text)
            Spacer(Modifier.height(4.dp))
            Text(
                "Models run on this phone's CPU. The internet is used only to download a model once, " +
                    "and every file is checked against its SHA-256 before use.",
                color = Jx.TextDim, style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Card {
                SectionTitle("Recommended for this phone")
                Text(rec.reason, color = Jx.Text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "RAM %.1f GB total, %.1f GB free now · storage %.1f GB free".format(
                        profile.totalRamMb / 1024.0, profile.availableRamMb / 1024.0, profile.freeStorageMb / 1024.0,
                    ),
                    color = Jx.TextDim, style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        item {
            Card {
                SectionTitle("Already have the model file?")
                Text(
                    "Import a .gguf file from your phone (for example Downloads) instead of downloading it again. " +
                        "It is accepted only if its SHA-256 matches a JARVIS X model.",
                    color = Jx.TextDim, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pickFile.launch(arrayOf("*/*")) }, enabled = !importing && !anyActive) {
                    Text(if (importing) "Importing…" else "Import model file")
                }
                importStatus?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = if (it.startsWith("Import failed")) Jx.Bad else Jx.TextDim, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(ModelCatalog.all, key = { it.id }) { spec ->
            val installed = remember(refresh, downloads, importing) { vm.isInstalled(spec) }
            val st = downloads[spec.id] ?: DownloadState.Idle
            val fits = ModelSelector.canLoadNow(spec, profile)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            buildString {
                                append(spec.displayName)
                                if (spec.id == rec.model.id) append("  · recommended")
                                if (spec.experimental) append("  · experimental")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (spec.id == rec.model.id) Jx.Accent else Jx.Text,
                        )
                        Text(
                            "${spec.quantization} · ~${spec.approxSizeMb} MB download · needs ~${spec.ramRequiredMb} MB RAM · ${spec.licence}",
                            style = MaterialTheme.typography.labelSmall, color = Jx.TextDim,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(spec.summary, style = MaterialTheme.typography.bodyMedium, color = Jx.Text)
                if (!fits.ok) {
                    Spacer(Modifier.height(4.dp))
                    Text(fits.reason, style = MaterialTheme.typography.bodyMedium, color = Jx.Warn)
                }
                Spacer(Modifier.height(10.dp))
                when (st) {
                    is DownloadState.Checking -> Status("Checking file and checksum on the server…")
                    is DownloadState.Downloading -> {
                        LinearProgressIndicator(progress = { st.fraction }, modifier = Modifier.fillMaxWidth(), color = Jx.Accent)
                        Spacer(Modifier.height(4.dp))
                        Status("${st.bytes / (1024 * 1024)} / ${if (st.total > 0) st.total / (1024 * 1024) else "?"} MB")
                    }
                    is DownloadState.Verifying -> Status("Verifying SHA-256…")
                    is DownloadState.Failed -> Text(st.message, color = Jx.Bad, style = MaterialTheme.typography.bodyMedium)
                    else -> Unit
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val active = st is DownloadState.Checking || st is DownloadState.Downloading || st is DownloadState.Verifying
                    when {
                        active -> OutlinedButton(onClick = { ModelDownloadService.cancel(context) }) { Text("Cancel") }
                        installed -> {
                            if (selected == spec.id) {
                                Text("✓ In use", color = Jx.Good, modifier = Modifier.padding(top = 12.dp))
                            } else {
                                Button(
                                    onClick = { vm.select(spec); refresh++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Jx.Accent),
                                ) { Text("Use this model") }
                            }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { confirmDelete = spec }) { Text("Delete", color = Jx.Bad) }
                        }
                        else -> {
                            val partial = vm.partialMb(spec)
                            Button(
                                onClick = { startDownload(spec) },
                                enabled = !anyActive,
                                colors = ButtonDefaults.buttonColors(containerColor = Jx.Accent),
                            ) { Text(if (partial > 0) "Resume download ($partial MB done)" else "Download") }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Tip: download on Wi-Fi. A partly downloaded file resumes where it stopped.",
                color = Jx.TextDim, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    confirmDelete?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${spec.displayName}?") },
            text = { Text("This frees about ${spec.approxSizeMb} MB. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(spec); confirmDelete = null; refresh++ }) { Text("Delete", color = Jx.Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun Status(text: String) {
    Text(text, color = Jx.TextDim, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(6.dp))
}
