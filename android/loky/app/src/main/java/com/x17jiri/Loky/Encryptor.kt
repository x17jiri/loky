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

class Encryptor {
	companion object {
		private const val RSA_ALGORITHM = "RSA"
		private const val AES_ALGORITHM = "AES/ECB/PKCS5Padding"

		fun hash(input: String): ByteArray {
			val md = MessageDigest.getInstance("SHA-256")
			return md.digest(input.toByteArray(Charsets.UTF_8))
		}

		fun encryptWithAES(input: String, key: SecretKey): ByteArray {
			val cipher = Cipher.getInstance(AES_ALGORITHM)
			cipher.init(Cipher.ENCRYPT_MODE, key)
			return cipher.doFinal(input.toByteArray(Charsets.UTF_8))
		}

		fun decryptWithAES(input: ByteArray, key: SecretKey): String {
			val cipher = Cipher.getInstance(AES_ALGORITHM)
			cipher.init(Cipher.DECRYPT_MODE, key)
			return String(cipher.doFinal(input), Charsets.UTF_8)
		}

		fun encryptWithRSA(input: ByteArray, publicKey: PublicKey): ByteArray {
			val cipher = Cipher.getInstance(RSA_ALGORITHM)
			cipher.init(Cipher.ENCRYPT_MODE, publicKey)
			return cipher.doFinal(input)
		}

		fun decryptWithRSA(input: ByteArray, privateKey: PrivateKey): ByteArray {
			val cipher = Cipher.getInstance(RSA_ALGORITHM)
			cipher.init(Cipher.DECRYPT_MODE, privateKey)
			return cipher.doFinal(input)
		}

		fun generateAESKey(): SecretKey {
			val keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM)
			keyGenerator.init(256)
			return keyGenerator.generateKey()
		}
	}

	private var rsaPublicKey: PublicKey? = null
	private var rsaPrivateKey: PrivateKey? = null

	var publicKey: ByteArray?
		get() = rsaPublicKey?.encoded
		set(value) {
			rsaPublicKey = null
			if (value != null) {
				try {
					KeyFactory
						.getInstance(RSA_ALGORITHM)
						.generatePublic(X509EncodedKeySpec(value))
				} catch (e: Exception) {}
			}
		}

	var privateKey: ByteArray?
		get() = rsaPrivateKey?.encoded
		set(value) {
			rsaPrivateKey = null
			if (value != null) {
				try {
					KeyFactory
						.getInstance(RSA_ALGORITHM)
						.generatePrivate(PKCS8EncodedKeySpec(value))
				} catch (e: Exception) {}
			}
		}

	fun generateKeys() {
		val keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM)
		keyPairGenerator.initialize(2048)
		val keyPair = keyPairGenerator.generateKeyPair()
		rsaPublicKey = keyPair.public
		rsaPrivateKey = keyPair.private
	}

	@OptIn(ExperimentalEncodingApi::class)
	fun encrypt(input: String, validationPrefix: String): String {
		assert(rsaPublicKey != null)
		val validatedInput = validationPrefix + input
		val aesKey = generateAESKey()
		val encryptedMessage = encryptWithAES(validatedInput, aesKey)
		val encryptedAESKey = encryptWithRSA(aesKey.encoded, rsaPublicKey!!)
		Log.d("Locodile", "length of encrypred key: ${Base64.encode(encryptedAESKey).length}")
		return Base64.encode(encryptedAESKey) + ":" + Base64.encode(encryptedMessage)
	}

	@OptIn(ExperimentalEncodingApi::class)
	fun decrypt(input: String, validatonPrefix: String): String? {
		assert(rsaPrivateKey != null)
		val parts = input.split(":")
		if (parts.size != 2) {
			return null
		}

		val encryptedAESKey = Base64.decode(parts[0])
		val encryptedMessage = Base64.decode(parts[1])

		val aesKeyBytes = decryptWithRSA(encryptedAESKey, rsaPrivateKey!!)
		val aesKey = SecretKeySpec(aesKeyBytes, AES_ALGORITHM)

		val decryptedMessage = decryptWithAES(encryptedMessage, aesKey)

		if (!decryptedMessage.startsWith(validatonPrefix)) {
			return null
		}

		return decryptedMessage.substring(validatonPrefix.length)
	}
}

