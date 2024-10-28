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
	private val server = "loky.x17jiri.online:11443"
	private var trustManager: X509TrustManager
	private var sslContext: SSLContext
	private val gson = Gson()

	private val profileStore = context.__profileStore
	private val auth = profileStore.tmpCred
	private val contactsStore = context.__contactsStore
	private val preKeyStore = context.__preKeyStore

	// `init` prepares `sslContext` so that we trust the server's self-signed certificate.
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
		autoAuthorize: Boolean,
		responseType: Class<Response>
	): Result<Response> {
		try {
			val connection = URL(url).openConnection() as HttpsURLConnection
			connection.requestMethod = "POST"
			connection.setRequestProperty("Content-Type", "application/json")
			connection.setRequestProperty("Accept", "application/json")
			if (autoAuthorize) {
				val auth = this.auth.value
				connection.setRequestProperty("Authorization", "Token ${auth.id},${auth.token}")
			}
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

	inline suspend fun <reified Request, reified Response> restAPI(
		url: String,
		request: Request,
		autoAuthorize: Boolean = true,
	): Result<Response> {
		return __restAPI(url, request, autoAuthorize, Response::class.java)
	}

	@OptIn(ExperimentalEncodingApi::class)
	suspend fun login(): Result<Unit> {
		return withContext(Dispatchers.IO) {
			val cred = profileStore.cred.value
			val signingKeys = profileStore.signingKeys.value

			// Note: the password hash is not used for security.
			// It is used so we can limit message size on the server side
			// without limiting the password size.
			val username = cred.user
			val passwd = Base64.encode(Encryptor.hash(cred.passwd))

			val keyPair: SigningKeyPair
			if (signingKeys.keyPair != null && signingKeys.keyOwner == username) {
				keyPair = signingKeys.keyPair
			} else {
				try {
					keyPair = SigningKeyPair.generate()
					profileStore.updateKeys { SigningKeys(keyPair, username) }
				} catch (e: Exception) {
					Log.d("Locodile", "ServerInterface.login: e=$e")
					return@withContext Result.failure(e)
				}
			}

			data class LoginRequest(
				val name: String,
				val passwd: String,
				val public_signing_key: String,
			)

			data class LoginResponse(
				val id: Long,
				val token: String,
			)

			restAPI<LoginRequest, LoginResponse>(
				"https://$server/api/login",
				LoginRequest(username, passwd, keyPair.public.toString()),
				autoAuthorize = false,
			).mapCatching { resp ->
				profileStore.updateTmpCred {
					TmpCredentials(
						id = resp.id,
						token = resp.token,
					)
				}
				Unit
			}
		}
	}

	suspend fun userInfo(username: String): Result<UserInfo> {
		return withContext(Dispatchers.IO) {
			data class UserInfoRequest(
				val username: String,
			)

			data class UserInfoResponse(
				val id: Long,
				val public_signing_key: String,
			)

			restAPI<UserInfoRequest, UserInfoResponse>(
				"https://$server/api/userInfo",
				UserInfoRequest(username),
			).mapCatching { resp ->
				UserInfo(resp.id, PublicSigningKey.fromString(resp.public_signing_key).getOrThrow())
			}
		}
	}

	suspend fun fetchPreKeys(contacts: List<SendChan>): Result<
		List<Pair<SendChan, SignedPublicDHKey?>>
	> {
		return withContext(Dispatchers.IO) {
			data class FetchPreKeysRequest(
				val who: List<Long>,
			)

			data class FetchPreKeysResponse(
				val prekeys: List<String>,
			)

			restAPI<FetchPreKeysRequest, FetchPreKeysResponse>(
				"https://$server/api/fetchPrekeys",
				FetchPreKeysRequest(contacts.map { it.id })
			).mapCatching { response ->
				contacts.zip(response.prekeys).map { (contact, keySig) ->
					val parts = keySig.split(",")
					if (parts.size != 2) {
						return@map Pair(contact, null)
					}

					val key = PublicDHKey.fromString(parts[0]).getOrNull()
					val sig = Signature.fromString(parts[1]).getOrNull()
					if (key == null || sig == null) {
						return@map Pair(contact, null)
					}

					Pair(contact, SignedPublicDHKey(key, sig))
				}
			}
		}
	}

	suspend fun addPreKeys(): Result<Unit> {
		preKeyStore.generate { newKeys ->
			data class AddPreKeysRequest(
				val prekeys: List<String>,
			)

			data class AddPreKeysResponse(
				val used_prekeys: List<String>,
				val added: Boolean,
			)

			val mySigningKeys = profileStore.signingKeys.value.keyPair
				?: return@generate Result.failure(Exception("No signing keys. Cannot sign prekeys."))

			restAPI<AddPreKeysRequest, AddPreKeysResponse>(
				"https://$server/api/addPrekeys",
				AddPreKeysRequest(newKeys.map { newKey ->
					val signedKey = newKey.signBy(mySigningKeys.private)
					"${signedKey.key.string},${signedKey.signature.string}"
				}),
			).mapCatching { resp ->
				if (resp.used_prekeys.isNotEmpty()) {
					preKeyStore.markUsed(resp.used_prekeys.mapNotNull { keySig ->
						val parts = keySig.split(",")
						if (parts.size != 2) {
							return@mapNotNull null
						}
						PublicDHKey.fromString(parts[0]).getOrNull()
					})
					preKeyStore.delExpired()
				}
				if (!resp.added) {
					throw Exception("Server did not accept prekeys")
				}
			}
		}
	}

	@OptIn(ExperimentalEncodingApi::class)
	suspend fun sendLoc(loc: Location, contacts: List<SendChan>): Result<Unit> {
		if (contacts.isEmpty()) {
			return Result.success(Unit)
		}

		return withContext(Dispatchers.IO) {
			data class Message(
				val to: Long,
				val type: String,
				val msg: String,
			)

			data class SendRequest(
				val messages: List<Message>,
			)

			data class SendResponse(
				val needPreKeys: Boolean,
			)

			val messages = mutableListOf<Message>()

			val now = monotonicSeconds()
			val needNewKeys = contacts.filter { it.needNewKeys(now) }
			if (needNewKeys.isNotEmpty()) {
				fetchPreKeys(needNewKeys).fold(
					onSuccess = {
						for ((contact, theirNewKey) in it) {
							// Note that we want to call `switchKeys()` even if `theirNewKey` is null.
							contact.switchKeys(now, theirNewKey) { myNewKey, theirNewKey ->
								val myKey = myNewKey.key.toString()
								val mySig = myNewKey.signature.toString()
								val theirKey = theirNewKey.toString()
								messages.add(Message(contact.id, "k", "$myKey,$mySig,$theirKey"))
							}
						}
					},
					onFailure = { e ->
						return@withContext Result.failure(e)
					}
				)
			}

			val msg = loc.latitude.toString() + "," + loc.longitude.toString()
			for (contact in contacts) {
				contact.encrypt(msg).onSuccess { encrypted ->
					messages.add(Message(contact.id, "", Base64.encode(encrypted)))
				}
			}

			restAPI<SendRequest, SendResponse>(
				"https://$server/api/send",
				SendRequest(messages)
			).mapCatching {
				if (it.needPreKeys) {
					sendPreKeys().getOrThrow()
				} else {
					Unit
				}
			}
		}
	}

	@OptIn(ExperimentalEncodingApi::class)
	suspend fun recv(contacts: Map<Long, RecvChan>): Result<List<Message>> {
		if (contacts.isEmpty()) {
			return Result.success(emptyList())
		}

		return withContext(Dispatchers.IO) {
			data class RecvRequest()

			data class RecvResponseItem(
				val from: Long,
				val ageSeconds: Long,
				val type: String,
				val msg: String,
			)

			data class RecvResponse(
				val items: List<RecvResponseItem>,
			)

			val now = monotonicSeconds()
			restAPI<RecvRequest, RecvResponse>(
				"https://$server/api/recv",
				RecvRequest()
			).mapCatching {
				val messages = mutableListOf<Message>()
				for (msg in it.items) {
					try {
						val contact = contacts[msg.from] ?: continue
						if (msg.type == "k") {
							val keys = msg.msg.split(",")
							if (keys.size != 3) {
								Log.d("Locodile", "ServerInterface.recv: invalid keys")
								continue
							}
							val theirKey = PublicDHKey.fromString(keys[0]).getOrThrow()
							val theirSig = Signature.fromString(keys[1]).getOrThrow()
							val myKey = PublicDHKey.fromString(keys[2]).getOrThrow()
							contact.switchKeys(myKey, SignedPublicDHKey(theirKey, theirSig))
						} else {
							val encrypted = Base64.decode(msg.msg)
							val decrypted = contact.decrypt(encrypted).getOrThrow()
							val parts = decrypted.split(",")
							if (parts.size != 2) {
								Log.d("Locodile", "ServerInterface.recv: invalid loc")
								continue
							}
							val timestamp = now - msg.ageSeconds
							val lat = parts[0].toDouble()
							val lon = parts[1].toDouble()
							messages.add(Message(msg.from, timestamp, lat, lon))
						}
					} catch (e: Exception) {
						Log.d("Locodile", "ServerInterface.recv: e=$e")
					}
				}
				messages
			}
		}
	}
}
