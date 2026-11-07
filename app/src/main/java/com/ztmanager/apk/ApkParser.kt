package com.ztmanager.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.ztmanager.fs.Format
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

data class CertInfo(
    val subject: String,
    val issuer: String,
    val serial: String,
    val validFrom: Long,
    val validTo: Long,
    val algorithm: String,
    val md5: String,
    val sha1: String,
    val sha256: String
)

data class ApkInfo(
    val label: String,
    val icon: Drawable?,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val size: Long,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val abis: List<String>,
    val dexCount: Int,
    val certs: List<CertInfo>,
    val installedVersion: String?,
    val splitApk: Boolean
)

object ApkParser {

    fun parse(context: Context, path: String): ApkInfo {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or signatureFlag()
        val info: PackageInfo = pm.getPackageArchiveInfo(path, flags)
            ?: pm.getPackageArchiveInfo(path, 0)
            ?: throw IllegalArgumentException("Cannot parse APK")
        val ai = info.applicationInfo
        ai?.sourceDir = path
        ai?.publicSourceDir = path
        val label = ai?.loadLabel(pm)?.toString() ?: info.packageName
        val icon = try { ai?.loadIcon(pm) } catch (e: Exception) { null }
        val versionCode = versionCodeOf(info)

        val abis = LinkedHashSet<String>()
        var dex = 0
        try {
            ZipFile(path).use { z ->
                val en = z.entries()
                while (en.hasMoreElements()) {
                    val name = en.nextElement().name
                    if (name.startsWith("lib/")) name.split('/').getOrNull(1)?.let { if (it.isNotEmpty()) abis.add(it) }
                    if (name.matches(Regex("classes\\d*\\.dex"))) dex++
                }
            }
        } catch (_: Exception) {
        }

        val rawCerts: List<ByteArray> = try {
            if (Build.VERSION.SDK_INT >= 28) {
                val si = info.signingInfo
                val sigs = if (si == null) null else if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
                sigs?.map { it.toByteArray() } ?: emptyList()
            } else {
                legacySignatures(info)
            }
        } catch (e: Exception) {
            emptyList()
        }
        val certs = rawCerts.mapNotNull { parseCert(it) }

        val installed = try {
            pm.getPackageInfo(info.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }

        return ApkInfo(
            label = label,
            icon = icon,
            packageName = info.packageName,
            versionName = info.versionName ?: "",
            versionCode = versionCode,
            minSdk = if (Build.VERSION.SDK_INT >= 24) ai?.minSdkVersion ?: 0 else 0,
            targetSdk = ai?.targetSdkVersion ?: 0,
            size = File(path).length(),
            permissions = info.requestedPermissions?.toList() ?: emptyList(),
            activities = info.activities?.map { it.name } ?: emptyList(),
            services = info.services?.map { it.name } ?: emptyList(),
            receivers = info.receivers?.map { it.name } ?: emptyList(),
            providers = info.providers?.map { it.name } ?: emptyList(),
            abis = abis.toList(),
            dexCount = dex,
            certs = certs,
            installedVersion = installed,
            splitApk = info.splitNames?.isNotEmpty() == true
        )
    }

    @Suppress("DEPRECATION")
    private fun signatureFlag(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun parseCert(bytes: ByteArray): CertInfo? = try {
        val cf = CertificateFactory.getInstance("X.509")
        val c = cf.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        CertInfo(
            subject = c.subjectDN.name,
            issuer = c.issuerDN.name,
            serial = c.serialNumber.toString(16),
            validFrom = c.notBefore.time,
            validTo = c.notAfter.time,
            algorithm = c.sigAlgName,
            md5 = digest("MD5", bytes),
            sha1 = digest("SHA-1", bytes),
            sha256 = digest("SHA-256", bytes)
        )
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun legacySignatures(info: PackageInfo): List<ByteArray> =
        info.signatures?.map { it.toByteArray() } ?: emptyList()

    private fun digest(algo: String, b: ByteArray): String =
        Format.hex(MessageDigest.getInstance(algo).digest(b)).chunked(2).joinToString(":").uppercase()

    fun readManifest(path: String): String {
        ZipFile(path).use { z ->
            val e = z.getEntry("AndroidManifest.xml") ?: throw IllegalArgumentException("AndroidManifest.xml not found")
            val bytes = z.getInputStream(e).use { it.readBytes() }
            return AxmlDecoder.decode(bytes)
        }
    }

    fun sdkName(api: Int): String {
        val name = when (api) {
            21 -> "5.0"; 22 -> "5.1"; 23 -> "6.0"; 24 -> "7.0"; 25 -> "7.1"; 26 -> "8.0"; 27 -> "8.1"
            28 -> "9"; 29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"
            else -> null
        }
        return if (name != null) "$api (Android $name)" else api.toString()
    }
}
