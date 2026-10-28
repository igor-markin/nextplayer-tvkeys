package dev.anilbeesetti.nextplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.anilbeesetti.nextplayer.core.common.service.system.SystemService
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.navigation.ensureRoot
import dev.anilbeesetti.nextplayer.navigation.removeLastIfNotRoot
import dev.anilbeesetti.nextplayer.navigation.settingsNavGraph
import dev.anilbeesetti.nextplayer.settings.navigation.SettingsRoute
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : FragmentActivity() {

    private val systemService: SystemService by inject()

    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        systemService.initialize(this@MainActivity)

        installSplashScreen().setKeepOnScreenCondition {
            viewModel.state.value is MainActivityUiState.Loading
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val shouldUseDarkTheme = shouldUseDarkTheme(state = state)

            LaunchedEffect(shouldUseDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                )
            }

            NextPlayerTheme(
                darkTheme = shouldUseDarkTheme,
                highContrastDarkTheme = shouldUseHighContrastDarkTheme(state = state),
                dynamicColor = shouldUseDynamicTheming(state = state),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    // Playback is started by other apps; launching from the TV home opens settings.
                    val backStack = rememberNavBackStack(SettingsRoute)
                    backStack.ensureRoot(SettingsRoute)
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastIfNotRoot() },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                            settingsNavGraph(backStack = backStack, onExit = ::finish)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Returns `true` if dark theme should be used, as a function of the [state] and the
 * current system context.
 */
@Composable
fun shouldUseDarkTheme(
    state: MainActivityUiState,
): Boolean = when (state) {
    MainActivityUiState.Loading -> isSystemInDarkTheme()
    is MainActivityUiState.Success -> when (state.preferences.themeConfig) {
        ThemeConfig.SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.OFF -> false
        ThemeConfig.ON -> true
    }
}

@Composable
fun shouldUseHighContrastDarkTheme(
    state: MainActivityUiState,
): Boolean = when (state) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> state.preferences.useHighContrastDarkTheme
}

/**
 * Returns `true` if the dynamic color is disabled, as a function of the [state].
 */
@Composable
fun shouldUseDynamicTheming(
    state: MainActivityUiState,
): Boolean = when (state) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> state.preferences.useDynamicColors
}
