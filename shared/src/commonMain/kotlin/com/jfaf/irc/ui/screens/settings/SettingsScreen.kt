package com.jfaf.irc.ui.screens.settings

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.jfaf.irc.data.prefs.ServerConfig
import com.jfaf.irc.data.prefs.MessageDensity
import com.jfaf.irc.data.prefs.ThemeMode
import com.jfaf.irc.ui.common.ThemedOutlinedTextField
import com.jfaf.irc.ui.platform.LocalPlatformUi
import com.jfaf.irc.ui.screens.terms.TermsDialog
import com.jfaf.irc.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenIgnored: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val showJoinPartQuitMessages by viewModel.showJoinPartQuitMessages.collectAsState()
    val showNickChanges by viewModel.showNickChanges.collectAsState()
    val showModeChanges by viewModel.showModeChanges.collectAsState()
    val showPingPongMessages by viewModel.showPingPongMessages.collectAsState()
    val showMediaPreviews by viewModel.showMediaPreviews.collectAsState()
    val soundAlerts by viewModel.soundAlerts.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val messageDensity by viewModel.messageDensity.collectAsState()
    val platformUi = LocalPlatformUi.current
    val supportsSoundAlerts = platformUi.supportsSoundAlerts
    var showTerms by remember { mutableStateOf(false) }
    val ignoredUsers by viewModel.ignoredUsers.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val serverConfig by viewModel.serverConfig.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.menu_item_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_navigate_up)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                ServerConfigSection(
                    config = serverConfig,
                    onSave = { host, plain, ssl -> viewModel.saveServerConfig(host, plain, ssl) },
                    onReset = { viewModel.resetServerConfig() }
                )
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.settings_section_appearance),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            items(ThemeMode.entries) { mode ->
                SettingRowWithRadio(
                    text = stringResource(
                        when (mode) {
                            ThemeMode.DARK -> Res.string.settings_theme_dark
                            ThemeMode.LIGHT -> Res.string.settings_theme_light
                            ThemeMode.SYSTEM -> Res.string.settings_theme_system
                        }
                    ),
                    selected = mode == themeMode,
                    onSelect = { viewModel.setThemeMode(mode) }
                )
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.settings_section_density),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            items(MessageDensity.entries) { density ->
                SettingRowWithRadio(
                    text = stringResource(
                        when (density) {
                            MessageDensity.COMFORTABLE -> Res.string.settings_density_comfortable
                            MessageDensity.COMPACT -> Res.string.settings_density_compact
                        }
                    ),
                    selected = density == messageDensity,
                    onSelect = { viewModel.setMessageDensity(density) }
                )
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.settings_section_message_preferences),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            item {
                SettingRowWithCheckbox(
                    text = stringResource(Res.string.settings_option_show_join_part_quit),
                    checked = showJoinPartQuitMessages,
                    onCheckedChange = { viewModel.setShowJoinPartQuitMessages(it) }
                )
            }
            item {
                SettingRowWithCheckbox(
                    text = stringResource(Res.string.settings_option_show_nick_changes),
                    checked = showNickChanges,
                    onCheckedChange = { viewModel.setShowNickChanges(it) }
                )
            }
            item {
                SettingRowWithCheckbox(
                    text = stringResource(Res.string.settings_option_show_mode_changes),
                    checked = showModeChanges,
                    onCheckedChange = { viewModel.setShowModeChanges(it) }
                )
            }
            item {
                SettingRowWithCheckbox(
                    text = stringResource(Res.string.settings_option_show_ping_messages),
                    checked = showPingPongMessages,
                    onCheckedChange = { viewModel.setShowPingPongMessages(it) }
                )
            }
            item {
                SettingRowWithCheckbox(
                    text = stringResource(Res.string.settings_option_show_media_previews),
                    checked = showMediaPreviews,
                    onCheckedChange = { viewModel.setShowMediaPreviews(it) }
                )
            }
            if (supportsSoundAlerts) {
                item {
                    SettingRowWithCheckbox(
                        text = stringResource(Res.string.settings_option_sound_alerts),
                        checked = soundAlerts,
                        onCheckedChange = { viewModel.setSoundAlerts(it) }
                    )
                }
            }
            item { platformUi.PrivacyOptionsItem() }
            item {
                Text(
                    text = stringResource(Res.string.settings_terms),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTerms = true }
                        .padding(vertical = 16.dp)
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            item {
                UserListEntryRow(
                    title = stringResource(Res.string.settings_section_friends),
                    count = friends.size,
                    onClick = onOpenFriends
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            item {
                UserListEntryRow(
                    title = stringResource(Res.string.settings_section_ignored_users),
                    count = ignoredUsers.size,
                    onClick = onOpenIgnored
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showTerms) {
        TermsDialog(onDecline = { showTerms = false })
    }
}

/** Fila de Ajustes que abre la pantalla de una lista de nicks, con cuántos hay. */
@Composable
private fun UserListEntryRow(title: String, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = count.toString(), color = MaterialTheme.colorScheme.onBackground)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun SettingRowWithRadio(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun SettingRowWithCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ServerConfigSection(
    config: ServerConfig,
    onSave: (host: String, plainPort: String, sslPort: String) -> Boolean,
    onReset: () -> Unit
) {
    // Se reinician los campos cuando cambia la configuración guardada (p. ej. tras "Restaurar").
    var host by remember(config) { mutableStateOf(config.host) }
    var plainPort by remember(config) { mutableStateOf(config.plainPort.toString()) }
    var sslPort by remember(config) { mutableStateOf(config.sslPort.toString()) }
    var feedback by remember { mutableStateOf<StringResource?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.settings_section_server),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )
        ThemedOutlinedTextField(
            value = host,
            onValueChange = { host = it; feedback = null },
            label = { Text(stringResource(Res.string.settings_server_host_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemedOutlinedTextField(
                value = plainPort,
                onValueChange = { plainPort = it; feedback = null },
                label = { Text(stringResource(Res.string.settings_server_plain_port_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            ThemedOutlinedTextField(
                value = sslPort,
                onValueChange = { sslPort = it; feedback = null },
                label = { Text(stringResource(Res.string.settings_server_ssl_port_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        feedback?.let {
            Text(
                text = stringResource(it),
                color = if (it == Res.string.settings_server_invalid) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onBackground
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val ok = onSave(host, plainPort, sslPort)
                feedback = if (ok) Res.string.settings_server_saved else Res.string.settings_server_invalid
                if (ok) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            }) { Text(stringResource(Res.string.settings_server_save)) }
            OutlinedButton(onClick = {
                onReset()
                feedback = null
            }) { Text(stringResource(Res.string.settings_server_reset)) }
        }
    }
}
