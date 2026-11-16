package com.jfaf.irc.ui.viewmodels

import com.jfaf.irc.domain.channel.ChannelListResult

/** Estado del explorador de canales. */
sealed interface ChannelListUiState {
    /** Aún no se ha pedido la lista. */
    data object Idle : ChannelListUiState

    /** `LIST` enviado, esperando al servidor. */
    data object Loading : ChannelListUiState

    /** No hay conexión con el servidor: no se pudo pedir la lista. */
    data object NotConnected : ChannelListUiState

    /** La última lista recibida (completa, incompleta o rechazada) y cuándo, en milisegundos Unix. */
    data class Ready(val result: ChannelListResult, val loadedAtMillis: Long) : ChannelListUiState
}
