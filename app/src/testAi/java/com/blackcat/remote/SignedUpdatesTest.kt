package com.blackcat.remote

import com.android.apksig.ApkVerifier
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.Signature
import java.security.cert.Certificate
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

/** Authorities here exist only in test memory. They are never packaged as production trust. */
class SignedUpdatesTest {
    companion object {
        private val authority = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        private const val SIGNER = "fc5c0b42b9da40c2507d8f2e77ed92db9e023740aa1a44d1a3b2c14a6e9f4764"
        private const val NOW = 1800000000L
        private val host = setOf("updates.example.test")
        private val apkHost = setOf("apk.example.test")
        private val trust = UpdateTrust("https://updates.example.test/v1/ai.json", host, apkHost,
            mapOf("unit-test-only" to authority.public), "testing")
        private val installed = InstalledUpdateIdentity("com.blackcat.remote", 22, SIGNER)
    }
    private fun payload(vararg changes: Pair<String, Any>): ByteArray {
        val values = linkedMapOf<String, Any>("schema" to 1, "key_id" to "unit-test-only", "package" to installed.packageName,
            "flavor" to "ai", "channel" to "testing", "sequence" to 10, "issued" to NOW - 60, "expires" to NOW + 3600,
            "version_code" to 23, "version_name" to "20.0-test", "apk_size" to 4,
            "apk_sha256" to SignedUpdateMetadata.sha256(byteArrayOf(1, 2, 3, 4)), "signer_sha256" to SIGNER,
            "apk_url" to "https://apk.example.test/releases/app.apk")
        changes.forEach { values[it.first] = it.second }
        return values.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":" + if (v is String) "\"$v\"" else "$v" }.toByteArray()
    }
    private fun envelope(bytes: ByteArray = payload()): ByteArray {
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(authority.private); update("BlackCat signed update v1\u0000".toByteArray()); update(bytes); sign()
        }
        val b64 = Base64.getEncoder()
        return "{\"payload\":\"${b64.encodeToString(bytes)}\",\"signature\":\"${b64.encodeToString(signature)}\"}".toByteArray()
    }
    private fun release(vararg changes: Pair<String, Any>) = SignedUpdateMetadata.authenticate(envelope(payload(*changes)), trust)
    private fun reject(action: () -> Unit) {
        try { action(); fail("Expected a closed failure") } catch (e: Exception) { /* No successful result is returned. */ }
    }
    private class Ledger : UpdateLedger {
        var floor = UpdateFloor()
        override fun accept(release: UpdateRelease, installed: InstalledUpdateIdentity, now: Long) {
            floor = SignedUpdateMetadata.nextFloor(release, installed, floor, now)
        }
    }
    private class Response(url: URL, private val status: Int, bytes: ByteArray = byteArrayOf(),
        private val headers: Map<String, String> = emptyMap()) : HttpsURLConnection(url) {
        val content = ByteArrayInputStream(bytes)
        var disconnected = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String) = headers[name]
        override fun getInputStream() = content
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TLS_TEST_ONLY"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getPeerPrincipal(): Principal? = null
        override fun getLocalPrincipal(): Principal? = null
    }
    private fun https(status: Int = 200, bytes: ByteArray = byteArrayOf(1), headers: Map<String, String> = emptyMap()) =
        UpdateHttps { Response(it, status, bytes, headers) }

    @Test fun productionTrustMatchesOwnerApprovedPublicAuthority() {
        val config = org.json.JSONObject(File("../updates/production-trust-v1.json").readText())
        val actual = ProductionUpdateTrust.configured()!!
        assertEquals(config.getString("metadata_url"), actual.metadataUrl)
        assertEquals(setOf("raw.githubusercontent.com"), actual.metadataHosts)
        assertEquals(actual.metadataHosts, actual.apkHosts)
        assertEquals(setOf(config.getString("key_id")), actual.keys.keys)
        assertEquals(config.getString("public_key_sha256"), SignedUpdateMetadata.sha256(actual.keys.values.single().encoded))
        assertEquals("testing", actual.channel)
        reject { SignedUpdateMetadata.authenticate(envelope(), actual) }
    }
    @Test fun authenticMetadataPassesAndBindsAllReleaseFields() {
        val r = release()
        assertEquals(23L, r.version); assertEquals(SIGNER, r.signer)
        assertEquals(10L, SignedUpdateMetadata.nextFloor(r, installed, UpdateFloor(), NOW).sequence)
    }
    @Test fun realPublishedPythonEnvelopeAuthenticatesWithProductionKotlinVerifier() {
        val bytes = javaClass.getResourceAsStream("/published-release-v1.json")!!.use { it.readBytes() }
        val r = SignedUpdateMetadata.authenticate(bytes, ProductionUpdateTrust.configured()!!)
        assertEquals(23L, r.version)
        assertEquals("20.0-signed-updates", r.versionName)
        assertEquals("2f812311109f65bf324abf472a8f9d4782b4f42814d270e043f875c300a5beeb", r.hash)
        assertEquals(23L, SignedUpdateMetadata.nextFloor(r, installed, UpdateFloor(), r.issued + 1).version)
        reject { SignedUpdateMetadata.nextFloor(r, installed, UpdateFloor(), r.expires) }
    }
    @Test fun unsignedOrModifiedMetadataCannotAdvanceFloor() {
        val signed = envelope().toString(Charsets.US_ASCII)
        val b64 = Base64.getEncoder().encodeToString(payload("version_code" to 24))
        reject { SignedUpdateMetadata.authenticate(signed.replace(Base64.getEncoder().encodeToString(payload()), b64).toByteArray(), trust) }
        reject { SignedUpdateMetadata.authenticate(payload(), trust) }
    }
    @Test fun unknownAuthorityAndWrongSignatureAreRejected() {
        reject { release("key_id" to "unknown") }
        val e = envelope().toString(Charsets.US_ASCII).replace("\"signature\":\"", "\"signature\":\"AAAA")
        reject { SignedUpdateMetadata.authenticate(e.toByteArray(), trust) }
    }
    @Test fun signatureFromAnotherAuthorityIsRejected() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val otherTrust = UpdateTrust(trust.metadataUrl, host, apkHost, mapOf("unit-test-only" to other.public), "testing")
        reject { SignedUpdateMetadata.authenticate(envelope(), otherTrust) }
    }
    @Test fun duplicateUnknownNestedAndCoercedFieldsAreRejectedEvenWhenSigned() {
        val text = payload().toString(Charsets.US_ASCII)
        for (bad in listOf(text.replace("\"schema\":1", "\"schema\":1,\"schema\":1"),
            text.replace("\"schema\":1", "\"schema\":1,\"unknown\":1"),
            text.replace("\"schema\":1", "\"schema\":\"1\""),
            text.replace("\"schema\":1", "\"schema\":{\"value\":1}"),
            text.replace("\"schema\":1", "\"schema\":1.0"), text.replace("\"schema\":1", "\"schema\":01"))) {
            reject { SignedUpdateMetadata.authenticate(envelope(bad.toByteArray()), trust) }
        }
    }
    @Test fun oversizeEnvelopeAndNoncanonicalBase64AreRejected() {
        reject { SignedUpdateMetadata.authenticate(ByteArray(16385) { 32 }, trust) }
        val e = envelope().toString(Charsets.US_ASCII).replace("\"payload\":\"", "\"payload\":\" ")
        reject { SignedUpdateMetadata.authenticate(e.toByteArray(), trust) }
    }
    @Test fun wrongFlavorChannelSchemaAndInvalidNamesAreRejected() {
        for (change in listOf("flavor" to "offline", "channel" to "stable", "schema" to 2, "version_name" to "bad name")) reject { release(change) }
    }
    @Test fun releaseSizesAndLifetimesAreBounded() {
        for (change in listOf("apk_size" to 0, "apk_size" to SignedUpdateMetadata.MAX_APK + 1,
            "sequence" to 0, "version_code" to 2100000001L, "expires" to NOW - 61,
            "expires" to NOW + SignedUpdateMetadata.MAX_LIFETIME + 60)) reject { release(change) }
    }
    @Test fun apkAndSignerHashFormatsAreStrict() {
        reject { release("apk_sha256" to "x".repeat(64)) }
        reject { release("signer_sha256" to SIGNER.uppercase()) }
    }
    @Test fun expiredFutureAndRolledBackClocksFail() {
        val r = release()
        reject { SignedUpdateMetadata.nextFloor(r, installed, UpdateFloor(), r.expires) }
        reject { SignedUpdateMetadata.nextFloor(r, installed, UpdateFloor(), r.issued - 301) }
        reject { SignedUpdateMetadata.nextFloor(r, installed, UpdateFloor(wallTime = NOW + 301), NOW) }
    }
    @Test fun packageAndExistingSigningIdentityMustMatch() {
        reject { SignedUpdateMetadata.nextFloor(release("package" to "other.app"), installed, UpdateFloor(), NOW) }
        reject { SignedUpdateMetadata.nextFloor(release("signer_sha256" to "a".repeat(64)), installed, UpdateFloor(), NOW) }
    }
    @Test fun installedAndRememberedVersionsBothPreventRollback() {
        reject { SignedUpdateMetadata.nextFloor(release("version_code" to 21), installed, UpdateFloor(), NOW) }
        reject { SignedUpdateMetadata.nextFloor(release(), installed, UpdateFloor(version = 24), NOW) }
        reject { SignedUpdateMetadata.nextFloor(release(), installed, UpdateFloor(sequence = 11), NOW) }
    }
    @Test fun replayOfSameMetadataIsRetryableButEquivocationIsNot() {
        val r = release()
        val floor = SignedUpdateMetadata.nextFloor(r, installed, UpdateFloor(), NOW)
        assertEquals(floor, SignedUpdateMetadata.nextFloor(r, installed, floor, NOW))
        reject { SignedUpdateMetadata.nextFloor(release("version_name" to "changed"), installed, floor, NOW) }
        assertEquals(11L, SignedUpdateMetadata.nextFloor(release("sequence" to 11), installed, floor, NOW).sequence)
    }
    @Test fun malformedAndDisallowedUrlsNeverConnect() {
        val bad = listOf("http://apk.example.test/a", "https://evil.test/a", "https://apk.example.test.evil/a",
            "https://user@apk.example.test/a", "https://apk.example.test:444/a", "https://apk.example.test/a#x",
            "https://apk.example.test/a?token=x", "https://apk.example.test/%2e%2e/a", "https://apk.example.test/../a",
            "https://apk.example.test//a", "https://APK.EXAMPLE.TEST/a", "https://apk.example.test/a\\b")
        for (url in bad) {
            var connected = false
            val t = UpdateHttps { connected = true; error("Must not connect") }
            reject { t.fetch(url, apkHost, 10, ByteArrayOutputStream()) }
            assertFalse(connected)
        }
    }
    @Test fun onlyExplicitRedirectHostsAreAllowedWithoutCredentials() {
        val connected = mutableListOf<Response>()
        val t = UpdateHttps { url ->
            Response(url, if (url.path == "/a") 302 else 200, byteArrayOf(1, 2),
                if (url.path == "/a") mapOf("Location" to "/b") else emptyMap()).also { connected.add(it) }
        }
        assertEquals(2L, t.fetch("https://apk.example.test/a", apkHost, 2, ByteArrayOutputStream()))
        assertEquals(2, connected.size)
        assertTrue(connected.all { it.disconnected && !it.instanceFollowRedirects && !it.useCaches })
        assertNull(connected.first().getRequestProperty("Authorization"))
        assertEquals("identity", connected.first().getRequestProperty("Accept-Encoding"))
    }
    @Test fun downgradeCrossHostLoopsAndTooManyRedirectsAreBlocked() {
        for (target in listOf("http://apk.example.test/a", "https://evil.test/a", "/a", "//evil.test/a")) {
            reject { https(302, headers = mapOf("Location" to target)).fetch("https://apk.example.test/a", apkHost, 20, ByteArrayOutputStream()) }
        }
        var count = 0
        val t = UpdateHttps { url -> count++; Response(url, 302, headers = mapOf("Location" to "/a$count")) }
        reject { t.fetch("https://apk.example.test/start", apkHost, 20, ByteArrayOutputStream()) }
        assertEquals(3, count)
    }
    @Test fun partialResponsesEncodingErrorsAndWrongLengthsAreRejected() {
        for (status in listOf(206, 304, 401, 404, 500)) reject {
            https(status).fetch("https://apk.example.test/a", apkHost, 10, ByteArrayOutputStream())
        }
        for (headers in listOf(mapOf("Content-Length" to "2"), mapOf("Content-Length" to "999"),
            mapOf("Content-Length" to "NaN"), mapOf("Content-Encoding" to "gzip"))) reject {
            https(headers = headers).fetch("https://apk.example.test/a", apkHost, 10, ByteArrayOutputStream())
        }
    }
    @Test fun chunkedBodyIsBoundedAndCancelledTransportIsNotReusable() {
        reject { https(bytes = ByteArray(11)).fetch("https://apk.example.test/a", apkHost, 10, ByteArrayOutputStream()) }
        val t = https(); t.cancel()
        reject { t.fetch("https://apk.example.test/a", apkHost, 10, ByteArrayOutputStream()) }
    }
    private fun verifier(r: UpdateRelease = release(), snapshot: UpdateApkIdentity = snapshot(r),
        bytes: ByteArray = byteArrayOf(1, 2, 3, 4), ledger: UpdateLedger = Ledger()) = UpdateVerifier(trust, installed,
        setOf("INTERNET"), ledger, UpdateApkInspector { snapshot }, https(bytes = bytes), { NOW })
    private fun snapshot(r: UpdateRelease) = UpdateApkIdentity(r.packageName, r.version, r.versionName, setOf(r.signer), setOf("INTERNET"))
    private fun withFile(action: (File) -> Unit) {
        val dir = java.nio.file.Files.createTempDirectory("test-only-updates-").toFile()
        try { action(File(dir, "checked.apk")) } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun exactBytesAndIdentityAreRequiredBeforeAFileIsOffered() = withFile { file ->
        val r = release(); verifier().download(r, file)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), file.readBytes())
    }
    @Test fun wrongSizeHashOrManifestDeletesStagingAndFailsClosed() {
        val r = release()
        val snapshots = listOf(snapshot(r).copy(packageName = "other.app"), snapshot(r).copy(version = 22),
            snapshot(r).copy(versionName = "other"), snapshot(r).copy(signers = setOf("a".repeat(64))),
            snapshot(r).copy(signers = setOf(SIGNER, "b".repeat(64))), snapshot(r).copy(permissions = emptySet()),
            snapshot(r).copy(permissions = setOf("INTERNET", "REQUEST_INSTALL_PACKAGES")))
        for (s in snapshots) withFile { file -> reject { verifier(snapshot = s).download(r, file) }; assertFalse(file.exists()) }
        for (bytes in listOf(byteArrayOf(1), ByteArray(5), byteArrayOf(4, 3, 2, 1))) withFile { file ->
            reject { verifier(bytes = bytes).download(r, file) }; assertFalse(file.exists())
        }
    }
    @Test fun durableStateFailurePreventsAnyDownload() = withFile { file ->
        val fail = object : UpdateLedger { override fun accept(release: UpdateRelease, installed: InstalledUpdateIdentity, now: Long) { error("I/O failed") } }
        reject { verifier(ledger = fail).download(release(), file) }; assertFalse(file.exists())
    }
    @Test fun alteredFileIsRecheckedBeforeExport() = withFile { file ->
        val r = release(); val v = verifier(); v.download(r, file)
        file.writeBytes(byteArrayOf(4, 3, 2, 1)); reject { v.verifyFile(r, file) }
    }
    @Test fun authenticButOlderMetadataNeverPoisonsHighWaterState() {
        val ledger = Ledger(); ledger.accept(release(), installed, NOW)
        val before = ledger.floor
        reject { ledger.accept(release("sequence" to 9), installed, NOW) }
        assertEquals(before, ledger.floor)
    }
    @Test fun checkAuthenticatesBeforePersistingAndUsesMetadataHostPolicy() {
        val ledger = Ledger()
        val urls = mutableListOf<String>()
        val transport = UpdateHttps { url -> urls.add(url.toString()); Response(url, 200, envelope()) }
        val v = UpdateVerifier(trust, installed, setOf("INTERNET"), ledger, UpdateApkInspector { error("No APK during check") }, transport, { NOW })
        assertEquals(23L, v.check().version)
        assertEquals(listOf(trust.metadataUrl), urls)
        assertEquals(10L, ledger.floor.sequence)
        val old = ledger.floor
        val invalid = UpdateVerifier(trust, installed, emptySet(), ledger,
            UpdateApkInspector { error("No APK") }, https(bytes = payload()), { NOW })
        reject { invalid.check() }; assertEquals(old, ledger.floor)
    }
    @Test fun inspectionFailureAndCancellationDeleteUnverifiedDownloads() = withFile { file ->
        val v = UpdateVerifier(trust, installed, setOf("INTERNET"), Ledger(),
            UpdateApkInspector { error("Invalid APK signature") }, https(bytes = byteArrayOf(1, 2, 3, 4)), { NOW })
        reject { v.download(release(), file) }; assertFalse(file.exists())
        val cancelled = verifier(); cancelled.cancel()
        reject { cancelled.download(release(), file) }; assertFalse(file.exists())
    }
    @Test fun existingStagingFileCannotBeOverwritten() = withFile { file ->
        file.writeText("existing")
        reject { verifier().download(release(), file) }
        assertEquals("existing", file.readText())
    }
    @Test fun installedVersionCanBeReportedButNeverDownloadedAgain() = withFile { file ->
        val r = release("version_code" to 22)
        SignedUpdateMetadata.nextFloor(r, installed, UpdateFloor(), NOW)
        reject { verifier(r).download(r, file) }; assertFalse(file.exists())
    }
    @Test fun actualCat19ApksHaveOriginalCryptographicallyVerifiedSigner() {
        for (name in listOf("CatAI-19.apk", "CatBT-19.apk")) {
            val file = File("../docs/ui19/install/$name")
            assertTrue("Real retained APK fixture missing", file.isFile)
            val result = ApkVerifier.Builder(file).setMinCheckedPlatformVersion(28).setMaxCheckedPlatformVersion(35).build().verify()
            assertTrue(result.isVerified)
            assertTrue(result.isVerifiedUsingV2Scheme || result.isVerifiedUsingV3Scheme)
            assertEquals(setOf(SIGNER), result.signerCertificates.map { SignedUpdateMetadata.sha256(it.encoded) }.toSet())
        }
    }
    @Test fun alteredRealApkFailsCryptographicVerificationEvenWithOriginalCertificateBlock() = withFile { file ->
        val bytes = File("../docs/ui19/install/CatAI-19.apk").readBytes()
        bytes[1000] = (bytes[1000].toInt() xor 1).toByte(); file.writeBytes(bytes)
        val result = ApkVerifier.Builder(file).setMinCheckedPlatformVersion(28).setMaxCheckedPlatformVersion(35).build().verify()
        assertFalse(result.isVerified)
    }
}
