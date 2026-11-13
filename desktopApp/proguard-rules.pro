# Reglas de ProGuard de la build release de escritorio (se amplían según lo que falle al arrancar el empaquetado).

# Punto de entrada
-keep class com.jfaf.irc.desktop.MainKt { public static void main(java.lang.String[]); }

# Koin resuelve por reflexión de tipos de Kotlin; Room instancia la base generada por KSP por reflexión
-keep class com.jfaf.irc.** { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Kotlin, corrutinas y serialización de metadatos
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
-dontwarn kotlinx.coroutines.**

# Compose Multiplatform (recursos) y Skiko
-keep class org.jetbrains.compose.resources.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }
-dontwarn org.jetbrains.skiko.**

# SQLite empaquetado (JNI)
-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**

# Coil y OkHttp (red de las imágenes)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Otras clases del classpath que sólo se usan en Android o en pruebas
-dontwarn android.**
-dontwarn androidx.compose.ui.test.**
-dontwarn org.junit.**

# Kotlin 2.4: los tipos atómicos de la stdlib son alias de java.util.concurrent.atomic y ProGuard no los resuelve
-dontwarn kotlin.concurrent.atomics.**
