package com.x17jiri.Loky

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class PublicECKey(val key: PublicKey) {
	companion object {
		val PREFIX = "=ec:pub:"
		val SUFFIX = ":pub:ec="

		fun fromBytes(key: ByteArray): Result<PublicECKey> {
			try {
				val keyFactory = KeyFactory.getInstance("EC")
				val spec = X509EncodedKeySpec(key)
				return Result.success(PublicECKey(keyFactory.generatePublic(spec)))
			} catch (e: Exception) {
				return Result.failure(e)
			}
		}

		fun fromString(key: String): Result<PublicECKey> {
			return Crypto.extractFromStr(key, PREFIX, SUFFIX).mapCatching { k ->
				fromBytes(Base64.decode(k)).getOrThrow()
			}
		}
	}

	fun encode(): ByteArray {
		return key.encoded
	}

	override fun toString(): String {
		return PREFIX + Base64.encode(key.encoded) + SUFFIX
	}
}

class PrivateECKey(val key: PrivateKey) {
	companion object {
		val PREFIX = "=ec:priv:"
		val SUFFIX = ":priv:ec="

		fun fromBytes(key: ByteArray): Result<PrivateECKey> {
			try {
				val keyFactory = KeyFactory.getInstance("EC")
				val spec = PKCS8EncodedKeySpec(key)
				return Result.success(PrivateECKey(keyFactory.generatePrivate(spec)))
			} catch (e: Exception) {
				return Result.failure(e)
			}
		}

		fun fromString(key: String): Result<PrivateECKey> {
			return Crypto.extractFromStr(key, PREFIX, SUFFIX).mapCatching { k ->
				fromBytes(Base64.decode(k)).getOrThrow()
			}
		}
	}

	fun encode(): ByteArray {
		return key.encoded
	}

	override fun toString(): String {
		return PREFIX + Base64.encode(key.encoded) + SUFFIX
	}
}

class ECKeyPair(val public: PublicECKey, val private: PrivateECKey) {
	companion object {
		fun generate(): ECKeyPair {
			val keyPairGenerator = KeyPairGenerator.getInstance("EC")
			val ecSpec = ECGenParameterSpec("secp256r1")
			keyPairGenerator.initialize(ecSpec)
			val keyPair = keyPairGenerator.generateKeyPair()
			return ECKeyPair(
				PublicECKey(keyPair.public),
				PrivateECKey(keyPair.private)
			)
		}
	}
}

class Signature(val encoded: ByteArray) {
	companion object {
		val PREFIX = "=sig:"
		val SUFFIX = ":sig="

		fun fromBytes(bytes: ByteArray): Signature {
			return Signature(bytes)
		}

		fun fromString(str: String): Result<Signature> {
			return Crypto.extractFromStr(str, PREFIX, SUFFIX).mapCatching { s ->
				Signature(Base64.decode(s))
			}
		}
	}

	override fun toString(): String {
		return PREFIX + Base64.encode(encoded) + SUFFIX
	}
}

class PublicSigningKey(val key: PublicECKey) {
	companion object {
		val PREFIX = "=sig:pub:"
		val SUFFIX = ":pub:sig="

		fun fromBytes(key: ByteArray): Result<PublicSigningKey> {
			return PublicECKey.fromBytes(key).map { PublicSigningKey(it) }
		}

		fun fromString(key: String): Result<PublicSigningKey> {
			return Crypto.extractFromStr(key, PREFIX, SUFFIX).mapCatching { k ->
				PublicECKey.fromString(k).mapCatching { PublicSigningKey(it) }.getOrThrow()
			}
		}
	}

	fun encode(): ByteArray {
		return key.encode()
	}

	override fun toString(): String {
		return PREFIX + key.toString() + SUFFIX
	}
}

