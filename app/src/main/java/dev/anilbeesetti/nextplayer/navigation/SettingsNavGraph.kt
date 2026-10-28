package dev.anilbeesetti.nextplayer.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.Setting
import dev.anilbeesetti.nextplayer.settings.navigation.aboutPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.appearancePreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.audioPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.generalPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.gesturePreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.librariesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAboutPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAppearancePreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAudioPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToGeneralPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToGesturePreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToLibraries
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToPlayerPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSubtitlePreferences
import dev.anilbeesetti.nextplayer.settings.navigation.playerPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.settingsEntry
import dev.anilbeesetti.nextplayer.settings.navigation.subtitlePreferencesEntry

/** Settings are the app's only screen; the root entry's "navigate up" leaves the app. */
fun EntryProviderScope<NavKey>.settingsNavGraph(
    backStack: NavBackStack<NavKey>,
    onExit: () -> Unit,
) {
    settingsEntry(
        onNavigateUp = { if (backStack.removeLastIfNotRoot() == null) onExit() },
        onItemClick = { setting ->
            when (setting) {
                Setting.APPEARANCE -> backStack.navigateToAppearancePreferences()
                Setting.PLAYER -> backStack.navigateToPlayerPreferences()
                Setting.GESTURES -> backStack.navigateToGesturePreferences()
                Setting.AUDIO -> backStack.navigateToAudioPreferences()
                Setting.SUBTITLE -> backStack.navigateToSubtitlePreferences()
                Setting.GENERAL -> backStack.navigateToGeneralPreferences()
                Setting.ABOUT -> backStack.navigateToAboutPreferences()
            }
        },
    )
    appearancePreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    playerPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    gesturePreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    audioPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    subtitlePreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    generalPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    aboutPreferencesEntry(
        onLibrariesClick = backStack::navigateToLibraries,
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    librariesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
}
