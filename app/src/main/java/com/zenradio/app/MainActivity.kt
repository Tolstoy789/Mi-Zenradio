package com.zenradio.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenradio.app.data.StationRepository
import com.zenradio.app.playback.RadioViewModel
import com.zenradio.app.ui.ZenRadioScreen
import com.zenradio.app.ui.theme.ZenPalettes
import com.zenradio.app.ui.theme.ZenRadioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    // Android 13+: pedir permiso para mostrar la notificación de medios.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* la radio suena igual */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Pantalla de arranque con la "Luna zen" animada. La mantenemos hasta que
        // termina la animación y luego se desvanece suavemente.
        val splashStart = SystemClock.elapsedRealtime()
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                SystemClock.elapsedRealtime() - splashStart < SPLASH_ANIMATION_MS
            }
            setOnExitAnimationListener { splash ->
                splash.view.animate()
                    .alpha(0f)
                    .setDuration(300L)
                    .withEndAction { splash.remove() }
                    .start()
            }
        }
        // Iconos de la barra de estado claros u oscuros según el tema guardado.
        val savedTheme = ZenPalettes.byId(StationRepository.get(this).themeId.value)
        applySystemBars(lightTheme = savedTheme.isLight)
        super.onCreate(savedInstanceState)
        askNotificationPermission()

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            // Los niveles de audio se leen al dibujar, no aquí: así la pantalla
            // no se recompone 30 veces por segundo.
            val levels = viewModel.audioLevels.collectAsStateWithLifecycle()
            val palette = ZenPalettes.byId(state.themeId)
            LaunchedEffect(palette.isLight) { applySystemBars(lightTheme = palette.isLight) }

            ZenRadioTheme(palette) {
                ZenRadioScreen(state = state, levels = { levels.value }, actions = viewModel)
            }
        }
    }

    private fun applySystemBars(lightTheme: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        if (lightTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(transparent, transparent),
                // Android 8.0 no admite iconos oscuros en la barra de navegación: velo oscuro.
                navigationBarStyle = SystemBarStyle.light(transparent, NAV_BAR_DARK_SCRIM),
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(transparent),
                navigationBarStyle = SystemBarStyle.dark(transparent),
            )
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val SPLASH_ANIMATION_MS = 1_000L
        val NAV_BAR_DARK_SCRIM = 0x801B1B1B.toInt()
    }
}
