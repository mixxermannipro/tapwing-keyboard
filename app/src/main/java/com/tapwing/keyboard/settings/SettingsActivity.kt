package com.tapwing.keyboard.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPreferencesRepository
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.ui.theme.TapwingTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefsRepo = TapwingPreferencesRepository(this)
        val secureKeyStorage = SecureKeyStorage(this)

        setContent {
            val prefs by prefsRepo.prefsFlow.collectAsState(initial = TapwingPrefs())
            val scope = rememberCoroutineScope()

            TapwingTheme(
                themeMode = prefs.themeMode,
                darkModePreference = prefs.darkMode
            ) {
                TapwingSettingsApp(
                    prefs = prefs,
                    secureKeyStorage = secureKeyStorage,
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
