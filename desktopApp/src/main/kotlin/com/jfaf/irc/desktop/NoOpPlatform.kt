package com.jfaf.irc.desktop

import com.jfaf.irc.domain.config.AppConfig
import com.jfaf.irc.domain.config.parseKeywordList
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.domain.platform.AdProvider
import com.jfaf.irc.domain.platform.AnalyticsTracker
import com.jfaf.irc.domain.platform.ConnectionHost
import com.jfaf.irc.domain.platform.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Implementaciones de escritorio de lo que, según la decisión 0.1, no tiene función en el PC:
 * sin anuncios, sin analítica, sin aviso de actualización y sin vibración.
 */

/** Palabras del filtro de imágenes: variable de entorno `IRC_IMAGE_FILTER_KEYWORDS`, separadas por comas. */
class DesktopAppConfig(
    raw: String = System.getenv("IRC_IMAGE_FILTER_KEYWORDS").orEmpty()
) : AppConfig {
    override val imageFilterKeywords: Set<String> = parseKeywordList(raw)
}

class NoAdProvider : AdProvider {
    override val currentAd: StateFlow<AdHandle?> = MutableStateFlow<AdHandle?>(null).asStateFlow()
    override fun load() {}
}

class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun logEvent(name: String, params: Map<String, String>) {}
}

class NoUpdateChecker : UpdateChecker {
    override val isUpdateRequired: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override fun check() {}
}

/** La sesión vive lo que viva el proceso: no hay nada que mantener vivo. */
class DesktopConnectionHost : ConnectionHost {
    override fun start() {}
    override fun stop() {}
}
