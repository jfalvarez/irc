package com.jfaf.irc.log

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import kotlin.concurrent.Volatile

/**
 * Fachada de log multiplataforma sobre Kermit, con la misma firma que android.util.Log
 * (migrar = cambiar el import). Por defecto escribe por consola (tests JVM, escritorio);
 * la app Android instala el writer de Logcat al arrancar con [install].
 */
object Log {
    @Volatile
    private var logger = Logger(loggerConfigInit(CommonWriter()))

    fun install(vararg writers: LogWriter) {
        logger = Logger(loggerConfigInit(*writers))
    }

    fun v(tag: String, msg: String, tr: Throwable? = null) = logger.v(msg, tr, tag)
    fun d(tag: String, msg: String, tr: Throwable? = null) = logger.d(msg, tr, tag)
    fun i(tag: String, msg: String, tr: Throwable? = null) = logger.i(msg, tr, tag)
    fun w(tag: String, msg: String, tr: Throwable? = null) = logger.w(msg, tr, tag)
    fun e(tag: String, msg: String, tr: Throwable? = null) = logger.e(msg, tr, tag)
}
