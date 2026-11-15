package com.jfaf.irc.domain.chat

/** Reglas puras para detectar que un mensaje menciona tu nick (UI-5). */
object Mentions {
    // Caracteres que puede llevar un nick (RFC 2812); fuera de estos, el nick queda delimitado como palabra
    private fun isNickChar(c: Char): Boolean = c.isLetterOrDigit() || c in "[]\\`_^{|}-"

    /** Si [text] contiene [nick] como palabra completa, sin distinguir mayúsculas («ana:», «hola @Ana», pero no «banana» ni «ana2»). */
    fun isMention(text: String, nick: String): Boolean {
        val target = NickColors.normalize(nick)
        if (target.isEmpty()) return false
        var from = 0
        while (true) {
            val at = text.indexOf(target, from, ignoreCase = true)
            if (at < 0) return false
            val before = text.getOrNull(at - 1)
            val after = text.getOrNull(at + target.length)
            if ((before == null || !isNickChar(before)) && (after == null || !isNickChar(after))) return true
            from = at + 1
        }
    }
}
