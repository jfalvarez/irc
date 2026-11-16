package com.jfaf.irc.ui.viewmodels

import com.jfaf.irc.domain.user.WhoisResult

/** Estado del diálogo de WHOIS. */
sealed interface WhoisUiState {
    /** WHOIS enviado, esperando la respuesta del servidor. */
    data class Loading(val nick: String) : WhoisUiState

    data class Result(val result: WhoisResult) : WhoisUiState
}
