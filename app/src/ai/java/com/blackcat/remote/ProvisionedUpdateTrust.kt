package com.blackcat.remote

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Owner-approved metadata authority; private key is never part of this app. */
internal object ProvisionedUpdateTrust {
    const val KEY_ID = "blackcat-8cb2f3d61ba630c5"
    const val PUBLIC_KEY_SHA256 = "8cb2f3d61ba630c526a2b3dd47449f540e1a2666414da6166655eba441147a1f"
    private const val SPKI = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA0lcJgZMqOOXnNmLvMB7zNzNszXPo4YBDaWbP6GDtphY7guhPf72ogZLhfqIkaa5dAcWggAbqSsfnAAKSUvwsfpV2C4C+q4X/q8jyeylmp5+xEDvMtuX05tZHrK6mxDn89cYMo2tNJx5anrC/L0Wv7yTTvqlvlUYtZ+vVpYp30zNwOLPHWWaUl+/MgAkgpI1VWaxINlBO3sV1wQDkQ2fB9ct0DaJM+/NXqu/4n10omH565p9C0Zgj/fVGytCvG+UBrLzDpGzgWxRm3ZAOsZh9Qk5Px91K5tQN0RgTEzBEqH9sVtaGDHqPfUXRikgNXcBtICC/A8m7CvQKKU+vPTShyj9Q6QJqKCMv3T+26I3J+f8i2N3dfnITJk16FF2RebAE/wRLehzaNfrWOGCVgVjZqXhZIkE5HboYHmunxL2RwsTCNBLegSdRW8dVQZY9gq61DiyQsaLq82joMSeLT/C/Bjk6D5ZVzwdzy89tcPzYHnkTc5r5MNZyQ8+gFbVYhoaVAgMBAAE="
    fun configured(): UpdateTrust {
        val der = Base64.getDecoder().decode(SPKI)
        check(SignedUpdateMetadata.sha256(der) == PUBLIC_KEY_SHA256)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        return UpdateTrust("https://raw.githubusercontent.com/efrgdgr0024345/autophone/blackcat-update-feed/updates/ai.json", setOf("raw.githubusercontent.com"), setOf("raw.githubusercontent.com"), mapOf(KEY_ID to key), "testing")
    }
}
