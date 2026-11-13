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
import com.jfaf.irc.log.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.jfaf.irc.service.AndroidNotifier
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.ui.App
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.ui.viewmodels.SignInViewModel
import com.jfaf.irc.util.NotificationHelper
import kotlinx.coroutines.flow.collectLatest
import org.koin.android.ext.android.inject
import java.util.Arrays

/**
 * Anfitrión Android de la UI común ([App]): sólo lo exclusivo de la plataforma (permiso de
 * notificaciones, borde a borde, AdMob, Google Sign-In, vibración, intents de las notificaciones).
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG_ACTIVITY = "MainActivity"
        private const val VIBRATION_DURATION_MS = 150L
    }

    private val appVisibility: AppVisibility by inject()

    private lateinit var mainViewModel: MainViewModel
    private lateinit var signInViewModel: SignInViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(TAG_ACTIVITY, "Permiso de notificación CONCEDIDO")
        } else {
            Log.w(TAG_ACTIVITY, "Permiso de notificación DENEGADO")
        }
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            signInViewModel.signInWithGoogleIdToken(account.idToken!!)
        } catch (e: ApiException) {
            Log.w(TAG_ACTIVITY, "Google sign in failed", e)
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

        MobileAds.initialize(this) {}
        val testDeviceIds = Arrays.asList("CC7C862030BF02013922527B26AC1285")
        val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        MobileAds.setRequestConfiguration(configuration)

        enableEdgeToEdge()
        askNotificationPermission()

        setContent {
            mainViewModel = koinViewModel()
            signInViewModel = koinViewModel()

            val context = LocalContext.current
            val currentActiveTarget by mainViewModel.chatScreenState.activeTarget.collectAsState()

            LaunchedEffect(Unit) {
                handleIntent(intent)
            }

            // El efecto no se reinicia al cambiar de pestaña: sin esto leería siempre la pestaña inicial.
            val latestActiveTarget by rememberUpdatedState(currentActiveTarget)

            // En segundo plano las notificaciones las genera BackgroundNotifications; aquí sólo se vibra con la app visible.
            LaunchedEffect(mainViewModel.incomingPrivateMessageEvent) {
                mainViewModel.incomingPrivateMessageEvent.collectLatest { pmSourceNick ->
                    if (isAppResumed() && !pmSourceNick.equals(latestActiveTarget, ignoreCase = true)) {
                        vibrate(context)
                    }
                }
            }

            LaunchedEffect(mainViewModel.friendCameOnlineEvent) {
                mainViewModel.friendCameOnlineEvent.collectLatest {
                    if (isAppResumed()) vibrate(context)
                }
            }

            App(
                onGoogleSignIn = ::launchGoogleSignIn,
                onRedirectToPlayStore = { redirectToPlayStore(context) },
                onExit = {
                    NotificationHelper.clearAllNotifications(context)
                    finishAndRemoveTask()
                },
                mainViewModel = mainViewModel,
                signInViewModel = signInViewModel
            )
        }
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

    private fun launchGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(signInViewModel.googleSignInClientId)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(googleSignInClient.signInIntent)
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

    private fun isAppResumed(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun vibrate(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.e(TAG_ACTIVITY, "Error al activar vibración", e)
        }
    }
}
