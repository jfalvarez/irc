package com.jfaf.irc.config

import android.content.Context
import android.content.pm.ApplicationInfo // Importación añadida
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
// No se usa BuildConfig ya que no se resuelve
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "RemoteConfigManager"
        private const val MIN_APP_VERSION_CODE_KEY = "min_app_version_code"
    }

    private val _isUpdateRequired = MutableStateFlow(false)
    val isUpdateRequired: StateFlow<Boolean> = _isUpdateRequired

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        Firebase.remoteConfig 
    }

    init {
        initializeConfig()
    }

    private fun initializeConfig() {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val fetchIntervalSeconds = if (isDebuggable) {
            Log.d(TAG, "Modo DEBUG (detectado por ApplicationInfo): Estableciendo intervalo de obtención de Remote Config a 0 segundos.")
            0L
        } else {
            Log.d(TAG, "Modo RELEASE (detectado por ApplicationInfo): Estableciendo intervalo de obtención de Remote Config a 3600 segundos.")
            3600L
        }

        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(fetchIntervalSeconds)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        val defaults = mapOf<String, Any>(
            MIN_APP_VERSION_CODE_KEY to 1L // Default min version
        )
        remoteConfig.setDefaultsAsync(defaults)
    }

    fun fetchAndActivateConfig() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Remote Config params fetched & activated (are they new? $updated)")
                } else {
                    Log.w(TAG, "Remote Config fetch failed")
                }
                performVersionCheck()
            }
    }

    private fun performVersionCheck() {
        val currentVersion = getCurrentAppVersionCode(context)
        val minRequiredVersion = remoteConfig.getLong(MIN_APP_VERSION_CODE_KEY)

        Log.i(TAG, "Current app version: $currentVersion, Min required version from Remote Config: $minRequiredVersion")

        if (currentVersion != -1L && currentVersion < minRequiredVersion) {
            _isUpdateRequired.value = true
        } else {
            _isUpdateRequired.value = false
        }
    }

    private fun getCurrentAppVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Could not get package info", e)
            -1L // Indicate error
        }
    }
}
