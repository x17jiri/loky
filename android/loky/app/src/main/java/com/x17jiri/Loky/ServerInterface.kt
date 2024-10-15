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
			Log.d("Locodile", "ServerInterface.login: $name, $passwd")

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
			Log.d("Locodile", "ServerInterface.login: public key: $publicKey")

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
				Log.d("Locodile","ServerInterface.login: id: ${it.id}, token: ${it.token}")
				val newCred = cred.copy(
					id = it.id,
					token = it.token,
					enc = enc
				)
				Log.d("Locodile","ServerInterface.login: newCred: $newCred")
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
					var enc = Encryptor()
					enc.publicKey = Base64.decode(contact.publicKey)
					val prefix = "valid ${contact.id}:"
					val encrypted = enc.encrypt(data, prefix)
					items.add(Message(contact.id, encrypted, contact.keyHash))
				}
			}

			restAPI<SendRequest, List<SendResponseItem>>(
				"https://$server/api/send",
				SendRequest(cred.id, cred.token, items)
			).mapCatching {
				Log.d("Locodile", "ServerInterface.sendLoc: response=$it")
				if (it.isEmpty()) {
					Log.d("Locodile", "ServerInterface.sendLoc: no new keys")
					return@mapCatching
				}

				try {
					val map = it.associateBy { it.to }

					Log.d("Locodile", "ServerInterface.sendLoc: map=$map")

					model.contactsMan.update {
						Log.d("Locodile", "ServerInterface.sendLoc: updating contact $it")
						val item = map[it.id]
						if (item != null) {
							Log.d("Locodile", "ServerInterface.sendLoc: updating contact: $item")
							it.copy(publicKey = item.key, keyHash = item.keyHash)
						} else {
							Log.d("Locodile", "ServerInterface.sendLoc: NOT updating contact: $it")
							it
						}
					}
				} catch (e: Exception) {
					Log.d("Locodile", "ServerInterface.sendLoc: e=$e")
				}
			}
		}
	}
}
