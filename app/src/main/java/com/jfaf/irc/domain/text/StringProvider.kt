package com.jfaf.irc.domain.text

/**
 * Resuelve textos localizados a partir de claves. La lógica depende sólo de esta interfaz;
 * cada plataforma la implementa (Android: `R.string`; en KMP: Compose Multiplatform Resources).
 * Los argumentos se sustituyen en los marcadores posicionales `%1$s`, `%2$s`…
 */
interface StringProvider {
    fun get(msg: Msg, vararg args: Any): String
}