class PrivateSigningKey(val key: PrivateECKey) {
	companion object {
		val PREFIX = "=sig:priv:"
		val SUFFIX = ":priv:sig="

		fun fromBytes(key: ByteArray): Result<PrivateSigningKey> {
			return PrivateECKey.fromBytes(key).map { PrivateSigningKey(it) }
		}

		fun fromString(key: String): Result<PrivateSigningKey> {
			return Crypto.extractFromStr(key, PREFIX, SUFFIX).mapCatching { k ->
				PrivateECKey.fromString(k).mapCatching { PrivateSigningKey(it) }.getOrThrow()
			}
		}
	}

	fun encode(): ByteArray {
		return key.encode()
	}

	override fun toString(): String {
		return PREFIX + key.toString() + SUFFIX
	}
}

class SigningKeyPair(val public: PublicSigningKey, val private: PrivateSigningKey) {
	companion object {
		fun generate(): SigningKeyPair {
			val keyPair = ECKeyPair.generate()
			return SigningKeyPair(
				PublicSigningKey(keyPair.public),
				PrivateSigningKey(keyPair.private)
			)
		}
	}
}

class PublicDHKey(val key: PublicECKey) {
	companion object {
		val PREFIX = "=dh:pub:"
		val SUFFIX = ":pub:dh="

		fun fromBytes(key: ByteArray): Result<PublicDHKey> {
			return PublicECKey.fromBytes(key).map { PublicDHKey(it) }
		}

		fun fromString(key: String): Result<PublicDHKey> {
			return Crypto.extractFromStr(key, PREFIX, SUFFIX).mapCatching { k ->
				PublicECKey.fromString(k).mapCatching { PublicDHKey(it) }.getOrThrow()
			}
		}
	}

	fun encode(): ByteArray {
		return key.encode()
	}

	override fun toString(): String {
		return PREFIX + key.toString() + SUFFIX
	}

	fun signBy(privateSigningKey: PrivateSigningKey): SignedPublicDHKey {
		val signature = Crypto.sign(encode(), privateSigningKey)
		return SignedPublicDHKey(this, signature)
	}
}

class PrivateDHKey(val key: PrivateECKey) {
	companion object {
		val PREFIX = "=dh:priv:"
		val SUFFIX = ":priv:dh="

		fun fromBytes(key: ByteArray): Result<PrivateDHKey> {
			return PrivateECKey.fromBytes(key).map { PrivateDHKey(it) }
		}

		fun fromString(key: String): Result<PrivateDHKey> {
			return Crypto.extractFromStr(key, PREFIX, SUFFIX).mapCatching { k ->
				PrivateECKey.fromString(k).mapCatching { PrivateDHKey(it) }.getOrThrow()
			}
		}
	}

	fun encode(): ByteArray {
		return key.encode()
	}

	override fun toString(): String {
		return PREFIX + key.toString() + SUFFIX
	}
}

class DHKeyPair(val public: PublicDHKey, val private: PrivateDHKey) {
	companion object {
		fun generate(): DHKeyPair {
			val keyPair = ECKeyPair.generate()
			return DHKeyPair(
				PublicDHKey(keyPair.public),
				PrivateDHKey(keyPair.private)
			)
		}
	}
}

class SignedPublicDHKey(val key: PublicDHKey, val signature: Signature) {
	fun verifySignature(publicSigningKey: PublicSigningKey): Boolean {
		return Crypto.verifySignature(key.encode(), signature, publicSigningKey)
	}
}

class SecretKey(val key: javax.crypto.SecretKey) {
	companion object {
		val PREFIX = "=aes:"
		val SUFFIX = ":aes="

		fun fromBytes(key: ByteArray): Result<SecretKey> {
			try {
				val secretKey = SecretKeySpec(key, "AES")
				return Result.success(SecretKey(secretKey))
			} catch (e: Exception) {
				return Result.failure(e)
			}
		}

		fun fromString(key: String): Result<SecretKey> {
			return Crypto.extractFromStr(key, PREFIX, SUFFIX).mapCatching { k ->
				fromBytes(Base64.decode(k)).getOrThrow()
			}
		}

		fun generate(): SecretKey {
			val keyBits = 256
			val keyBytes = ByteArray(keyBits / 8)
			Crypto.rng.nextBytes(keyBytes)
			return SecretKey(SecretKeySpec(keyBytes, "AES"))
		}

		fun deriveFromSecret(secret: ByteArray): SecretKey {
			val keyBytes = Crypto.hash(secret)
			return SecretKey(SecretKeySpec(keyBytes, "AES"))
		}
	}

