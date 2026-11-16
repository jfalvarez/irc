package com.jfaf.irc.domain.linkpreview

import com.jfaf.irc.domain.chat.LinkFinder
import com.jfaf.irc.domain.chat.MessageGrouping
import com.jfaf.irc.domain.chat.model.UiChatMessage

/** Qué enlace de un mensaje se previsualiza (UI-31). */
object LinkPreviewTarget {

    /**
     * El primer enlace http(s) a una web pública del texto de un mensaje de conversación; null si no hay ninguno o si el
     * mensaje ya lleva una imagen o un vídeo (de esos se encarga la vista previa de medios).
     */
    fun of(message: UiChatMessage): String? {
        if (!MessageGrouping.isSpeech(message) || message.mediaUrl != null) return null
        val text = MessageGrouping.speech(message)?.body?.text ?: message.annotatedString?.text ?: message.fullText
        return LinkFinder.find(text)
            .map { it.target }
            .firstOrNull { UrlSafety.isPublicHttpUrl(it) }
    }
}
