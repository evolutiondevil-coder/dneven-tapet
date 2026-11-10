package bg.dnevnitapet.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

enum class Screen { HOME, HISTORY, FAVORITES, SETTINGS }

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                when (screen) {
                    Screen.HOME -> HomeScreen(vm, onNavigate = { screen = it })
                    Screen.HISTORY -> ImageGridScreen(vm, favorites = false, onBack = { screen = Screen.HOME })
                    Screen.FAVORITES -> ImageGridScreen(vm, favorites = true, onBack = { screen = Screen.HOME })
                    Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.HOME })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}
