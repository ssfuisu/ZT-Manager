package com.ztmanager.apk

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.ztmanager.fs.FileOps
import com.ztmanager.fs.Format
import com.ztmanager.task.TaskContext
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * Signs APKs with a key that is generated on this device and kept in the Android
 * Keystore, so the private key never leaves the secure hardware or the app.
 */
object ApkSigning {
    private const val ALIAS = "zt_apk_signer"
    private const val STORE = "AndroidKeyStore"

    private fun keyStore(): KeyStore = KeyStore.getInstance(STORE).apply { load(null) }

    fun hasKey(): Boolean = try {
        keyStore().containsAlias(ALIAS)
    } catch (e: Exception) {
        false
    }

    private fun ensureKey(): Pair<PrivateKey, X509Certificate> {
        val ks = keyStore()
        if (!ks.containsAlias(ALIAS)) {
            val now = System.currentTimeMillis()
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=ZT Manager, OU=APK Signer, O=ZT Manager"))
                .setCertificateSerialNumber(BigInteger.valueOf(now))
                .setCertificateNotBefore(Date(now - 24L * 3600 * 1000))
                .setCertificateNotAfter(Date(now + 30L * 365 * 24 * 3600 * 1000))
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, STORE).apply { initialize(spec) }.generateKeyPair()
        }
        val key = ks.getKey(ALIAS, null) as? PrivateKey ?: throw IOException("Signing key not available")
        val cert = ks.getCertificate(ALIAS) as? X509Certificate ?: throw IOException("Signing certificate not available")
        return key to cert
    }

    fun certificateSha256(): String? = try {
        (keyStore().getCertificate(ALIAS) as? X509Certificate)?.let { digest(it.encoded) }
    } catch (e: Exception) {
        null
    }

    fun deleteKey() {
        try {
            keyStore().deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }

    private fun digest(b: ByteArray) =
        Format.hex(MessageDigest.getInstance("SHA-256").digest(b)).chunked(2).joinToString(":").uppercase()

    fun sign(tc: TaskContext, input: File): File {
        tc.setMessage(input.name)
        val (key, cert) = ensureKey()
        val base = input.name.removeSuffix(".apk").removeSuffix(".APK")
        val out = FileOps.uniqueFile(input.parentFile!!, "${base}_signed.apk")
        val tmp = File(out.parentFile, out.name + ".part")
        try {
            val signer = ApkSigner.SignerConfig.Builder("CERT", key, listOf(cert)).build()
            ApkSigner.Builder(listOf(signer))
                .setInputApk(input)
                .setOutputApk(tmp)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setCreatedBy("ZT Manager")
                .build()
                .sign()
            if (!tmp.renameTo(out)) throw IOException("Cannot write ${out.name}")
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
        return out
    }

    fun verifyReport(file: File): String {
        val r = ApkVerifier.Builder(file).build().verify()
        val sb = StringBuilder()
        sb.append(if (r.isVerified) "VERIFIED" else "NOT VERIFIED").append("\n\n")
        sb.append("v1 (JAR): ").append(if (r.isVerifiedUsingV1Scheme) "yes" else "no").append('\n')
        sb.append("v2: ").append(if (r.isVerifiedUsingV2Scheme) "yes" else "no").append('\n')
        sb.append("v3: ").append(if (r.isVerifiedUsingV3Scheme) "yes" else "no").append('\n')
        sb.append("v3.1: ").append(if (r.isVerifiedUsingV31Scheme) "yes" else "no").append('\n')
        sb.append("v4: ").append(if (r.isVerifiedUsingV4Scheme) "yes" else "no").append('\n')
        r.signerCertificates.forEachIndexed { i, c ->
            sb.append("\nSigner ").append(i + 1).append('\n')
            sb.append(c.subjectX500Principal.name).append('\n')
            sb.append("SHA-256: ").append(digest(c.encoded)).append('\n')
            sb.append("Valid until: ").append(Format.date(c.notAfter.time)).append('\n')
        }
        if (r.errors.isNotEmpty() || r.allErrors.isNotEmpty()) {
            sb.append("\nErrors:\n")
            (r.errors + r.allErrors).distinctBy { it.toString() }.take(20).forEach { sb.append("- ").append(it.toString()).append('\n') }
        }
        if (r.warnings.isNotEmpty()) {
            sb.append("\nWarnings:\n")
            r.warnings.take(20).forEach { sb.append("- ").append(it.toString()).append('\n') }
        }
        return sb.toString().trimEnd()
    }
}
