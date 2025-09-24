package com.jfaf.irc

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jfaf.irc.config.RemoteConfigManager
import com.jfaf.irc.service.IrcService
import com.jfaf.irc.service.IrcServiceApi
import com.jfaf.irc.ui.screens.MainScreen
import com.jfaf.irc.ui.screens.settings.SettingsScreen
import com.jfaf.irc.ui.theme.IRCAppTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.util.NotificationHelper // Importación para NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalAnimationApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG_ACTIVITY = "MainActivity"
        private const val VIBRATION_DURATION_MS = 150L
    }

    @Inject
    lateinit var remoteConfigManager: RemoteConfigManager

    private lateinit var mainViewModel: MainViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(TAG_ACTIVITY, "Permiso de notificación CONCEDIDO")
        } else {
            Log.w(TAG_ACTIVITY, "Permiso de notificación DENEGADO")
        }
    }

    override fun onStart() {
        super.onStart()
        IrcServiceApi.appEnteredForeground()
        Log.d(TAG_ACTIVITY, "App en primer plano.")
    }

    override fun onStop() {
        super.onStop()
        IrcServiceApi.appEnteredBackground()
        Log.d(TAG_ACTIVITY, "App en segundo plano.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()

        setContent {
            mainViewModel = hiltViewModel()
            val context = LocalContext.current
            val navController = rememberNavController()
            val connectionState by IrcServiceApi.connectionState.collectAsState()
            var showExitConfirmationDialog by remember { mutableStateOf(false) }
            
            val currentActiveTarget by mainViewModel.chatScreenState.activeTarget.collectAsState()
            val showUpdateDialog by remoteConfigManager.isUpdateRequired.collectAsState()

            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope() 

            LaunchedEffect(Unit) {
                remoteConfigManager.fetchAndActivateConfig()
                handleIntent(intent)
            }

            LaunchedEffect(mainViewModel.snackbarEvents) { // Observar snackbarEvents
                mainViewModel.snackbarEvents.collectLatest { message ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }

            LaunchedEffect(mainViewModel.chatScreenState.connectionState) { // Observar connectionState desde ViewModel
                mainViewModel.chatScreenState.connectionState.collectLatest { isConnected ->
                    logToUi("Estado Conexión VM (MainActivity): ${if (isConnected) "CONECTADO" else "DESCONECTADO"}")
                }
            }

            LaunchedEffect(mainViewModel.incomingPrivateMessageEvent) {
                mainViewModel.incomingPrivateMessageEvent.collectLatest { pmSourceNick ->
                    val isAppCurrentlyInForegroundByProcess = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    Log.d(TAG_ACTIVITY, "Incoming PM Event from '$pmSourceNick'. App FG: $isAppCurrentlyInForegroundByProcess. Active Target: $currentActiveTarget")
                    if (!isAppCurrentlyInForegroundByProcess) {
                        Log.d(TAG_ACTIVITY, "App en BG. PM Event recibido de '$pmSourceNick'. IrcService se encargará de la notificación si es necesario.")
                    } else {
                        if (pmSourceNick.equals(currentActiveTarget, ignoreCase = true)) {
                            Log.i(TAG_ACTIVITY, "App en primer plano, PM de '$pmSourceNick' que ES el target activo. No hay feedback adicional.")
                        } else {
                            Log.i(TAG_ACTIVITY, "App en primer plano, PM de '$pmSourceNick' que NO ES el target activo ('$currentActiveTarget'). Activando vibración.")
                            try {
                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(VIBRATION_DURATION_MS)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG_ACTIVITY, "Error al activar vibración", e)
                            }
                        }
                    }
                }
            }

            IRCAppTheme {
                BackHandler(enabled = connectionState && navController.previousBackStackEntry == null) {
                    showExitConfirmationDialog = true
                }

                NavHost(
                    navController = navController,
                    startDestination = "main",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    composable(
                        route = "main",
                        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) + fadeOut() },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() }
                    ) {
                        MainScreen(
                            viewModel = mainViewModel, 
                            navController = navController, 
                            snackbarHostState = snackbarHostState // Pasar snackbarHostState
                        )
                    }
                    composable(
                        route = "settings",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
                    ) {
                        SettingsScreen(onNavigateUp = { navController.navigateUp() })
                    }
                }

                if (showUpdateDialog) {
                    AlertDialog(
                        onDismissRequest = { /* No hacer nada */ },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = { Text(stringResource(R.string.force_update_title)) },
                        text = { Text(stringResource(R.string.force_update_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = { redirectToPlayStore(this@MainActivity) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                Text(stringResource(R.string.force_update_button_text))
                            }
                        }
                    )
                }

                if (showExitConfirmationDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirmationDialog = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = { Text(stringResource(R.string.dialog_exit_title)) },
                        text = { Text(stringResource(R.string.dialog_exit_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExitConfirmationDialog = false
                                    mainViewModel.disconnectFromServerAndStopService()
                                    NotificationHelper.clearAllNotifications(context)
                                    finishAndRemoveTask() 
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) // Usar color de error para la acción destructiva
                            ) {
                                Text(stringResource(R.string.dialog_exit_confirm_button))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showExitConfirmationDialog = false },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                Text(stringResource(R.string.dialog_exit_cancel_button))
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG_ACTIVITY, "onNewIntent recibido.")
        setIntent(intent)
        if (::mainViewModel.isInitialized) {
            handleIntent(intent)
        } else {
            Log.w(TAG_ACTIVITY, "onNewIntent: mainViewModel no inicializado aún.")
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(IrcService.EXTRA_TARGET_FOR_NOTIFICATION)?.let { target ->
            Log.i(TAG_ACTIVITY, "Intent de notificación recibido para target: $target")
            if (::mainViewModel.isInitialized) {
                if (target.startsWith("#")) {
                    mainViewModel.setActiveTarget(target)
                } else {
                    mainViewModel.openPrivateMessage(target)
                }
            } else {
                Log.e(TAG_ACTIVITY, "handleIntent no pudo procesar: mainViewModel no está inicializado.")
            }
        }
    }

    private fun redirectToPlayStore(context: Context) {
        val appPackageName = context.packageName
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
        } catch (anfe: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG_ACTIVITY, "onResume: Actividad en primer plano.")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG_ACTIVITY, "onPause: Actividad NO está en primer plano.")
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG_ACTIVITY, "Permiso de notificación ya concedido.")
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun logToUi(message: String) {
        Log.i(TAG_ACTIVITY, message)
    }
}
