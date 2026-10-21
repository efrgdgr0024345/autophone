package com.blackcat.remote

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import com.android.apksig.ApkVerifier
import java.io.File

/** APK signature verification is cryptographic; PackageManager is used only for manifest identity. */
internal object AndroidUpdateSecurity {
    fun packageInfo(context: Context): PackageInfo = context.packageManager.getPackageInfo(context.packageName,
        PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS)

    fun installed(context: Context): InstalledUpdateIdentity {
        val info = packageInfo(context)
        return InstalledUpdateIdentity(info.packageName, info.longVersionCode, signers(info).single())
    }

    private fun signers(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: error("APK signing identity unavailable")
        check(!signing.hasMultipleSigners()) { "Multiple APK signers are unsupported" }
        val current = signing.apkContentsSigners
        check(current.size == 1)
        return current.map { SignedUpdateMetadata.sha256(it.toByteArray()) }.toSet()
    }

    fun inspect(context: Context, file: File): UpdateApkIdentity {
        val result = ApkVerifier.Builder(file).setMinCheckedPlatformVersion(28)
            .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT).build().verify()
        check(result.isVerified && (result.isVerifiedUsingV2Scheme || result.isVerifiedUsingV3Scheme) &&
            result.signerCertificates.size == 1) { "APK cryptographic signature rejected" }
        val cryptoSigners = result.signerCertificates.map { SignedUpdateMetadata.sha256(it.encoded) }.toSet()
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS) ?: error("APK manifest rejected")
        check(signers(info) == cryptoSigners) { "APK signature parsers disagree" }
        return UpdateApkIdentity(info.packageName, info.longVersionCode, info.versionName ?: "",
            cryptoSigners, info.requestedPermissions.orEmpty().toSet())
    }
}

/** Private, non-backed-up high-water state. Corruption and failed durable writes block updates. */
internal class AndroidUpdateLedger(context: Context) : UpdateLedger {
    private val directory = File(context.noBackupFilesDir, "signed-updates")
    private val file = File(directory, "floor-v1")
    override fun accept(release: UpdateRelease, installed: InstalledUpdateIdentity, now: Long) = synchronized(lock) {
        check(directory.isDirectory || directory.mkdirs())
        val atomic = AtomicFile(file)
        val floor = if (file.exists() || File(file.path + ".bak").exists()) {
            val parts = atomic.openRead().use { stream ->
                val bytes = stream.readBytesBounded(256)
                bytes.toString(Charsets.US_ASCII).split('\n')
            }
            check(parts.size == 5 && parts[0] == "BCF1" && parts[1].matches(Regex("[1-9][0-9]{0,17}")) &&
                parts[2].matches(Regex("[1-9][0-9]{0,17}")) && parts[3].matches(Regex("[0-9a-f]{64}")) &&
                parts[4].matches(Regex("[1-9][0-9]{0,17}"))) { "Update rollback state is corrupt" }
            UpdateFloor(parts[1].toLong(), parts[2].toLong(), parts[3], parts[4].toLong())
        } else {
            check(!File(file.path + ".new").exists()) { "Incomplete update rollback state" }
            UpdateFloor()
        }
        val next = SignedUpdateMetadata.nextFloor(release, installed, floor, now)
        val output = atomic.startWrite()
        try {
            output.write("BCF1\n${next.sequence}\n${next.version}\n${next.payloadHash}\n${next.wallTime}".toByteArray(Charsets.US_ASCII))
            output.fd.sync()
            atomic.finishWrite(output)
            // AtomicFile may report an I/O failure via logging; verify the persisted result too.
            check(atomic.openRead().use { it.readBytesBounded(256) }.contentEquals(
                "BCF1\n${next.sequence}\n${next.version}\n${next.payloadHash}\n${next.wallTime}".toByteArray(Charsets.US_ASCII)))
        } catch (e: Exception) { atomic.failWrite(output); throw e }
    }
    companion object { private val lock = Any() }
}

private fun java.io.InputStream.readBytesBounded(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    repeat(max + 1) {
        val n = read()
        if (n == -1) return out.toByteArray()
        check(out.size() < max) { "Oversized update rollback state" }
        out.write(n)
    }
    error("Oversized update rollback state")
}
