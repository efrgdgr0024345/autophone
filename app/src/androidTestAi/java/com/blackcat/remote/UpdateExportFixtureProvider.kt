package com.blackcat.remote

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.content.Intent
import java.io.File

/** Test APK only: simulates one explicit file-picker destination, never an installer. */
class UpdateExportFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        return true
    }
    private fun file(uri: Uri): File {
        val c = context!!
        check(Build.HARDWARE in setOf("ranchu", "goldfish") && uri.toString() == URI)
        val caller = Binder.getCallingUid()
        check(c.packageManager.getPackagesForUid(caller).orEmpty().any { it == "com.blackcat.remote" || it == "com.blackcat.remote.test" })
        check(c.packageManager.checkSignatures(caller, android.os.Process.myUid()) == PackageManager.SIGNATURE_MATCH)
        return File(c.cacheDir, "explicit-fixture-export.apk")
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        // The fake picker cannot issue framework URI grants. Only the disposable-emulator
        // shell may prepare this fixed, signature-checked test destination before the run.
        check(Build.HARDWARE in setOf("ranchu", "goldfish") && Binder.getCallingUid() in setOf(0, 2000))
        check(method == "grant-fixture-destination")
        val token = Binder.clearCallingIdentity()
        try {
            val c = context!!
            check(c.packageManager.checkSignatures("com.blackcat.remote", c.packageName) == PackageManager.SIGNATURE_MATCH)
            c.grantUriPermission("com.blackcat.remote", Uri.parse(URI), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            return Bundle().apply { putBoolean("granted", true) }
        } finally { Binder.restoreCallingIdentity(token) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = file(uri)
        val flags = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w", "wt" -> ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY
            else -> error("Unsupported fixture mode")
        }
        return ParcelFileDescriptor.open(file, flags)
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = if (file(uri).delete()) 1 else 0
    override fun getType(uri: Uri): String { file(uri); return "application/vnd.android.package-archive" }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? { file(uri); return null }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Not supported")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Not supported")
    companion object { const val URI = "content://com.blackcat.remote.test.update-export/checked.apk" }
}
