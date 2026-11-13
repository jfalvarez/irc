package com.jfaf.irc.ui.emoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiCatalogTest {

    private val categories = EmojiCatalog.categories

    @Test
    fun haySuficientesCategoriasYEmoticonos() {
        assertTrue(categories.size >= 7, "Categorías: ${categories.size}")
        categories.forEach { assertTrue(it.emojis.size >= 20, "${it.icon} sólo tiene ${it.emojis.size}") }
        assertTrue(categories.sumOf { it.emojis.size } >= 300)
    }

    @Test
    fun ningunEmoticonoSeRepiteDentroDeSuCategoria() {
        categories.forEach { category ->
            val repeated = category.emojis.groupBy { it }.filter { it.value.size > 1 }.keys
            assertTrue(repeated.isEmpty(), "Repetidos en ${category.icon}: $repeated")
        }
    }

    @Test
    fun ningunEmoticonoEstaVacioNiLlevaEspacios() {
        // Los espacios separan los «recientes» al guardarlos: un emoticono con espacios los rompería
        categories.flatMap { it.emojis }.forEach { emoji ->
            assertTrue(emoji.isNotEmpty() && emoji.none { it.isWhitespace() }, "Entrada inválida: '$emoji'")
        }
    }

    @Test
    fun ningunEmoticonoEstaCorrompido() {
        categories.flatMap { it.emojis }.forEach { emoji ->
            assertTrue('\uFFFD' !in emoji, "Carácter de sustitución en '$emoji' (¿codificación del archivo?)")
            // Los pares sustitutos deben venir completos
            var i = 0
            while (i < emoji.length) {
                val c = emoji[i]
                when {
                    c.isHighSurrogate() -> {
                        assertTrue(i + 1 < emoji.length && emoji[i + 1].isLowSurrogate(), "Par sustituto incompleto en '$emoji'")
                        i += 2
                    }
                    c.isLowSurrogate() -> error("Par sustituto suelto en '$emoji'")
                    else -> i++
                }
            }
        }
    }

    @Test
    fun elIconoDeCadaCategoriaEsUnEmoticonoDeVerdad() {
        categories.forEach { category ->
            assertTrue(category.icon.length >= 1 && category.icon.codePointAtCompat(0) > 0x2000, "Icono raro: '${category.icon}'")
        }
        assertEquals(categories.size, categories.map { it.icon }.toSet().size, "Iconos repetidos entre categorías")
    }

    /** Punto de código en [index] (Kotlin común no trae `codePointAt`). */
    private fun String.codePointAtCompat(index: Int): Int {
        val high = this[index]
        return if (high.isHighSurrogate() && index + 1 < length) {
            0x10000 + ((high.code - 0xD800) shl 10) + (this[index + 1].code - 0xDC00)
        } else {
            high.code
        }
    }
}
