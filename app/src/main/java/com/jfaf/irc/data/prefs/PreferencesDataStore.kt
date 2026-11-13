package com.jfaf.irc.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

/**
 * Archivo de ajustes. En Android vive en `filesDir/datastore/`, la misma ruta que usaba
 * `preferencesDataStoreFile("user_prefs")`, así que los ajustes existentes se conservan.
 */
const val USER_PREFERENCES_FILE = "user_prefs.preferences_pb"

/**
 * DataStore de ajustes a partir de una ruta absoluta que da cada plataforma (debe acabar en
 * `.preferences_pb`). Sólo puede haber uno activo por archivo: la app lo crea como singleton.
 */
fun createPreferencesDataStore(
    path: String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { path.toPath() })
