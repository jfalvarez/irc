package com.jfaf.irc.domain.platform

/** Registro de eventos de uso. Android: Firebase Analytics; escritorio: no-op (decisión 0.1). */
interface AnalyticsTracker {
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
}
