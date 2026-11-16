package com.jfaf.irc.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.domain.platform.Haptics
import com.jfaf.irc.domain.platform.UpdateChecker
import com.jfaf.irc.ui.platform.LocalPlatformUi
import com.jfaf.irc.ui.platform.PlatformUi
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.dialog_exit_cancel_button
import com.jfaf.irc.resources.dialog_exit_confirm_button
import com.jfaf.irc.resources.dialog_exit_message
import com.jfaf.irc.resources.dialog_exit_title
import com.jfaf.irc.resources.force_update_button_text
import com.jfaf.irc.resources.force_update_message
import com.jfaf.irc.resources.force_update_title
import com.jfaf.irc.resources.settings_button_remove_friend
import com.jfaf.irc.resources.settings_button_unignore
import com.jfaf.irc.resources.settings_no_friends
import com.jfaf.irc.resources.settings_no_ignored_users
import com.jfaf.irc.resources.settings_section_friends
import com.jfaf.irc.resources.settings_section_ignored_users
import com.jfaf.irc.resources.user_list_add_friend
import com.jfaf.irc.resources.user_list_add_ignored
import com.jfaf.irc.ui.screens.MainScreen
import com.jfaf.irc.ui.screens.settings.SettingsScreen
import com.jfaf.irc.ui.screens.settings.UserListScreen
import com.jfaf.irc.ui.theme.IRCAppTheme
import com.jfaf.irc.ui.viewmodels.SettingsViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.ui.viewmodels.TermsViewModel
import com.jfaf.irc.ui.screens.terms.TermsDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Raíz común de la UI: tema, navegación, avisos de salir y de actualización.
 * Lo que depende de la plataforma entra como parámetro.
 *
 * @param platformUi piezas de la plataforma (anuncios, imágenes y vídeo).
 * @param onRedirectToPlayStore abre la ficha de la tienda desde el aviso de actualización.
 * @param onExit cierra la app, una vez desconectado (en Android, quita las notificaciones y la tarea).
 */
@OptIn(ExperimentalComposeUiApi::class) // BackHandler multiplataforma
@Composable
fun App(
    platformUi: PlatformUi,
    onRedirectToPlayStore: () -> Unit,
    onExit: () -> Unit,
    mainViewModel: MainViewModel = koinViewModel(),
    termsViewModel: TermsViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    updateChecker: UpdateChecker = koinInject(),
    haptics: Haptics = koinInject(),
    appVisibility: AppVisibility = koinInject()
) {
    val navController = rememberNavController()
    val connectionState by mainViewModel.chatScreenState.connectionState.collectAsState()
    var showExitConfirmationDialog by remember { mutableStateOf(false) }
    val showUpdateDialog by updateChecker.isUpdateRequired.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        updateChecker.check()
    }

    val currentActiveTarget by mainViewModel.chatScreenState.activeTarget.collectAsState()
    // El efecto no se reinicia al cambiar de pestaña: sin esto leería siempre la pestaña inicial.
    val latestActiveTarget by rememberUpdatedState(currentActiveTarget)

    // En segundo plano las notificaciones las genera BackgroundNotifications; aquí sólo se vibra con la app visible.
    LaunchedEffect(mainViewModel.incomingPrivateMessageEvent) {
        mainViewModel.incomingPrivateMessageEvent.collectLatest { pmSourceNick ->
            if (appVisibility.isInForeground.value && !pmSourceNick.equals(latestActiveTarget, ignoreCase = true)) {
                haptics.vibrate()
            }
        }
    }

    LaunchedEffect(mainViewModel.friendCameOnlineEvent) {
        mainViewModel.friendCameOnlineEvent.collectLatest {
            if (appVisibility.isInForeground.value) haptics.vibrate()
        }
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

    CompositionLocalProvider(LocalPlatformUi provides platformUi) {
        val themeMode by settingsViewModel.themeMode.collectAsState()
        IRCAppTheme(darkTheme = themeMode.isDark(systemDark = isSystemInDarkTheme())) {
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
                        snackbarHostState = snackbarHostState
                    )
                }
                composable(
                    route = "settings",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
                ) {
                    SettingsScreen(
                        onNavigateUp = { navController.navigateUp() },
                        onOpenFriends = { navController.navigate("settings/friends") },
                        onOpenIgnored = { navController.navigate("settings/ignored") }
                    )
                }
                composable(
                    route = "settings/ignored",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
                ) {
                    val viewModel: SettingsViewModel = koinViewModel()
                    val ignored by viewModel.ignoredUsers.collectAsState()
                    UserListScreen(
                        title = stringResource(Res.string.settings_section_ignored_users),
                        users = ignored,
                        emptyText = stringResource(Res.string.settings_no_ignored_users),
                        removeButtonText = stringResource(Res.string.settings_button_unignore),
                        addLabel = { stringResource(Res.string.user_list_add_ignored, it) },
                        onAdd = viewModel::addIgnoredUser,
                        onRemove = viewModel::removeIgnoredUser,
                        onNavigateUp = { navController.navigateUp() }
                    )
                }
                composable(
                    route = "settings/friends",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
                ) {
                    val viewModel: SettingsViewModel = koinViewModel()
                    val friends by viewModel.friends.collectAsState()
                    UserListScreen(
                        title = stringResource(Res.string.settings_section_friends),
                        users = friends,
                        emptyText = stringResource(Res.string.settings_no_friends),
                        removeButtonText = stringResource(Res.string.settings_button_remove_friend),
                        addLabel = { stringResource(Res.string.user_list_add_friend, it) },
                        onAdd = viewModel::addFriend,
                        onRemove = viewModel::removeFriend,
                        onNavigateUp = { navController.navigateUp() }
                    )
                }
            }

            if (showUpdateDialog) {
                AlertDialog(
                    onDismissRequest = { /* No hacer nada */ },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(Res.string.force_update_title)) },
                    text = { Text(stringResource(Res.string.force_update_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = onRedirectToPlayStore,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text(stringResource(Res.string.force_update_button_text))
                        }
                    }
                )
            }

            // Condiciones de uso: hasta aceptarlas no se puede usar la app (null: aún leyendo el ajuste)
            val termsAccepted by termsViewModel.termsAccepted.collectAsState()
            if (termsAccepted == false) {
                TermsDialog(onDecline = onExit, onAccept = termsViewModel::accept)
            }

            if (showExitConfirmationDialog) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmationDialog = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(Res.string.dialog_exit_title)) },
                    text = { Text(stringResource(Res.string.dialog_exit_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showExitConfirmationDialog = false
                                mainViewModel.disconnectFromServerAndStopService()
                                onExit()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(Res.string.dialog_exit_confirm_button))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showExitConfirmationDialog = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text(stringResource(Res.string.dialog_exit_cancel_button))
                        }
                    }
                )
            }
        }
    }
}
