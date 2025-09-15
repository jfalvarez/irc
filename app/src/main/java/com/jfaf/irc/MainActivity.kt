package com.jfaf.irc

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background // Importación añadida
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme // Importación añadida para MaterialTheme.colorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jfaf.irc.service.IrcServiceApi
import com.jfaf.irc.ui.screens.MainScreen
import com.jfaf.irc.ui.screens.settings.SettingsScreen
import com.jfaf.irc.ui.theme.IRCAppTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalAnimationApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG_ACTIVITY = "MainActivity"
    }

    private var newMessagesCount = 0

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
        Log.d("MainActivity", "App en primer plano.")
    }

    override fun onStop() {
        super.onStop()
        IrcServiceApi.appEnteredBackground()
        Log.d("MainActivity", "App en segundo plano.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val context = LocalContext.current
            val currentActiveTarget by mainViewModel.chatScreenState.activeTarget.collectAsState()

            LaunchedEffect(mainViewModel.chatScreenState.connectionState) {
                mainViewModel.chatScreenState.connectionState.collectLatest { isConnected ->
                    logToUi("Estado Conexión VM (MainActivity): ${if (isConnected) "CONECTADO" else "DESCONECTADO"}")
                }
            }

            LaunchedEffect(mainViewModel.incomingPrivateMessageEvent) { 
                mainViewModel.incomingPrivateMessageEvent.collectLatest { pmSourceNick -> 
                    val isAppCurrentlyInForegroundByProcess = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    
                    Log.d(TAG_ACTIVITY, "Incoming PM Event from '$pmSourceNick'. App FG: $isAppCurrentlyInForegroundByProcess. Active Target: $currentActiveTarget")

                    if (!isAppCurrentlyInForegroundByProcess) {
                        Log.d(TAG_ACTIVITY, "App en BG. newMessagesCount BEFORE increment: $newMessagesCount")
                        newMessagesCount++
                        val notificationTitle = context.getString(R.string.pm_notification_title)
                        val notificationContent = if (newMessagesCount > 1) {
                            context.getString(R.string.pm_notification_content_multiple, newMessagesCount)
                        } else {
                            context.getString(R.string.pm_notification_content_single)
                        }
                        Log.i(TAG_ACTIVITY, "App en segundo plano, mostrando/actualizando notificación para '$pmSourceNick': $newMessagesCount mensajes.")
                        NotificationHelper.showPrivateMessageNotification(context, notificationTitle, notificationContent)
                    } else {
                        if (pmSourceNick.equals(currentActiveTarget, ignoreCase = true)) {
                            Log.i(TAG_ACTIVITY, "App en primer plano, PM de '$pmSourceNick' que ES el target activo. No hay sonido adicional.")
                        } else {
                            Log.i(TAG_ACTIVITY, "App en primer plano, PM de '$pmSourceNick' que NO ES el target activo ('$currentActiveTarget'). Reproduciendo sonido.")
                            try {
                                val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                val ringtone = RingtoneManager.getRingtone(context, notificationSoundUri)
                                ringtone.play()
                            } catch (e: Exception) {
                                Log.e(TAG_ACTIVITY, "Error al reproducir sonido de notificación", e)
                            }
                        }
                    }
                }
            }

            val navController = rememberNavController()

            IRCAppTheme { // Corregido para usar el Composable del Tema
                NavHost(
                    navController = navController,
                    startDestination = "main",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background) // Fondo usa el color primario del tema (PurpleStart)
                ) {
                    composable(
                        route = "main",
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
                        }
                    ) {
                        MainScreen(
                            viewModel = mainViewModel,
                            navController = navController
                        )
                    }
                    composable(
                        route = "settings",
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }) + fadeIn()
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                        }
                    ) {
                        SettingsScreen(
                            onNavigateUp = { navController.navigateUp() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        newMessagesCount = 0
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NotificationHelper.PRIVATE_MESSAGE_NOTIFICATION_ID)
        Log.d(TAG_ACTIVITY, "onResume: Actividad en primer plano. Contador de mensajes reseteado a $newMessagesCount. Notificación cancelada.")
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
