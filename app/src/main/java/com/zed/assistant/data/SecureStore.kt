package com.zed.assistant.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption for the one secret Zed persists: the Gemini API key.
 * The key material never leaves the Android Keystore, so a backup of the
 * preferences file alone cannot recover the credential.
 */
object SecureStore {

  private const val TAG = "ZedSecureStore"
  private const val KEYSTORE = "AndroidKeyStore"
  private const val ALIAS = "zed_api_key_v1"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val PREFIX = "enc:v1:"
  private const val IV_BYTES = 12

  fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

  fun encrypt(plaintext: String): String? = runCatching {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    PREFIX +
      Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
      Base64.encodeToString(ciphertext, Base64.NO_WRAP)
  }.onFailure { Log.e(TAG, "Could not encrypt secret", it) }.getOrNull()

  fun decrypt(value: String?): String? {
    if (!isEncrypted(value)) return null
    return runCatching {
      val payload = value!!.removePrefix(PREFIX)
      val split = payload.indexOf(':')
      require(split > 0) { "Malformed encrypted payload" }
      val iv = Base64.decode(payload.substring(0, split), Base64.NO_WRAP)
      val ciphertext = Base64.decode(payload.substring(split + 1), Base64.NO_WRAP)
      require(iv.size == IV_BYTES) { "Unexpected IV length" }
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
      String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.onFailure { Log.e(TAG, "Could not decrypt secret", it) }.getOrNull()
  }

  private fun key(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
        ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build()
    )
    return generator.generateKey()
  }
}
