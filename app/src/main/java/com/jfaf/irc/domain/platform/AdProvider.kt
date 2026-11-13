package com.jfaf.irc.domain.platform

import kotlinx.coroutines.flow.StateFlow

/** Anuncio ya cargado. Es opaco: sólo la UI de cada plataforma sabe pintarlo. */
interface AdHandle

/** Proveedor de anuncios. Android: AdMob nativo; escritorio: ninguno (decisión 0.1). */
interface AdProvider {
    val currentAd: StateFlow<AdHandle?>
    fun load()
}
