package com.jfaf.irc.ui.media

/**
 * Pausa entre fotogramas de un GIF. Los navegadores tratan como 100 ms los fotogramas que declaran 10 ms o menos
 * (muchos GIF antiguos ponen 0 o 1 centésima); sin esto irían a toda velocidad.
 */
internal fun gifFrameDelayMs(declaredMs: Int): Long = if (declaredMs <= 10) 100L else declaredMs.toLong()

/**
 * Cuántas pasadas completas hace un GIF según su número de repeticiones (el de Skia: -1 infinito, 0 sin repetir,
 * n = n repeticiones más), o `null` si se repite sin fin.
 */
internal fun gifTotalPlays(repetitionCount: Int): Int? = if (repetitionCount < 0) null else repetitionCount + 1
