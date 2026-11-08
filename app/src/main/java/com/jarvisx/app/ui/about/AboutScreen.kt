package com.jarvisx.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvisx.app.BuildConfig
import com.jarvisx.app.ui.common.Card
import com.jarvisx.app.ui.common.SectionTitle
import com.jarvisx.app.ui.theme.Jx

private data class Credit(val name: String, val licence: String, val use: String)

private val credits = listOf(
    Credit("llama.cpp (ggml-org)", "MIT", "Runs the language model on the CPU"),
    Credit("HearoPilot-App lib-llama-android (Helldez)", "Apache-2.0", "JNI bridge to llama.cpp, modified by JARVIS X"),
    Credit("llama.cpp Android example (ggml authors)", "MIT", "Origin of the JNI bridge"),
    Credit("Qwen3.5 models (Alibaba Qwen team)", "Apache-2.0", "Downloaded by you; not bundled"),
    Credit("GGUF conversions (Unsloth)", "Apache-2.0 (inherits Qwen)", "Quantised model files"),
    Credit("Android Jetpack / Compose", "Apache-2.0", "User interface"),
    Credit("Kotlin, kotlinx.coroutines", "Apache-2.0", "Language and concurrency"),
)

@Composable
fun AboutScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("About JARVIS X", style = MaterialTheme.typography.headlineSmall, color = Jx.Text)
        Card {
            Text(
                "A personal AI assistant that thinks on your phone. No internet needed to answer, no API keys, " +
                    "no subscriptions, no cloud AI. Your conversations stay on this device.",
                color = Jx.Text, style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Honest limits: a small local model can be wrong, its knowledge has a cutoff date, and it is far " +
                    "weaker than large cloud models. Maths and the clock are answered by exact tools instead.",
                color = Jx.TextDim, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Card {
            SectionTitle("Open-source credits")
            credits.forEach {
                Text(it.name, color = Jx.Text, style = MaterialTheme.typography.bodyMedium)
                Text("${it.licence} · ${it.use}", color = Jx.TextDim, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                "JARVIS X is licensed under Apache-2.0. Full licence texts and change notes are in the source " +
                    "repository (LICENSE, NOTICE, THIRD_PARTY.md).",
                color = Jx.TextDim, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Card {
            SectionTitle("Name")
            Text(
                "JARVIS X is an independent project, not affiliated with or endorsed by Marvel or Disney.",
                color = Jx.TextDim, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text("Version ${BuildConfig.VERSION_NAME}", color = Jx.TextDim, style = MaterialTheme.typography.labelSmall)
    }
}
