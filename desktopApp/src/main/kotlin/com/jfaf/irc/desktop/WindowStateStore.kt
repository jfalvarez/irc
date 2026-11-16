package com.jfaf.irc.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File
import java.util.Properties

/** Tamaño, posición y maximizado de la ventana, guardados en `window.properties` dentro de la carpeta de datos. */
class WindowStateStore(dataDir: File) {
    private val file = File(dataDir, "window.properties")

    /** Estado guardado, o el por defecto si no hay fichero, está corrupto o la posición cae fuera de las pantallas. */
    fun load(): WindowState {
        val default = WindowState(size = DEFAULT_SIZE)
        val props = runCatching {
            Properties().apply { file.inputStream().use(::load) }
        }.getOrNull() ?: return default

        val width = props.getProperty("width")?.toFloatOrNull()
        val height = props.getProperty("height")?.toFloatOrNull()
        if (width == null || height == null || width < MIN_DIM || height < MIN_DIM) return default

        val x = props.getProperty("x")?.toFloatOrNull()
        val y = props.getProperty("y")?.toFloatOrNull()
        val position = if (x != null && y != null && isOnScreen(x.toInt(), y.toInt(), width.toInt(), height.toInt())) {
            WindowPosition(x.dp, y.dp)
        } else {
            WindowPosition.PlatformDefault
        }
        val maximized = props.getProperty("maximized").toBoolean()

        return WindowState(
            placement = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = position,
            size = DpSize(width.dp, height.dp)
        )
    }

    fun save(state: WindowState) {
        val props = Properties()
        val maximized = state.placement == WindowPlacement.Maximized
        props.setProperty("maximized", maximized.toString())
        // Maximizada, size/position son los de la ventana maximizada; se conservan los últimos "normales" ya guardados.
        if (!maximized) {
            props.setProperty("width", state.size.width.value.toString())
            props.setProperty("height", state.size.height.value.toString())
            val pos = state.position
            if (pos.isSpecified) {
                props.setProperty("x", pos.x.value.toString())
                props.setProperty("y", pos.y.value.toString())
            }
        } else {
            runCatching { Properties().apply { file.inputStream().use(::load) } }.getOrNull()?.let { old ->
                listOf("width", "height", "x", "y").forEach { k ->
                    old.getProperty(k)?.let { props.setProperty(k, it) }
                }
            }
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, null) }
        }
    }

    // Evita abrir la ventana fuera de pantalla si se quitó un monitor o cambió la resolución.
    private fun isOnScreen(x: Int, y: Int, w: Int, h: Int): Boolean {
        val bounds = Rectangle(x, y, w, h)
        return GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any {
            it.defaultConfiguration.bounds.intersection(bounds).let { r -> r.width >= MIN_VISIBLE && r.height >= MIN_VISIBLE }
        }
    }

    private companion object {
        val DEFAULT_SIZE = DpSize(1000.dp, 700.dp)
        const val MIN_DIM = 300f
        const val MIN_VISIBLE = 100
    }
}
