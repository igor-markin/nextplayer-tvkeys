package dev.anilbeesetti.nextplayer

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.core.common.di.DiQualifiers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.SearchHistory
import dev.anilbeesetti.nextplayer.feature.player.PlayerViewModel
import dev.anilbeesetti.nextplayer.settings.screens.about.AboutPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.about.LibrariesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.appearance.AppearancePreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.audio.AudioPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.general.GeneralPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.gesture.GesturePreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.player.PlayerPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.subtitle.SubtitlePreferencesViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.viewmodel.resolveViewModel

@RunWith(AndroidJUnit4::class)
class KoinGraphTest {
    private val koin get() = GlobalContext.get()

    @Test
    fun sharedServicesAndAllThreePreferenceStoresResolve() {
        runBlocking {
            assertSame(koin.get<PreferencesRepository>(), koin.get<PreferencesRepository>())
            assertSame(koin.get<MediaOperationsService>(), koin.get<MediaOperationsService>())
            assertSame(koin.get<MediaService>(), koin.get<MediaService>())
            assertSame(koin.get<MediaSynchronizer>(), koin.get<MediaSynchronizer>())
            withTimeout(10_000) {
                // Read the typed values to catch collisions between erased DataStore keys.
                koin.get<DataStore<ApplicationPreferences>>(named(DiQualifiers.APP_PREFERENCES)).data.first().mediaViewMode
                koin.get<DataStore<PlayerPreferences>>(named(DiQualifiers.PLAYER_PREFERENCES)).data.first().resume
                koin.get<DataStore<SearchHistory>>(named(DiQualifiers.SEARCH_HISTORY)).data.first().queries.size
            }
        }
    }

    @Test
    fun viewModelsResolveWithRuntimeParametersAndRespectTheirStore() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val store = ViewModelStore()
            try {
                viewModel<MainViewModel>(store)
                viewModel<PlayerViewModel>(store, PlayerViewModel.Output({}, {}, {}, {}, {}, {}, {}))
                viewModel<AboutPreferencesViewModel>(store, AboutPreferencesViewModel.Output({}, {}))
                viewModel<LibrariesViewModel>(store, LibrariesViewModel.Output({}))
                viewModel<AppearancePreferencesViewModel>(store, AppearancePreferencesViewModel.Output({}))
                viewModel<AudioPreferencesViewModel>(store, AudioPreferencesViewModel.Output({}))
                viewModel<GeneralPreferencesViewModel>(store, GeneralPreferencesViewModel.Output({}))
                viewModel<GesturePreferencesViewModel>(store, GesturePreferencesViewModel.Output({}))
                viewModel<PlayerPreferencesViewModel>(store, PlayerPreferencesViewModel.Output({}))
                viewModel<SubtitlePreferencesViewModel>(store, SubtitlePreferencesViewModel.Output({}))
            } finally {
                store.clear()
            }
        }
    }

    @OptIn(KoinInternalApi::class)
    private inline fun <reified T : ViewModel> viewModel(store: ViewModelStore, vararg parameters: Any): T = resolveViewModel(
        vmClass = T::class,
        viewModelStore = store,
        extras = CreationExtras.Empty,
        scope = koin.scopeRegistry.rootScope,
        parameters = { parametersOf(*parameters) },
    )
}
