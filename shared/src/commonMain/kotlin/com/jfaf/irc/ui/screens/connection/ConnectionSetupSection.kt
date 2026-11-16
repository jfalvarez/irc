package com.jfaf.irc.ui.screens.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jfaf.irc.data.prefs.ServerConfig
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.button_connect
import com.jfaf.irc.resources.cd_hide_password
import com.jfaf.irc.resources.cd_show_password
import com.jfaf.irc.resources.connection_nick_preview_hint
import com.jfaf.irc.resources.connection_nickserv_toggle
import com.jfaf.irc.resources.connection_server_footer
import com.jfaf.irc.resources.connection_tagline
import com.jfaf.irc.resources.label_nickname
import com.jfaf.irc.resources.label_nickserv_password
import com.jfaf.irc.resources.label_remember_password
import com.jfaf.irc.ui.screens.chat.NickAvatar
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.theme.IRCWhite
import com.jfaf.irc.ui.theme.PurpleStart
import com.jfaf.irc.ui.theme.ownNickColor
import org.jetbrains.compose.resources.stringResource

/** Ancho de la tarjeta en escritorio y en las ventanas anchas. */
private val CARD_MAX_WIDTH = 400.dp

/** A partir de este ancho la tarjeta va centrada, con la cabecera dentro, en vez de a todo el ancho. */
private val CENTERED_CARD_MIN_WIDTH = 600.dp

/** Cuánto sube la tarjeta sobre la cabecera en el móvil. */
private val CARD_OVERLAP = 28.dp

private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)

/**
 * Pantalla de conexión (UI-18 a UI-24): cabecera con el degradado de la marca y, debajo, una tarjeta con la vista
 * previa del nick, el formulario y el botón de conectar; el servidor, en segundo plano.
 * Va sobre el fondo neutro y sigue el modo de color elegido.
 */
@Composable
fun ConnectionSetupSection(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    nickServPasswordState: MutableState<String>,
    rememberNickServPasswordState: MutableState<Boolean>,
    serverConfig: ServerConfig,
    onConnect: (String, String, Boolean) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val centered = maxWidth >= CENTERED_CARD_MIN_WIDTH
        val scroll = rememberScrollState()

        val form: @Composable () -> Unit = {
            ConnectionForm(
                nickname = nickname,
                onNicknameChange = onNicknameChange,
                nickServPasswordState = nickServPasswordState,
                rememberNickServPasswordState = rememberNickServPasswordState,
                onConnect = onConnect
            )
        }
        val outside: @Composable () -> Unit = {
            ServerFooter(serverConfig)
        }

        if (centered) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = CARD_MAX_WIDTH)
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    BrandHeader(Modifier.fillMaxWidth().padding(vertical = 28.dp))
                    form()
                }
                Column(
                    modifier = Modifier.widthIn(max = CARD_MAX_WIDTH).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { outside() }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                BrandHeader(Modifier.fillMaxWidth().padding(top = 48.dp, bottom = CARD_OVERLAP + 24.dp))
                // La tarjeta sube sobre la cabecera: se coloca más arriba sin dejar hueco debajo
                Surface(
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val shift = CARD_OVERLAP.roundToPx()
                            layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
                        }
                ) { Column { form() } }
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { outside() }
            }
        }
    }
}

/** Franja con el degradado de la marca: logo («#» en un cuadrado redondeado), nombre y lema (UI-18). */
@Composable
private fun BrandHeader(modifier: Modifier = Modifier) {
    Column(
        // El degradado va antes que el relleno que traiga `modifier`, para que éste quede dentro de la franja
        modifier = Modifier.background(IRCTheme.gradientBrush).then(modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(IRCWhite.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text("#", color = IRCWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("IRC", color = IRCWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.connection_tagline), color = IRCWhite, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Contenido de la tarjeta: vista previa del nick, campos, NickServ plegado y «Conectar» (UI-19 a UI-22). */
@Composable
private fun ConnectionForm(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    nickServPasswordState: MutableState<String>,
    rememberNickServPasswordState: MutableState<Boolean>,
    onConnect: (String, String, Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val canConnect = nickname.isNotBlank()
    fun connect() {
        if (canConnect) onConnect(nickname, nickServPasswordState.value, rememberNickServPasswordState.value)
    }

    // Con una contraseña recordada la sección aparece ya desplegada (la contraseña llega después, de las preferencias)
    val hasSavedPassword = nickServPasswordState.value.isNotEmpty()
    var nickServExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(hasSavedPassword) { if (hasSavedPassword) nickServExpanded = true }
    var showPassword by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(20.dp)) {
        NickPreview(nickname)
        Spacer(Modifier.height(20.dp))

        FieldLabel(stringResource(Res.string.label_nickname))
        FilledField(
            value = nickname,
            onValueChange = onNicknameChange,
            icon = Icons.Filled.AlternateEmail,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FieldShape)
                .clickable { nickServExpanded = !nickServExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.connection_nickserv_toggle),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                if (nickServExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = nickServExpanded) {
            Column {
                Spacer(Modifier.height(4.dp))
                FieldLabel(stringResource(Res.string.label_nickserv_password))
                FilledField(
                    value = nickServPasswordState.value,
                    onValueChange = { nickServPasswordState.value = it },
                    icon = Icons.Filled.Lock,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (showPassword) Res.string.cd_hide_password else Res.string.cd_show_password
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.label_remember_password),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = rememberNickServPasswordState.value,
                        onCheckedChange = { rememberNickServPasswordState.value = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = PurpleStart,
                            checkedThumbColor = IRCWhite,
                            checkedBorderColor = PurpleStart
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        ConnectButton(enabled = canConnect, onClick = ::connect)
    }
}

/** Tu avatar con el degradado y el nick en el color de acento propio, tal como te verán en el chat (UI-20). */
@Composable
private fun NickPreview(nickname: String) {
    val shown = nickname.trim()
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (shown.isNotEmpty()) {
            NickAvatar(nick = shown, isOwn = true, size = 56.dp)
        } else {
            // Con el campo vacío, un avatar genérico
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (shown.isNotEmpty()) {
            Text(
                shown,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = IRCTheme.ownNickColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            stringResource(Res.string.connection_nick_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Campo relleno (`surfaceVariant`, 12 dp) con icono delante y sin línea inferior (UI-19). */
@Composable
private fun FilledField(
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val container = MaterialTheme.colorScheme.surfaceVariant
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = FieldShape,
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** «Conectar» en píldora con el degradado, como el botón de enviar; apagado mientras no haya nick (UI-22). */
@Composable
private fun ConnectButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(IRCTheme.gradientBrush, alpha = if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(Res.string.button_connect),
            color = IRCWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Pie con el servidor al que se conectará, de los Ajustes; sólo informativo (UI-24). */
@Composable
private fun ServerFooter(config: ServerConfig) {
    Text(
        stringResource(Res.string.connection_server_footer, config.host),
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
