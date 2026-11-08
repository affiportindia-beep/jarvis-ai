package com.jarvisx.app.ui.chat

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.VolumeOff
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.jarvisx.app.voice.ListenState
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PublicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvisx.app.brain.BrainStatus
import com.jarvisx.app.ui.common.CoreOrb
import com.jarvisx.app.ui.common.markdownLite
import com.jarvisx.app.ui.theme.Jx

@Composable
fun ChatScreen(onOpenModels: () -> Unit, vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Header(state, onNewChat = vm::newChat, onToggleOnline = vm::toggleOnline, onToggleThink = vm::toggleThink)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                EmptyState(state.brain, onOpenModels)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(state.messages, key = { it.id }) { Bubble(it) }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
        VoiceBar(
            state = state,
            onStopListening = vm::stopListening,
            onStopSpeaking = vm::stopSpeaking,
            onDismiss = vm::dismissVoiceMessage,
            onDownload = vm::downloadSpeechPack,
        )
        val context = LocalContext.current
        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.startListening()
        }
        InputBar(
            value = input,
            onChange = { input = it },
            busy = state.busy,
            listening = state.listen is ListenState.Listening || state.listen is ListenState.Partial,
            voiceLanguage = state.voiceLanguage,
            onSend = { vm.send(input); input = "" },
            onStop = vm::stop,
            onMic = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    vm.startListening()
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onToggleLanguage = vm::toggleVoiceLanguage,
        )
    }
    if (state.showOnlineExplainer) {
        AlertDialog(
            onDismissRequest = { vm.confirmOnline(false) },
            title = { Text("Turn on online search?") },
            text = {
                Text(
                    "When a question is about something current (news, prices, recent episodes), JARVIS X will send " +
                        "only the search words to Google News and Wikipedia. Your chats, memory and files never leave the phone, " +
                        "and no AI service is used: the model on this phone reads the results and answers with sources.\n\n" +
                        "Everything else keeps working offline. You can switch this off any time with the globe button."
                )
            },
            confirmButton = { TextButton(onClick = { vm.confirmOnline(true) }) { Text("Turn on", color = Jx.Accent) } },
            dismissButton = { TextButton(onClick = { vm.confirmOnline(false) }) { Text("Keep offline") } },
        )
    }
}

@Composable
private fun Header(state: ChatUiState, onNewChat: () -> Unit, onToggleOnline: () -> Unit, onToggleThink: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoreOrb(active = state.busy, size = 30.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("JARVIS X", style = MaterialTheme.typography.headlineSmall, color = Jx.Text)
            Text(
                statusLine(state.brain).replace("OFFLINE", if (state.onlineSearch) "SEARCH ON" else "OFFLINE"),
                style = MaterialTheme.typography.labelSmall, color = Jx.TextDim,
            )
        }
        IconButton(onClick = onToggleThink, enabled = !state.busy) {
            Icon(
                Icons.Outlined.Psychology,
                contentDescription = if (state.thinkHarder) "Think harder on (slower)" else "Think harder off",
                tint = if (state.thinkHarder) Jx.Violet else Jx.TextDim,
            )
        }
        IconButton(onClick = onToggleOnline, enabled = !state.busy) {
            Icon(
                if (state.onlineSearch) Icons.Outlined.Public else Icons.Outlined.PublicOff,
                contentDescription = if (state.onlineSearch) "Online search on" else "Online search off",
                tint = if (state.onlineSearch) Jx.Accent else Jx.TextDim,
            )
        }
        IconButton(onClick = onNewChat, enabled = !state.busy) {
            Icon(Icons.Outlined.AddComment, contentDescription = "New chat", tint = Jx.TextDim)
        }
    }
}

private fun statusLine(s: BrainStatus) = when (s) {
    BrainStatus.NoModel -> "OFFLINE · no model loaded"
    is BrainStatus.Loading -> "Loading ${s.model.displayName}…"
    is BrainStatus.Ready -> "OFFLINE · ${s.model.displayName} ready"
    is BrainStatus.Generating -> "OFFLINE · ${s.model.displayName} thinking…"
    is BrainStatus.Error -> "Model error — see Diagnostics"
}

