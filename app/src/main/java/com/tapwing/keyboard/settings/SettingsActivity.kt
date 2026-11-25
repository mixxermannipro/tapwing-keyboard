package com.tapwing.keyboard.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPreferencesRepository
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.TypingStatsStore
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.dictionary.OfflineDictionaryPackManager
import com.tapwing.keyboard.ui.theme.TapwingTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefsRepo = TapwingPreferencesRepository(this)
        val secureKeyStorage = SecureKeyStorage(this)
        val dictionaryStore = AdaptiveDictionaryStore.createForContext(this)
        val modelManager = LocalModelManager(this)
        val packManager = OfflineDictionaryPackManager(this)
        val statsStore = TypingStatsStore.createForContext(this)

        setContent {
            val prefs by prefsRepo.prefsFlow.collectAsState(initial = TapwingPrefs())
            val scope = rememberCoroutineScope()
            val baseCtx = LocalContext.current
            val localizedCtx = remember(baseCtx, prefs.appUiLocale) {
                KeyboardLocales.localizedContext(baseCtx, prefs.appUiLocale)
            }

            CompositionLocalProvider(LocalContext provides localizedCtx) {
                TapwingTheme(
                    themeMode = prefs.themeMode,
                    darkModePreference = prefs.darkMode,
                    appleModeEnabled = prefs.appleModeEnabled,
                    highContrast = prefs.highContrastKeys
                ) {
                    TapwingSettingsApp(
                        prefs = prefs,
                        secureKeyStorage = secureKeyStorage,
                        dictionaryStore = dictionaryStore,
                        modelManager = modelManager,
                        packManager = packManager,
                        statsStore = statsStore,
                        onUpdatePrefs = { transform ->
                            scope.launch {
                                prefsRepo.update(transform)
                            }
                        }
                    )
                }
            }
        }
    }
}
