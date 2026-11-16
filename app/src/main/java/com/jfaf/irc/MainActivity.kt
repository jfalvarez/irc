package com.jfaf.irc

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.jfaf.irc.log.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.koin.compose.viewmodel.koinViewModel
import com.jfaf.irc.privacy.AdsConsentManager
import com.jfaf.irc.service.AndroidNotifier
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.ui.App
import com.jfaf.irc.ui.platform.AndroidPlatformUi
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.util.NotificationHelper
import org.koin.android.ext.android.inject

/**
 * Anfitrión Android de la UI común ([App]): sólo lo exclusivo de la plataforma (permiso de
 * notificaciones, borde a borde, consentimiento de AdMob, vibración, intents de las notificaciones).
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG_ACTIVITY = "MainActivity"
    }

    private val appVisibility: AppVisibility by inject()
    private val adsConsent: AdsConsentManager by inject()

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
        appVisibility.enteredForeground()
        Log.d(TAG_ACTIVITY, "App en primer plano.")
    }

    override fun onStop() {
        super.onStop()
        appVisibility.enteredBackground()
        Log.d(TAG_ACTIVITY, "App en segundo plano.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        askNotificationPermission()

        setContent {
            mainViewModel = koinViewModel()

            val context = LocalContext.current

            LaunchedEffect(Unit) {
                handleIntent(intent)
            }

            App(
                platformUi = AndroidPlatformUi,
                onRedirectToPlayStore = { redirectToPlayStore(context) },
                onExit = {
                    NotificationHelper.clearAllNotifications(context)
                    finishAndRemoveTask()
                },
                mainViewModel = mainViewModel
            )
        }

        // Formulario de consentimiento (RGPD) si hace falta; AdMob y la analítica esperan a la respuesta.
        // Va al final, con la ventana ya montada: antes de enableEdgeToEdge/setContent, UMP la tocaba a
        // la vez que el hilo principal y cerraba la app ("Window couldn't find content container view").
        adsConsent.gatherConsent(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::mainViewModel.isInitialized) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(AndroidNotifier.EXTRA_TARGET_FOR_NOTIFICATION)?.let { target ->
            if (::mainViewModel.isInitialized) {
                if (target.startsWith("#")) {
                    mainViewModel.setActiveTarget(target)
                } else {
                    mainViewModel.openPrivateMessage(target)
                }
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

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