@Composable
private fun EmptyState(brain: BrainStatus, onOpenModels: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CoreOrb(active = brain is BrainStatus.Loading, size = 120.dp)
        Spacer(Modifier.height(24.dp))
        Text("Ji, main sun raha hoon. Bataiye, kya karna hai?", color = Jx.Text, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Everything runs on this phone. No internet, no API keys, no cloud AI.",
            color = Jx.TextDim, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onOpenModels) { Text("Manage models", color = Jx.Accent) }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    when (m.role) {
        Role.USER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                m.text,
                color = Jx.Text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(Jx.SurfaceHigh, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Role.ASSISTANT -> Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Jx.Outline, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .background(Jx.Surface, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            m.source?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(if (m.streaming) Jx.Accent else Jx.Violet, CircleShape))
                    Text("  $it", style = MaterialTheme.typography.labelSmall, color = Jx.TextDim)
                }
                Spacer(Modifier.height(6.dp))
            }
            SelectionContainer {
                Text(
                    if (m.text.isEmpty() && m.streaming) markdownLite("…") else markdownLite(m.text),
                    color = Jx.Text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (m.sources.isNotEmpty()) {
                val context = LocalContext.current
                Spacer(Modifier.height(8.dp))
                Text("Sources", style = MaterialTheme.typography.labelSmall, color = Jx.TextDim)
                m.sources.forEachIndexed { i, r ->
                    Text(
                        "[${i + 1}] ${r.title} — ${r.source}${r.date?.let { ", $it" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Jx.Accent,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url))) } },
                    )
                }
            }
            m.stats?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = Jx.TextDim)
            }
        }
        Role.NOTICE -> Text(
            m.text,
            color = if (m.isError) Jx.Bad else Jx.Warn,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    onChange: (String) -> Unit,
    busy: Boolean,
    listening: Boolean,
    voiceLanguage: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onToggleLanguage: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Jx.Background).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask in English, हिंदी or Hinglish…", color = Jx.TextDim) },
            maxLines = 5,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Jx.Accent,
                unfocusedBorderColor = Jx.Outline,
                focusedTextColor = Jx.Text,
                unfocusedTextColor = Jx.Text,
                cursorColor = Jx.Accent,
            ),
        )
        Spacer(Modifier.size(8.dp))
        if (busy) {
            IconButton(onClick = onStop, modifier = Modifier.background(Jx.Bad.copy(alpha = 0.2f), CircleShape)) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Jx.Bad)
            }
        } else if (value.isBlank()) {
            TextButton(onClick = onToggleLanguage) {
                Text(if (voiceLanguage == "hi-IN") "हि" else "EN", color = Jx.TextDim)
            }
            IconButton(
                onClick = onMic,
                enabled = !listening,
                modifier = Modifier.background(Jx.Accent.copy(alpha = if (listening) 0.45f else 0.2f), CircleShape),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Speak", tint = Jx.Accent)
            }
        } else {
            IconButton(
                onClick = onSend,
                modifier = Modifier.background(Jx.Accent.copy(alpha = 0.2f), CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Jx.Accent)
            }
        }
    }
}


/** Shows the microphone state, partial speech, speaking status and voice errors. */
@Composable
private fun VoiceBar(
    state: ChatUiState,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val l = state.listen
    val (text, color) = when {
        l is ListenState.Listening -> "🎙 Listening (offline)… speak now" to Jx.Accent
        l is ListenState.Partial -> "🎙 ${l.text}" to Jx.Text
        l is ListenState.Error -> l.message to Jx.Warn
        state.speaking -> "🔊 Speaking…" to Jx.TextDim
        else -> return
    }
    Row(
        Modifier.fillMaxWidth().background(Jx.Surface).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        when {
            l is ListenState.Listening || l is ListenState.Partial -> TextButton(onClick = onStopListening) { Text("Stop", color = Jx.Bad) }
            l is ListenState.Error && l.canDownloadModel -> TextButton(onClick = onDownload) { Text("Download", color = Jx.Accent) }
            l is ListenState.Error -> TextButton(onClick = onDismiss) { Text("OK", color = Jx.TextDim) }
            state.speaking -> IconButton(onClick = onStopSpeaking) { Icon(Icons.Outlined.VolumeOff, contentDescription = "Stop speaking", tint = Jx.TextDim) }
        }
    }
}
