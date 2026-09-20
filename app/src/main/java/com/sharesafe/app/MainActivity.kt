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

    private val incomingImage = androidx.compose.runtime.mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingImage.value = intent?.extractSharedImage()
        enableEdgeToEdge()
        setContent {
            ShareSafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ShareSafeRoot(
                        incoming = incomingImage.value,
                        onIncomingConsumed = { incomingImage.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        intent.extractSharedImage()?.let { incomingImage.value = it }
    }

    private fun Intent.extractSharedImage(): Uri? {
        if (action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) return uri
        }
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            return uris?.firstOrNull()
        }
        return null
    }
}

@Composable
private fun ShareSafeRoot(incoming: Uri?, onIncomingConsumed: () -> Unit) {
    val vm: MainViewModel = viewModel()

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(vm::loadImage) }

    androidx.compose.runtime.LaunchedEffect(incoming) {
        incoming?.let {
            vm.loadImage(it)
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
            error = vm.errorMessage,
            onPick = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        Screen.SCANNING -> ScanningScreen(
            phase = vm.scanPhase,
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
