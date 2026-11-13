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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.jfaf.irc.domain.platform.UpdateChecker
import com.jfaf.irc.service.IrcService
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.ui.screens.MainScreen
import com.jfaf.irc.ui.screens.settings.SettingsScreen
import com.jfaf.irc.ui.theme.IRCAppTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.ui.viewmodels.SignInViewModel
import com.jfaf.irc.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Arrays
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG_ACTIVITY = "MainActivity"
        private const val VIBRATION_DURATION_MS = 150L
    }

    @Inject
    lateinit var updateChecker: UpdateChecker

    @Inject
    lateinit var appVisibility: AppVisibility

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

    val signInLauncher = registerForActivityResult(
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
            mainViewModel = hiltViewModel()
            signInViewModel = hiltViewModel()

            val context = LocalContext.current
            val navController = rememberNavController()
            val connectionState by mainViewModel.chatScreenState.connectionState.collectAsState()
            var showExitConfirmationDialog by remember { mutableStateOf(false) }

            val currentActiveTarget by mainViewModel.chatScreenState.activeTarget.collectAsState()
            val showUpdateDialog by updateChecker.isUpdateRequired.collectAsState()

            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                updateChecker.check()
                handleIntent(intent)
            }

            LaunchedEffect(mainViewModel.snackbarEvents) {
                mainViewModel.snackbarEvents.collectLatest { message ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }

            // El efecto no se reinicia al cambiar de pestaña: sin esto leería siempre la pestaña inicial.
            val latestActiveTarget by rememberUpdatedState(currentActiveTarget)

            // En segundo plano las notificaciones las genera IrcService; aquí sólo se vibra con la app visible.
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
                            signInViewModel = signInViewModel,
                            mainActivity = this@MainActivity,
                            navController = navController,
                            snackbarHostState = snackbarHostState
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
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
        setIntent(intent)
        if (::mainViewModel.isInitialized) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(IrcService.EXTRA_TARGET_FOR_NOTIFICATION)?.let { target ->
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
