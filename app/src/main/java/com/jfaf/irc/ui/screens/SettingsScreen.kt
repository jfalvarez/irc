package com.jfaf.irc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jfaf.irc.R
import com.jfaf.irc.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val showJoinPartQuitMessages by viewModel.showJoinPartQuitMessages.collectAsState()
    val showNickChanges by viewModel.showNickChanges.collectAsState() // Nueva preferencia
    val showModeChanges by viewModel.showModeChanges.collectAsState() // Nueva preferencia

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_item_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_up)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp) // Reducido el espacio para más opciones
        ) {
            Text(
                text = stringResource(R.string.settings_section_message_preferences),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Opción para JOIN/PART/QUIT
            SettingRowWithCheckbox(
                text = stringResource(R.string.settings_option_show_join_part_quit),
                checked = showJoinPartQuitMessages,
                onCheckedChange = { viewModel.setShowJoinPartQuitMessages(it) }
            )

            // Opción para Cambios de Nick
            SettingRowWithCheckbox(
                text = stringResource(R.string.settings_option_show_nick_changes),
                checked = showNickChanges,
                onCheckedChange = { viewModel.setShowNickChanges(it) }
            )

            // Opción para Cambios de Modo
            SettingRowWithCheckbox(
                text = stringResource(R.string.settings_option_show_mode_changes),
                checked = showModeChanges,
                onCheckedChange = { viewModel.setShowModeChanges(it) }
            )
            // Future settings can be added here
        }
    }
}

// Composable reutilizable para una fila de configuración con Checkbox
@Composable
private fun SettingRowWithCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp), // Padding vertical para cada fila
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
