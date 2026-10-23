package com.jellycine.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Only sessions are persisted. The user's password is never saved. */
class WatchSyncSessionStore(context: Context, private val projectUrl: String) {
    private val prefs = context.applicationContext.getSharedPreferences("watch_sync_secure",Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(): SecretKey = synchronized(WatchSyncSessionStore::class.java) {
        val keyStore=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey("jellycine.watch.sync",null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("jellycine.watch.sync",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    fun read(): WatchSyncAuthSession? {
        if (prefs.getString("project", null) != projectUrl) return null
        val encoded = prefs.getString("session", null) ?: return null
        val bytes=Base64.decode(encoded,Base64.NO_WRAP)
        require(bytes.size>12)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            updateAAD(projectUrl.toByteArray(Charsets.UTF_8))
        }
        return json.decodeFromString<WatchSyncAuthSession>(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
    }

    fun save(session: WatchSyncAuthSession) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE,key());updateAAD(projectUrl.toByteArray(Charsets.UTF_8))
        }
        val encoded=Base64.encodeToString(cipher.iv+cipher.doFinal(json.encodeToString(session).toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
        check(prefs.edit().putString("project", projectUrl).putString("session",encoded).commit())
    }

    fun clear() { check(prefs.edit().clear().commit()) }
}
