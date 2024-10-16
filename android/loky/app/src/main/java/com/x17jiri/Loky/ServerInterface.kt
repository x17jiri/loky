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

class ServerInterface(context: Context, model: MainViewModel) {
	private val dataStore = context.dataStore
	private val model = model
	private val server = "10.0.0.2:9443"
	private var trustManager: X509TrustManager
	private var sslContext: SSLContext
	private val gson = Gson()

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

	companion object {
		val __publicKeyKey = stringPreferencesKey("key.public")
		val __privateKeyKey = stringPreferencesKey("key.private")
		val __keyHashKey = stringPreferencesKey("key.hash")
		val __keyOwnerKey = stringPreferencesKey("key.owner")
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
	suspend fun login(cred: Credentials): Result<Credentials> {
		return withContext(Dispatchers.IO) {
			val name = cred.user
			// Note: the hash is not used for security.
			// It is used so we can limit message size on the server side
			// without limiting the password size.
			val passwd = Base64.encode(Encryptor.hash(cred.passwd))

			var publicKey = ""
			var privateKey = ""
			var keyHash = ""
			var keyOwner = ""
			dataStore.data.first().let {
				publicKey = it[__publicKeyKey] ?: ""
				privateKey = it[__privateKeyKey] ?: ""
				keyHash = it[__keyHashKey] ?: ""
				keyOwner = it[__keyOwnerKey] ?: ""
			}
			var enc = cred.enc
			if (
				publicKey.isNotEmpty()
				&& privateKey.isNotEmpty()
				&& keyHash.isNotEmpty()
				&& keyOwner == name
			) {
				enc.publicKey = Base64.decode(publicKey)
				enc.privateKey = Base64.decode(privateKey)
			} else {
				enc.generateKeys()
				publicKey = Base64.encode(enc.publicKey!!)
				privateKey = Base64.encode(enc.privateKey!!)
				keyHash = Base64.encode(Encryptor.hash(publicKey))
				dataStore.edit {
					it[__publicKeyKey] = publicKey
					it[__privateKeyKey] = privateKey
					it[__keyHashKey] = keyHash
					it[__keyOwnerKey] = name
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
				LoginRequest(name, passwd, publicKey, keyHash)
			).mapCatching {
				val newCred = cred.copy(
					id = it.id,
					token = it.token,
					enc = enc
				)
				newCred
			}
		}
	}

	suspend fun userInfo(userName: String): Result<Long> {
		return withContext(Dispatchers.IO) {
			data class UserInfoRequest(
				val name: String,
			)

			data class UserInfoResponse(
				val id: Long,
			)

			restAPI<UserInfoRequest, UserInfoResponse>(
				"https://$server/api/userInfo",
				UserInfoRequest(userName)
			).mapCatching {
				it.id
			}
		}
	}

	@OptIn(ExperimentalEncodingApi::class)
	suspend fun sendLoc(cred: Credentials, loc: Location): Result<Unit> {
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

			var contacts = model.contactsMan.contacts.value.filter { it.send }
			if (contacts.isEmpty()) {
				return@withContext Result.success(Unit)
			}

			val data = loc.latitude.toString() + "," + loc.longitude.toString()
			var items = mutableListOf<Message>()
			for (contact in contacts) {
				if (contact.publicKey.isEmpty() || contact.keyHash.isEmpty()) {
					// We don't have the contact's public key yet,
					// so we cannot encrypt the message.
					// Send an empty message instead.
					// The server will respond with the contact's public key.
					items.add(Message(contact.id, "", ""))
				} else {
					try {
						var enc = Encryptor()
						enc.publicKey = Base64.decode(contact.publicKey)
						val prefix = "valid ${contact.id}:"
						val encrypted = enc.encrypt(data, prefix)
						items.add(Message(contact.id, encrypted, contact.keyHash))
					} catch (e: Exception) {
						Log.d("Locodile", "ServerInterface.sendLoc: e=$e")
					}
				}
			}

			restAPI<SendRequest, SendResponse>(
				"https://$server/api/send",
				SendRequest(cred.id, cred.token, items)
			).mapCatching {
				if (it.updatedKeys.isEmpty()) {
					return@mapCatching
				}

				val map = it.updatedKeys.associateBy { it.to }

				model.contactsMan.update {
					val item = map[it.id]
					if (item != null) {
						it.copy(publicKey = item.key, keyHash = item.keyHash)
					} else {
						it
					}
				}
			}
		}
	}

	suspend fun recv(cred: Credentials, process: (Message) -> Unit): Result<Unit> {
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

			restAPI<RecvRequest, RecvResponse>(
				"https://$server/api/recv",
				RecvRequest(cred.id, cred.token)
			).mapCatching {
				val enc = cred.enc
				val prefix = "valid ${cred.id}:"
				val now = Instant.now()
				for (item in it.items) {
					val decrypted = enc.decrypt(item.data, prefix)
					if (decrypted != null) {
						try {
							val (lat, lon) = decrypted.split(",").map { it.toDouble() }
							process(Message(item.from, lat, lon, now.minusSeconds(item.ageSeconds)))
						} catch (e: Exception) {
							Log.d("Locodile", "ServerInterface.recv: e=$e")
						}
					}
				}
			}
		}
	}
}
