package com.jarvisx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.jarvisx.app.ui.about.AboutScreen
import com.jarvisx.app.ui.chat.ChatScreen
import com.jarvisx.app.ui.diagnostics.DiagnosticsScreen
import com.jarvisx.app.ui.models.ModelsScreen
import com.jarvisx.app.ui.theme.Jx
import com.jarvisx.app.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JarvisTheme { Root() } }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Chat", Icons.Outlined.Forum),
    Tab("Models", Icons.Outlined.Memory),
    Tab("Diagnostics", Icons.Outlined.Speed),
    Tab("About", Icons.Outlined.Info),
)

@Composable
private fun Root() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Jx.Background,
        bottomBar = {
            NavigationBar(containerColor = Jx.Surface) {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Jx.Accent,
                            selectedTextColor = Jx.Accent,
                            indicatorColor = Jx.SurfaceHigh,
                            unselectedIconColor = Jx.TextDim,
                            unselectedTextColor = Jx.TextDim,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                0 -> ChatScreen(onOpenModels = { selected = 1 })
                1 -> ModelsScreen()
                2 -> DiagnosticsScreen()
                else -> AboutScreen()
            }
        }
    }
}
