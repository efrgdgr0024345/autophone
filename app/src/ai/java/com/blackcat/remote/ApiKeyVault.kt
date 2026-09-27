package com.blackcat.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Personal, opt-in BYOK storage; never an embedded/shared developer credential. */
class ApiKeyVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "openai-key-v1.enc"))
    private val aad = context.packageName.toByteArray(Charsets.UTF_8)
    fun exists(): Boolean = file.baseFile.exists()
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(create: Boolean): SecretKey {
        (store().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "Saved key is unavailable." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    // Call cryptographic/storage operations on Dispatchers.IO, not the UI thread.
    @Synchronized fun save(value: String) {
        require(OpenAiPlanner.validKey(value))
        val plain = value.toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(true))
            cipher.updateAAD(aad)
            check(cipher.iv.size == 12)
            val data = byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
            val output = file.startWrite()
            try { output.write(data); file.finishWrite(output) }
            catch (e: Exception) { file.failWrite(output); throw e }
        } finally { plain.fill(0) }
    }
    @Synchronized fun read(): String? {
        if (!exists()) return null
        check(file.baseFile.length() in 29..2048)
        val data = file.readFully()
        check(data[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, data.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(data, 13, data.size - 13)
        return try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
    }
    @Synchronized fun forget() {
        file.delete()
        store().deleteEntry(ALIAS)
        check(!exists())
    }
    private companion object { const val ALIAS = "blackcat.ai.personal-key.v1" }
}
