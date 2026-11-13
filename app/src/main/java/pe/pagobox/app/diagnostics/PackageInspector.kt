package pe.pagobox.app.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

data class PackageInfoSummary(
    val packageName: String,
    val label: String?,
    val versionName: String?,
    val installer: String?,
    val signingSha256: String?,
)

/** Información verificable de una app instalada (para identificar paquetes oficiales). */
class PackageInspector(private val context: Context) {
    // Se usa desde el hilo del lector y desde el procesador.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, PackageInfoSummary>()

    fun inspect(packageName: String, refresh: Boolean = false): PackageInfoSummary {
        if (!refresh) cache[packageName]?.let { return it }
        val pm = context.packageManager
        val summary = runCatching {
            @Suppress("DEPRECATION")
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            val sha = certs?.firstOrNull()?.toByteArray()?.let { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }
            }
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { pm.getInstallSourceInfo(packageName).installingPackageName }.getOrNull()
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            PackageInfoSummary(
                packageName = packageName,
                label = info.applicationInfo?.loadLabel(pm)?.toString(),
                versionName = info.versionName,
                installer = installer,
                signingSha256 = sha,
            )
        }.getOrElse { PackageInfoSummary(packageName, null, null, null, null) }
        cache[packageName] = summary
        return summary
    }

    fun installerLabel(installer: String?): String = when (installer) {
        "com.android.vending" -> "Google Play"
        "com.huawei.appmarket" -> "AppGallery"
        null -> "Desconocido (posible instalación manual)"
        else -> installer
    }
}
