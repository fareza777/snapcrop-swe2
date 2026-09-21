package com.sharesafe.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharesafe.app.ui.ExportScreen
import com.sharesafe.app.ui.HomeScreen
import com.sharesafe.app.ui.RedactScreen
import com.sharesafe.app.ui.ScanningScreen
import com.sharesafe.app.ui.theme.ShareSafeTheme

class MainActivity : ComponentActivity() {

    private val incomingImages = androidx.compose.runtime.mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingImages.value = intent?.extractSharedImages().orEmpty()
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            ShareSafeTheme(dynamicColor = vm.dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ShareSafeRoot(
                        vm = vm,
                        incoming = incomingImages.value,
                        onIncomingConsumed = { incomingImages.value = emptyList() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        intent.extractSharedImages().takeIf { it.isNotEmpty() }
            ?.let { incomingImages.value = it }
    }

    private fun Intent.extractSharedImages(): List<Uri> {
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (!uris.isNullOrEmpty()) return uris.toList()
        }
        if (action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) return listOf(uri)
        }
        return emptyList()
    }
}

@Composable
private fun ShareSafeRoot(
    vm: MainViewModel,
    incoming: List<Uri>,
    onIncomingConsumed: () -> Unit,
) {
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
    ) { uris -> if (uris.isNotEmpty()) vm.loadQueue(uris) }

    androidx.compose.runtime.LaunchedEffect(incoming) {
        if (incoming.isNotEmpty()) {
            vm.loadQueue(incoming)
            onIncomingConsumed()
        }
    }

    BackHandler(enabled = vm.screen != Screen.HOME) {
        when (vm.screen) {
            Screen.EXPORT -> vm.navigateTo(Screen.EDITOR)
            Screen.EDITOR, Screen.SCANNING -> vm.reset()
            Screen.HOME -> {}
        }
    }

    when (vm.screen) {
        Screen.HOME -> HomeScreen(
            vm = vm,
            error = vm.errorMessage,
            onPick = {
                pickImages.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        Screen.SCANNING -> ScanningScreen(
            phase = vm.scanPhase,
            progress = vm.scanProgress,
            thumbnail = vm.source,
            queueIndex = vm.queuePos + 1,
            queueTotal = vm.queueSize,
            onCancel = vm::reset,
        )
        Screen.EDITOR -> RedactScreen(
            vm = vm,
            onDone = vm::goExport,
            onClose = vm::reset,
        )
        Screen.EXPORT -> ExportScreen(
            vm = vm,
            onBack = { vm.navigateTo(Screen.EDITOR) },
            onFinish = vm::reset,
        )
    }
}
