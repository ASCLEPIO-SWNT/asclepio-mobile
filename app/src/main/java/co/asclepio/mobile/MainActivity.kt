package co.asclepio.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.asclepio.mobile.core.network.HealthApi
import co.asclepio.mobile.core.network.createRetrofit
import co.asclepio.mobile.core.ui.AsclepioTheme
import co.asclepio.mobile.feature.status.StatusScreen
import co.asclepio.mobile.feature.status.StatusViewModel

class MainActivity : ComponentActivity() {
    private val healthApi by lazy { createRetrofit(BuildConfig.API_BASE_URL).create(HealthApi::class.java) }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AsclepioTheme {
                Surface(Modifier.safeDrawingPadding()) {
                    val vm = viewModel { StatusViewModel(healthApi) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    StatusScreen(
                        state = state,
                        widthSizeClass = calculateWindowSizeClass(this).widthSizeClass,
                        apiBaseUrl = BuildConfig.API_BASE_URL,
                        contract = BuildConfig.CONSUMED_CONTRACT,
                        onRetry = vm::refresh,
                    )
                }
            }
        }
    }
}
