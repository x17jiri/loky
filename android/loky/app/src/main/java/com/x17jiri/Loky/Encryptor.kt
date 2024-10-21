package com.x17jiri.Loky

import android.util.Log
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class Encryptor(val __publicKey: ByteArray?, val __privateKey: ByteArray?) {
	companion object {
		fun hash(input: String): ByteArray {
			val md = MessageDigest.getInstance("SHA-256")
			return md.digest(input.toByteArray(Charsets.UTF_8))
		}

		fun encryptWithAES(input: String, key: SecretKey): ByteArray {
			val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
			cipher.init(Cipher.ENCRYPT_MODE, key)
			return cipher.doFinal(input.toByteArray(Charsets.UTF_8))
		}

		fun decryptWithAES(input: ByteArray, key: SecretKey): String {
			val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
			cipher.init(Cipher.DECRYPT_MODE, key)
			return String(cipher.doFinal(input), Charsets.UTF_8)
		}

		fun encryptWithRSA(input: ByteArray, publicKey: PublicKey): ByteArray {
			val cipher = Cipher.getInstance("RSA")
			cipher.init(Cipher.ENCRYPT_MODE, publicKey)
			return cipher.doFinal(input)
		}

		fun decryptWithRSA(input: ByteArray, privateKey: PrivateKey): ByteArray {
			val cipher = Cipher.getInstance("RSA")
			cipher.init(Cipher.DECRYPT_MODE, privateKey)
			return cipher.doFinal(input)
		}

		fun generateAESKey(): SecretKey {
			val keyGenerator = KeyGenerator.getInstance("AES")
			keyGenerator.init(256)
			return keyGenerator.generateKey()
		}
	}

	private var __rsaPublicKey: PublicKey? = null
	private var __rsaPrivateKey: PrivateKey? = null

	val publicKey: ByteArray?
		get() = __rsaPublicKey?.encoded

	val privateKey: ByteArray?
		get() = __rsaPrivateKey?.encoded

	init {
		if (__publicKey != null || __privateKey != null) {
			val kf = KeyFactory.getInstance("RSA")
			if (__publicKey != null) {
				try {
					val key: ByteArray = __publicKey
					__rsaPublicKey = kf.generatePublic(X509EncodedKeySpec(key))
				} catch (e: Exception) {
					Log.e("Locodile.Encryptor", "Failed to read public key", e)
				}
			}
			if (__privateKey != null) {
				try {
					val key: ByteArray = __privateKey
					__rsaPrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(key))
				} catch (e: Exception) {
					Log.e("Locodile.Encryptor", "Failed to generate private key", e)
				}
			}
		}
	}

	fun generateKeys() {
		val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
		keyPairGenerator.initialize(2048)
		val keyPair = keyPairGenerator.generateKeyPair()
		__rsaPublicKey = keyPair.public
		__rsaPrivateKey = keyPair.private
	}

	@OptIn(ExperimentalEncodingApi::class)
	fun encrypt(input: String, validationPrefix: String): String {
		if (__rsaPublicKey == null) {
			throw Exception("Public key is not set")
		}
		val validatedInput = validationPrefix + input
		val aesKey = generateAESKey()
		val encryptedMessage = encryptWithAES(validatedInput, aesKey)
		val encryptedAESKey = encryptWithRSA(aesKey.encoded, __rsaPublicKey!!)
		return Base64.encode(encryptedAESKey) + ":" + Base64.encode(encryptedMessage)
	}

	@OptIn(ExperimentalEncodingApi::class)
	fun decrypt(input: String, validatonPrefix: String): String? {
		if (__rsaPrivateKey == null) {
			throw Exception("Private key is not set")
		}
		val parts = input.split(":")
		if (parts.size != 2) {
			return null
		}

		val encryptedAESKey = Base64.decode(parts[0])
		val encryptedMessage = Base64.decode(parts[1])

		val aesKeyBytes = decryptWithRSA(encryptedAESKey, __rsaPrivateKey!!)
		val aesKey = SecretKeySpec(aesKeyBytes, "AES")

		val decryptedMessage = decryptWithAES(encryptedMessage, aesKey)

		if (!decryptedMessage.startsWith(validatonPrefix)) {
			return null
		}

		return decryptedMessage.substring(validatonPrefix.length)
	}
}