	fun encode(): ByteArray {
		return key.encoded
	}

	override fun toString(): String {
		return PREFIX + Base64.encode(encode()) + SUFFIX
	}
}

class Crypto {
	companion object {
		val rng = SecureRandom()
		val hasher = MessageDigest.getInstance("SHA-256")

		fun strToByteArray(str: String): ByteArray {
			return str.toByteArray(Charsets.UTF_8)
		}

		fun byteArrayToStr(bytes: ByteArray): String {
			return String(bytes, Charsets.UTF_8)
		}

		fun extractFromStr(str: String, prefix: String, suffix: String): Result<String> {
			val hasPrefix = str.startsWith(prefix)
			if (!hasPrefix) {
				return Result.failure(Exception("Prefix '$prefix' not found"))
			}
			val hasSuffix = str.endsWith(suffix)
			if (!hasSuffix) {
				return Result.failure(Exception("Suffix '$suffix' not found"))
			}
			return Result.success(str.substring(prefix.length, str.length - suffix.length))
		}

		fun hash(input: ByteArray): ByteArray {
			return hasher.digest(input)
		}

		fun encrypt(key: SecretKey, msg: ByteArray): ByteArray {
			val iv = ByteArray(16)
			rng.nextBytes(iv)
			val cipher = Cipher.getInstance("AES/CTR/NoPadding")
			cipher.init(Cipher.ENCRYPT_MODE, key.key, IvParameterSpec(iv))
			return iv + cipher.doFinal(msg)
		}

		fun decrypt(key: SecretKey, input: ByteArray): ByteArray? {
			// TODO - add some kind of integrity check
			// return null if the integrity check fails
			val iv = input.sliceArray(0 until 16)
			val msg = input.sliceArray(16 until input.size)
			val cipher = Cipher.getInstance("AES/CTR/NoPadding")
			cipher.init(Cipher.DECRYPT_MODE, key.key, IvParameterSpec(iv))
			return cipher.doFinal(msg)
		}

		fun diffieHellman(myPrivateKey: PrivateDHKey, theirPublicKey: PublicDHKey): ByteArray {
			val keyAgreement = KeyAgreement.getInstance("ECDH")
			keyAgreement.init(myPrivateKey.key.key)
			keyAgreement.doPhase(theirPublicKey.key.key, true)
			return keyAgreement.generateSecret()
		}

		fun deriveSharedKey(myPrivateKey: PrivateDHKey, theirPublicKey: PublicDHKey): SecretKey {
			return SecretKey.deriveFromSecret(
				Crypto.diffieHellman(myPrivateKey, theirPublicKey)
			)
		}

		fun sign(msg: ByteArray, key: PrivateSigningKey): Signature {
			val signature = java.security.Signature.getInstance("SHA256withECDSA")
			signature.initSign(key.key.key)
			signature.update(msg)
			return Signature(signature.sign())
		}

		fun verifySignature(msg: ByteArray, signature: Signature, key: PublicSigningKey): Boolean {
			val verifier = java.security.Signature.getInstance("SHA256withECDSA")
			verifier.initVerify(key.key.key)
			verifier.update(msg)
			return verifier.verify(signature.encoded)
		}
	}
}

object Base64 {
	val __encoder = java.util.Base64.getUrlEncoder().withoutPadding()
	val __decoder = java.util.Base64.getUrlDecoder()

	fun encode(bytes: ByteArray): String {
		return __encoder.encodeToString(bytes)
	}

	fun decode(str: String): ByteArray {
		return __decoder.decode(str)
	}
}

