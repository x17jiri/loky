package com.x17jiri.Loky

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.io.InputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.URL
import java.util.Calendar
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.reflect.Type
import java.time.Instant
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class UserInfo(
	val id: Long,
	val publicSigningKey: PublicSigningKey,
)

class ServerInterface(
	context: Context,
	val coroutineScope: CoroutineScope,
) {
	private val server = "loky.x17jiri.online:9443"
	private var trustManager: X509TrustManager
	private var sslContext: SSLContext
	private val gson = Gson()

	private val credMan = context.__credMan
	private val contactsMan = context.__contactsMan

	init {
		val input: InputStream = context.resources.openRawResource(R.raw.ca)
		val serverCA = CertificateFactory
			.getInstance("X.509")
			.generateCertificate(input)

		val keyStoreType = KeyStore.getDefaultType()
		val keyStore = KeyStore.getInstance(keyStoreType)
		keyStore.load(null, null) // Initialize a new empty keystore
		keyStore.setCertificateEntry("ca", serverCA) // Set the trusted CA

		// Create a TrustManager that trusts the CAs in our KeyStore
		val trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
		val trustManagerFactory = TrustManagerFactory.getInstance(trustManagerAlgorithm)
		trustManagerFactory.init(keyStore)
		val trustManagers = trustManagerFactory.getTrustManagers()

		trustManager = trustManagers[0] as X509TrustManager

		sslContext = SSLContext.getInstance("TLS")
		sslContext.init(null, arrayOf(trustManager), SecureRandom())

		HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
		HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
	}

	suspend fun <Request, Response> __restAPI(
		url: String,
		request: Request,
		responseType: Class<Response>
	): Result<Response> {
		try {
			val connection = URL(url).openConnection() as HttpsURLConnection
			connection.requestMethod = "POST"
			connection.setRequestProperty("Content-Type", "application/json")
			connection.setRequestProperty("Accept", "application/json")
			connection.doOutput = true
			Log.d("Locodile", "ServerInterface.restAPI: url=$url, request=$request")

			connection.outputStream.use { os ->
				val input = gson.toJson(request).toByteArray(Charsets.UTF_8)
				os.write(input, 0, input.size)
				os.flush()
			}
			Log.d("Locodile", "ServerInterface.restAPI: connection=$connection")

			try {
				connection.connect()
				val responseCode = connection.responseCode
				if (responseCode == HttpsURLConnection.HTTP_OK) {
					val response = connection.inputStream.bufferedReader().use { it.readText() }
					return Result.success(gson.fromJson(response, responseType))
				} else {
					Log.d("Locodile", "ServerInterface.restAPI: response code=$responseCode")
					var error = ""
					val errorStream = connection.errorStream
					if (errorStream != null) {
						error = errorStream.bufferedReader().use { it.readText() }
						Log.d("Locodile", "ServerInterface.restAPI: error=$error")
					}
					return Result.failure(Exception("Server error: $responseCode $error"))
				}
			} finally {
				connection.disconnect()
			}
		} catch (e: Exception) {
			Log.d("Locodile", "ServerInterface.restAPI: e=$e")
			return Result.failure(e)
		}
	}

	inline suspend fun <reified Request, reified Response> restAPI(url: String, request: Request): Result<Response> {
		return __restAPI(url, request, Response::class.java)
	}

	@OptIn(ExperimentalEncodingApi::class)
	suspend fun login(): Result<Unit> {
		return withContext(Dispatchers.IO) {
			val cred = credMan.cred.value
			val keys = credMan.keys.value

			// Note: the password hash is not used for security.
			// It is used so we can limit message size on the server side
			// without limiting the password size.
			val username = cred.user
			val passwd = Base64.encode(Encryptor.hash(cred.passwd))

			val publicKey: String
			val privateKey: String
			val keyHash: String
			if (keys.areValid(username)) {
				publicKey = keys.publicKey
				privateKey = keys.privateKey
				keyHash = keys.keyHash
			} else {
				val enc = Encryptor(null, null)
				enc.generateKeys()
				val publicKeyBytes = enc.publicKey
				val privateKeyBytes = enc.privateKey
				if (publicKeyBytes == null || privateKeyBytes == null) {
					return@withContext Result.failure(Exception("Failed to generate keys"))
				}
				publicKey = Base64.encode(publicKeyBytes)
				privateKey = Base64.encode(privateKeyBytes)
				keyHash = Base64.encode(Encryptor.hash(publicKey))
				credMan.updateKeys {
					Keys(
						publicKey = publicKey,
						privateKey = privateKey,
						keyHash = keyHash,
						keyOwner = username,
					)
				}
			}

			data class LoginRequest(
				val name: String,
				val passwd: String,
				val key: String,
				val keyHash: String,
			)

			data class LoginResponse(
				val id: Long,
				val token: String,
			)

			restAPI<LoginRequest, LoginResponse>(
				"https://$server/api/login",
				LoginRequest(username, passwd, publicKey, keyHash)
			).mapCatching { value ->
				credMan.updateTmpCred {
					TmpCredentials(
						id = value.id,
						token = value.token,
					)
				}
				Unit
			}
		}
	}

	suspend fun userInfo(userName: String): Result<UserInfo> {
		return withContext(Dispatchers.IO) {
			data class UserInfoRequest(
				val name: String,
			)

			data class UserInfoResponse(
				val id: Long,
				val publicSigningKey: String,
			)

			restAPI<UserInfoRequest, UserInfoResponse>(
				"https://$server/api/userInfo",
				UserInfoRequest(userName)
			).mapCatching {
				val publicSigningKey = PublicSigningKey.fromString(it.publicSigningKey).getOrNull()
				if (publicSigningKey == null) {
					throw Exception("Invalid public signing key")
				}
				UserInfo(it.id, publicSigningKey)
			}
		}
	}

	// TODO
	// Every user has their list of keys:
	//     - private
	//     - public
	//     - signature of public
	//     - random ID of the key
	//
	// Send:
	//     - just put the message in inbox
	//         - sender key ID - 32 bits
	//         - recipient key ID - 32 bits
	//         - message encrypted using shared secret
	//     - response:
	//         - recipient's last known public key + signature + key ID
	//
	// Recv:
	//     - get all messages from inbox
	//     - check that recipient key is in my list of keys
	//         - if not, throw away the message
	//     - check that the message can be decrypted
	//         - if it fails, request the whole list of the sender's keys and try again
	//         - it can fail either because we don't have the sender's key
	//         - or there is a new key with the same ID
	//         - or possibly corruption, but then requesting new list of keys will not help anyway

	@OptIn(ExperimentalEncodingApi::class)
	suspend fun sendLoc(loc: Location, contacts: List<Contact>): Result<Unit> {
		if (contacts.isEmpty()) {
			return Result.success(Unit)
		}

		return withContext(Dispatchers.IO) {
			data class Message(
				val to: Long,
				val data: String,
				val keyHash: String,
			)

			data class SendRequest(
				val id: Long,
				val token: String,
				val items: List<Message>,
			)

			data class SendResponseItem(
				val to: Long,
				val key: String,
				val keyHash: String,
			)

			data class SendResponse(
				val updatedKeys: List<SendResponseItem>,
			)

			// TODO - should specify precision of the `toString()` conversion ??
			val data = loc.latitude.toString() + "," + loc.longitude.toString()
			val items = mutableListOf<Message>()
			for (contact in contacts) {
				if (contact.publicKey.isEmpty() || contact.keyHash.isEmpty()) {
					// We don't have the contact's public key yet,
					// so we cannot encrypt the message.
					// Send an empty message instead.
					// The server will respond with the contact's public key.
					items.add(Message(contact.id, "", ""))
				} else {
					try {
						val enc = Encryptor(Base64.decode(contact.publicKey), null)
						val prefix = "valid ${contact.id}:"
						val encrypted = enc.encrypt(data, prefix)
						items.add(Message(contact.id, encrypted, contact.keyHash))
					} catch (e: Exception) {
						Log.d("Locodile", "ServerInterface.sendLoc: e=$e")
					}
				}
			}

			val tmpCred = credMan.tmpCred.value
			restAPI<SendRequest, SendResponse>(
				"https://$server/api/send",
				SendRequest(tmpCred.id, tmpCred.token, items)
			).mapCatching {
				val updatedKeys = it.updatedKeys
				if (updatedKeys.isNotEmpty()) {
					contactsMan.launchEdit { contactDao ->
						for (item in updatedKeys) {
							contactDao.updateKey(item.to, item.key, item.keyHash)
						}
					}
				}
				Unit
			}
		}
	}

	@OptIn(ExperimentalEncodingApi::class)
	suspend fun recv(): Result<List<Message>> {
		return withContext(Dispatchers.IO) {
			data class RecvRequest(
				val id: Long,
				val token: String,
			)

			data class RecvResponseItem(
				val from: Long,
				val ageSeconds: Long,
				val data: String,
			)

			data class RecvResponse(
				val items: List<RecvResponseItem>,
			)

			val cred = credMan.tmpCred.value
			val keys = credMan.keys.value
			restAPI<RecvRequest, RecvResponse>(
				"https://$server/api/recv",
				RecvRequest(cred.id, cred.token)
			).mapCatching {
				val enc = Encryptor(null, Base64.decode(keys.privateKey))
				val prefix = "valid ${cred.id}:"
				val now = monotonicSeconds()
				it.items.map { item ->
					val decrypted = enc.decrypt(item.data, prefix)
					if (decrypted != null) {
						try {
							val (lat, lon) = decrypted.split(",").map { it.toDouble() }
							Message(item.from, now - item.ageSeconds, lat, lon)
						} catch (e: Exception) {
							Log.d("Locodile", "ServerInterface.recv: e=$e")
							null
						}
					} else {
						null
					}
				}.filterNotNull()
			}
		}
	}
}
