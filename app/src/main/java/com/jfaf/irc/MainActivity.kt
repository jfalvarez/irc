package com.jfaf.irc

import android.Manifest 
import android.app.NotificationManager
import android.content.Context // Todavía necesario para getSystemService
// import android.app.PendingIntent // Ya no es necesario aquí
// import android.content.Intent // Ya no es necesario aquí
import android.content.pm.PackageManager 
import android.os.Build 
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts 
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
// import androidx.core.app.NotificationCompat // Ya no es necesario aquí
import androidx.core.content.ContextCompat 
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle 
import androidx.lifecycle.ProcessLifecycleOwner 
import com.jfaf.irc.data.model.ParsedIrcMessage 
import com.jfaf.irc.ui.screens.MainScreen 
import com.jfaf.irc.ui.theme.IrcTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.util.NotificationHelper // IMPORTAR EL HELPER
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG_ACTIVITY = "MainActivity"
        // PRIVATE_MESSAGE_NOTIFICATION_ID movido a NotificationHelper
    }

    private var isActivityInForeground: Boolean = false 
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val context = LocalContext.current 

            LaunchedEffect(mainViewModel.rawIrcMessagesEvents) {
                mainViewModel.rawIrcMessagesEvents.collectLatest { message: ParsedIrcMessage ->
                    val logOutput = "Raw Parsed << Command: ${message.command}, " +
                                    "Prefix: ${message.prefix ?: "N/A"}, " +
                                    "Params: ${message.params.joinToString()}, " +
                                    "Trailing: ${message.trailing ?: "N/A"}" +
                                    " (Raw: '${message.rawLine}')"
                    Log.d(TAG_ACTIVITY, logOutput)
                }
            }

            LaunchedEffect(mainViewModel.connectionState) {
                mainViewModel.connectionState.collectLatest { isConnected ->
                    logToUi("Estado Conexión VM (MainActivity): ${if (isConnected) "CONECTADO" else "DESCONECTADO"}")
                }
            }

            LaunchedEffect(Unit) { 
                mainViewModel.newPrivateMessageSoundEvent.collectLatest { 
                    val isAppCurrentlyInForegroundByProcess = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    Log.d(TAG_ACTIVITY, "Evento de nuevo PM. App en FG (ProcessLifecycle)? $isAppCurrentlyInForegroundByProcess.")
                    
                    if (!isAppCurrentlyInForegroundByProcess) {
                        Log.d(TAG_ACTIVITY, "App en BG. newMessagesCount BEFORE increment: $newMessagesCount")
                        newMessagesCount++ 
                        val notificationTitle = "Nuevo Mensaje Privado"
                        val notificationContent = if (newMessagesCount > 1) {
                            "Tienes $newMessagesCount mensajes nuevos"
                        } else {
                            "Has recibido un nuevo mensaje."
                        }
                        Log.i(TAG_ACTIVITY, "App en segundo plano, mostrando/actualizando notificación: $newMessagesCount mensajes.")
                        Log.d(TAG_ACTIVITY, "[DIAGNOSTICO] Llamando a NotificationHelper.showPrivateMessageNotification...") // LOG DE DIAGNÓSTICO
                        NotificationHelper.showPrivateMessageNotification(context, notificationTitle, notificationContent)
                    } else {
                        Log.i(TAG_ACTIVITY, "App en primer plano, la notificación del sistema NO se mostrará.")
                    }
                }
            }

            IrcTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = mainViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityInForeground = true
        newMessagesCount = 0 
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NotificationHelper.PRIVATE_MESSAGE_NOTIFICATION_ID)
        Log.d(TAG_ACTIVITY, "onResume: Actividad en primer plano. Contador de mensajes reseteado a $newMessagesCount. Notificación cancelada.")
    }

    override fun onPause() {
        super.onPause()
        isActivityInForeground = false
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

    // La función showPrivateMessageNotification ha sido movida a NotificationHelper.kt

    private fun logToUi(message: String) {
        Log.i(TAG_ACTIVITY, message)
    }
}
