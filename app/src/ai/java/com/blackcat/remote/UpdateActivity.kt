package com.blackcat.remote

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Foreground-only checking; SAF saves checked bytes. This activity never starts an installer. */
class UpdateActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var verifier: UpdateVerifier? = null
    private var release: UpdateRelease? = null
    private var generation = 0L
    private var choosingFile = false
    private lateinit var staging: File
    private lateinit var status: TextView
    private lateinit var checkButton: Button
    private lateinit var downloadButton: Button
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val stagingDirectory = File(noBackupFilesDir, "update-staging")
        check(stagingDirectory.isDirectory || stagingDirectory.mkdirs())
        // Only owned staging APKs; no key vault or rollback-state files are touched.
        stagingDirectory.listFiles()?.filter { it.isFile && it.name.matches(Regex("checked-[a-f0-9-]{36}\\.apk")) }
            ?.forEach { it.delete() }
        staging = newStaging()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        BlackCatStyle.applySystemBarInsets(root)
        root.addView(ReferencePanelStyle.header(this, "App updates") { finish() })
        val body = ReferencePanelStyle.card(this)
        val current = AndroidUpdateSecurity.packageInfo(this)
        body.addView(ReferencePanelStyle.label(this, "Installed ${current.versionName} (${current.longVersionCode})", 17f, Color.WHITE, true))
        status = ReferencePanelStyle.label(this,
            "Checking is optional. Nothing downloads when this screen opens. An available APK must pass signed-metadata, freshness, rollback, hash, size, package, version and signing-identity checks.", 14f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(0, dp(12), 0, dp(12))
        }
        body.addView(status)
        checkButton = action(body, "Check for updates") { checkUpdates() }
        downloadButton = action(body, "Download & verify APK") { download() }.apply { isEnabled = false }
        saveButton = action(body, "Save verified APK…") { chooseDestination() }.apply { isEnabled = false }
        action(body, "Cancel / discard download") { stop(); status.text = "Update cancelled. Check again when you are ready." }
        body.addView(ReferencePanelStyle.label(this,
            "You decide whether to install. Saving uses Android's file picker; then you can open the file yourself. Black Cat does not request permission to install apps or open an installer. No OpenAI key is used.", 13f))
        val scroll = ScrollView(this).apply { addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (ProductionUpdateTrust.configured() == null) status.text = NOT_PROVISIONED
    }

    private fun action(body: LinearLayout, text: String, run: () -> Unit): Button =
        ReferencePanelStyle.darkButton(this, text, action = run).apply { minHeight = dp(52) }.also {
            body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }

    private fun checkUpdates() {
        stop()
        val trust = ProductionUpdateTrust.configured()
        if (trust == null) { status.text = NOT_PROVISIONED; return }
        // Each generation owns a unique path: a cancelled worker cannot delete a later APK.
        staging = newStaging()
        val transport = UpdateHttps()
        val v = UpdateVerifier(trust, AndroidUpdateSecurity.installed(this),
            AndroidUpdateSecurity.packageInfo(this).requestedPermissions.orEmpty().toSet(), AndroidUpdateLedger(this),
            UpdateApkInspector { AndroidUpdateSecurity.inspect(this, it) }, transport)
        verifier = v
        work("Checking authenticated update metadata…") {
            val found = withContext(Dispatchers.IO) { v.check() }
            release = found
            val installed = AndroidUpdateSecurity.installed(this@UpdateActivity)
            if (found.version == installed.version) status.text = "This signed release is already installed. No APK downloaded."
            else {
                status.text = "Available: ${found.versionName} (${found.version}). Signed metadata accepted. APK size: ${found.size} bytes. Download it to verify its contents before saving."
                downloadButton.isEnabled = true
            }
        }
    }

    private fun download() {
        val v = verifier ?: return
        val found = release ?: return
        val file = staging
        work("Downloading to private storage and verifying the APK…") {
            withContext(Dispatchers.IO) { v.download(found, file) }
            status.text = "Verified ${found.versionName}. Package, capabilities and original signer match this app. SHA-256: ${found.hash}\nSave the APK if you want to install it yourself."
            saveButton.isEnabled = true
        }
    }

    private fun chooseDestination() {
        val found = release ?: return
        if (!staging.isFile || job?.isActive == true) return
        choosingFile = true
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_TITLE, "BlackCat-${found.version}.apk")
            }, SAVE_APK)
        } catch (_: Exception) {
            choosingFile = false
            stop()
            status.text = "No file picker is available. The private download was discarded."
        }
    }

    @Deprecated("Platform file-picker result; no installer or permission request")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SAVE_APK) return
        choosingFile = false
        val uri = data?.data
        val found = release
        val v = verifier
        val file = staging
        if (resultCode != RESULT_OK || uri?.scheme != "content" || found == null || v == null) {
            stop(); status.text = "Saving cancelled. The private download was discarded."; return
        }
        work("Rechecking and saving the verified APK…") {
            withContext(Dispatchers.IO) { export(v, found, uri, file) }
            stop()
            status.text = "Verified APK saved. Open it yourself in Files if you choose to install. SHA-256: ${found.hash}"
        }
    }

    private fun export(v: UpdateVerifier, found: UpdateRelease, uri: Uri, file: File) {
        try {
            v.verifyFile(found, file)
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            (contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")).use { out ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(16384)
                    while (true) {
                        v.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= found.size)
                        digest.update(buffer, 0, count)
                        out.write(buffer, 0, count)
                    }
                }
                check(total == found.size && digest.digest().joinToString("") { "%02x".format(it) } == found.hash)
                out.flush()
            }
            v.verifyFile(found, file)
        } catch (e: Exception) {
            // ACTION_CREATE_DOCUMENT creates a new, explicitly selected output. Best-effort removal.
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) { /* Provider may deny deletion. */ }
            throw e
        }
    }

    private fun work(message: String, operation: suspend () -> Unit) {
        if (job?.isActive == true) return
        val current = ++generation
        checkButton.isEnabled = false; downloadButton.isEnabled = false; saveButton.isEnabled = false
        status.text = message
        job = scope.launch {
            try { operation() }
            catch (_: Exception) {
                if (current == generation) {
                    stop()
                    status.text = "Update verification or saving failed. No installation was started. Check the network, device time and release trust configuration. If saving failed, delete any incomplete file left by the file provider."
                }
            } finally { if (current == generation) checkButton.isEnabled = true }
        }
    }

    private fun stop() {
        generation++; verifier?.cancel(); job?.cancel(); job = null
        verifier = null; release = null; staging.delete()
        checkButton.isEnabled = true; downloadButton.isEnabled = false; saveButton.isEnabled = false
    }
    override fun onPause() {
        super.onPause()
        if (!choosingFile) {
            stop()
            status.text = "Update work stopped when the app left the foreground. Check again to continue."
        }
    }
    override fun onDestroy() { stop(); scope.cancel(); super.onDestroy() }
    private fun dp(n: Int) = BlackCatStyle.dp(this, n)
    private fun newStaging() = File(noBackupFilesDir, "update-staging/checked-${java.util.UUID.randomUUID()}.apk")
    companion object {
        private const val SAVE_APK = 61
        internal const val NOT_PROVISIONED = "Secure updates are not configured for this build. No update request or APK download was made. A reviewed release-metadata public key, release endpoint and host policy must be provisioned first."
    }
}
