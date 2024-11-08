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
import kotlinx.coroutines.withContext
import java.lang.reflect.Type
import java.time.Instant
import kotlin.math.abs

data class UserInfo(
	val id: String,
	val publicSigningKey: PublicSigningKey,
)

data class NeedPrekeys(val value: Boolean)

class ServerInterface(
	context: Context,
	val coroutineScope: CoroutineScope,
) {
	private val server = "10.0.0.2:11443"
	private var trustManager: X509TrustManager
	private var sslContext: SSLContext
	private val gson = Gson()

	private val profileStore = context.__profileStore
	private val bearer = profileStore.bearer
	private val settingsStore = context.__settings
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
		useBearer: Boolean,
		responseType: Class<Response>
	): Result<Response> {
		try {
			val connection = URL(url).openConnection() as HttpsURLConnection
			connection.requestMethod = "POST"
			connection.setRequestProperty("Content-Type", "application/json")
			connection.setRequestProperty("Accept", "application/json")
			if (useBearer) {
				connection.setRequestProperty("Authorization", "Bearer ${this.bearer.value}")
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
					Log.d("Locodile", "ServerInterface.restAPI: response=$response")
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
		useBearer: Boolean = true,
	): Result<Response> {
		val res = __restAPI(url, request, useBearer, Response::class.java)
		Log.d("Locodile", "ServerInterface.__restAPI: res=$res")
		return res
	}

	suspend fun login(): Result<NeedPrekeys> {
		return withContext(Dispatchers.IO) {
			// Note: the password hash is not used for security.
			// It is used so we can limit message size on the server side
			// without limiting the password size.
			val cred = profileStore.cred.value
			val username = cred.username
			val passwd = Base64.encode(Crypto.hash(Crypto.strToByteArray(cred.passwd)))

			val signingKeys = profileStore.signingKeys.value
			val keyPair: SigningKeyPair
			Log.d("Locodile", "ServerInterface.login: signingKeys=$signingKeys")
			Log.d("Locodile", "ServerInterface.login: signingKeys.keyPair=${signingKeys.keyPair}")
			Log.d("Locodile", "ServerInterface.login: signingKeys.keyOwner=${signingKeys.keyOwner}")
			Log.d("Locodile", "ServerInterface.login: username=$username")
			if (signingKeys.keyPair != null && signingKeys.keyOwner == username) {
				keyPair = signingKeys.keyPair
			} else {
				try {
					Log.d("Locodile", "ServerInterface.login: generating keys")
					keyPair = SigningKeyPair.generate()
					Log.d("Locodile", "ServerInterface.login: generated keys")
					profileStore.setSigningKeys(keyPair, username)
					Log.d("Locodile", "ServerInterface.login: saved keys")
				} catch (e: Exception) {
					Log.d("Locodile", "ServerInterface.login: e=$e")
					return@withContext Result.failure(e)
				}
			}

			data class LoginRequest(
				val username: String,
				val passwd: String,
				val key: String, // our public signing key
			)

			data class LoginResponse(
				val bearer: String,
				val needPrekeys: Boolean,
			)

			restAPI<LoginRequest, LoginResponse>(
				"https://$server/api/login",
				LoginRequest(username, passwd, keyPair.public.toString()),
				useBearer = false,
			).mapCatching { resp ->
				profileStore.setBearer(resp.bearer)
				NeedPrekeys(resp.needPrekeys)
			}
		}
	}

	suspend fun userInfo(username: String): Result<UserInfo> {
		return withContext(Dispatchers.IO) {
			data class UserInfoRequest(
				val username: String,
			)

			data class UserInfoResponse(
				val id: String,
				val key: String,
			)

			restAPI<UserInfoRequest, UserInfoResponse>(
				"https://$server/api/userInfo",
				UserInfoRequest(username),
			).mapCatching { resp ->
				UserInfo(resp.id, PublicSigningKey.fromString(resp.key).getOrThrow())
			}
		}
	}

	suspend fun fetchPreKeys(contacts: List<SendChan>): Result<
		List<Pair<SendChan, SignedPublicDHKey?>>
	> {
		return withContext(Dispatchers.IO) {
			data class FetchPreKeysRequest(
				val ids: List<String>,
			)

			data class FetchPreKeysResponse(
				val prekeys: List<String>,
			)

			restAPI<FetchPreKeysRequest, FetchPreKeysResponse>(
				"https://$server/api/fetchPrekeys",
				FetchPreKeysRequest(contacts.map { contact -> contact.id }),
			).mapCatching { resp ->
				contacts.zip(resp.prekeys).map { (contact, keySig) ->
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

	suspend fun addPreKeys() {
		preKeyStore.generate { newKeys ->
			data class AddPreKeysRequest(
				val prekeys: List<String>,
			)

			data class AddPreKeysResponse(
				val live_prekeys: List<String>,
			)

			val mySigningKeys = profileStore.signingKeys.value.keyPair
				?: return@generate Result.failure(Exception("No signing keys. Cannot sign prekeys."))

			restAPI<AddPreKeysRequest, AddPreKeysResponse>(
				"https://$server/api/addPrekeys",
				AddPreKeysRequest(newKeys.map { newKey ->
					val signedKey = newKey.signBy(mySigningKeys.private)
					"${signedKey.key.toString()},${signedKey.signature.toString()}"
				}),
			).mapCatching { resp ->
				resp.live_prekeys
			}
		}
	}

	suspend fun sendLoc(
		loc: Location,
		contacts: List<SendChan>,
		forceKeyResend: Boolean = false,
	): Result<NeedPrekeys> {
		if (contacts.isEmpty()) {
			return Result.success(NeedPrekeys(false))
		}

		return withContext(Dispatchers.IO) {
			data class Message(
				val to: String,
				val type: String,
				val msg: String,
			)

			data class SendRequest(
				val items: List<Message>,
			)

			data class SendResponse(
				val needPrekeys: Boolean,
			)

			val items = mutableListOf<Message>()

			val lastKeyResend = settingsStore.lastKeyResend.value
			val nextKeyResend = lastKeyResend + KEY_RESEND_SEC
			val now = monotonicSeconds()
			Log.d("Locodile", "ServerInterface.sendLoc: now=$now")
			Log.d("Locodile", "ServerInterface.sendLoc: lastKeyResend=$lastKeyResend")
			Log.d("Locodile", "ServerInterface.sendLoc: nextKeyResend=$nextKeyResend")
			Log.d("Locodile", "ServerInterface.sendLoc: forceKeyResend=$forceKeyResend")
			Log.d("Locodile", "ServerInterface.sendLoc: contacts=$contacts")
			if (forceKeyResend || now !in lastKeyResend until nextKeyResend) {
				settingsStore.lastKeyResend.value = now

				// Try to change keys if the time has come
				val toChange = contacts.filter { contact -> contact.shouldChangeKeys(now) }
				if (toChange.isNotEmpty()) {
					fetchPreKeys(toChange).fold(
						onSuccess = { newKeys ->
							for ((contact, theirNewKey) in newKeys) {
								// Note that we want to call `changeKeys()` even
								// if `theirNewKey` is null, because it will handle
								// possible expiration of the old keys.
								contact.changeKeys(now, theirNewKey)
							}
						},
						onFailure = { e ->
							return@withContext Result.failure(e)
						}
					)
				}
				// Send message with the current keys
				for (contact in contacts) {
					Log.d("Locodile", "ServerInterface.sendLoc: sending keys for contact ${contact.id}")
					val (myKey, theirKey) = contact.usedKeys() ?: continue
					val myKeyStr = myKey.key.toString()
					val mySigStr = myKey.signature.toString()
					val theirKeyStr = theirKey.toString()
					Log.d("Locodile", "ServerInterface.sendLoc: 1: items.count=${items.count()}")
					items.add(Message(contact.id, "k", "$myKeyStr,$mySigStr,$theirKeyStr"))
					Log.d("Locodile", "ServerInterface.sendLoc: 2: items.count=${items.count()}")
				}
			}

			val msg = loc.latitude.toString() + "," + loc.longitude.toString()
			for (contact in contacts) {
				Log.d("Locodile", "ServerInterface.sendLoc: sending msg for contact ${contact.id}")
				contact.encrypt(msg).onSuccess { encrypted ->
					Log.d("Locodile", "ServerInterface.sendLoc: 1: items.count=${items.count()}")
					items.add(Message(contact.id, "m", "${Base64.encode(encrypted)}"))
					Log.d("Locodile", "ServerInterface.sendLoc: 2: items.count=${items.count()}")
				}
			}

			Log.d("Locodile", "ServerInterface.sendLoc: items.count=${items.count()}")
			Log.d("Locodile", "ServerInterface.sendLoc: items=$items")

			restAPI<SendRequest, SendResponse>(
				"https://$server/api/send",
				SendRequest(items)
			).mapCatching { resp ->
				NeedPrekeys(resp.needPrekeys)
			}
		}
	}

	suspend fun recv(contacts: Map<String, RecvChan>): Result<Pair<List<Message>, NeedPrekeys>> {
		if (contacts.isEmpty()) {
			return Result.success(Pair(emptyList(), NeedPrekeys(false)))
		}

		Log.d("Locodile", "ServerInterface.recv: before withContext")
		return withContext(Dispatchers.IO) {
			class RecvRequest {}

			data class RecvResponseItem(
				val ageSec: Long,
				val from: String,
				val type: String,
				val msg: String,
			)

			data class RecvResponse(
				val items: List<RecvResponseItem>,
				val needPrekeys: Boolean,
			)

			val now = monotonicSeconds()
			Log.d("Locodile", "ServerInterface.recv: before restAPI call")
			restAPI<RecvRequest, RecvResponse>(
				"https://$server/api/recv",
				RecvRequest()
			).mapCatching { resp ->
				if (resp.items.isNotEmpty()) {
					Log.d("Locodile", "++++++++++++++++++++++++++++++++++++++++++++ ServerInterface.recv: resp.items.count=${resp.items.count()}")
				}
				val messages = mutableListOf<Message>()
				for (msg in resp.items) {
					try {
						val contact = contacts[msg.from] ?: continue
						if (msg.type == "k") {
							Log.d("Locodile", "ServerInterface.recv: k")
							val keys = msg.msg.split(",")
							if (keys.size != 3) {
								Log.d("Locodile", "ServerInterface.recv: invalid keys")
								continue
							}
							val theirKey = PublicDHKey.fromString(keys[0]).getOrThrow()
							val theirSig = Signature.fromString(keys[1]).getOrThrow()
							val myKey = PublicDHKey.fromString(keys[2]).getOrThrow()
							contact.switchKeys(now, myKey, SignedPublicDHKey(theirKey, theirSig))
						} else if (msg.type == "m") {
							Log.d("Locodile", "ServerInterface.recv: m")
							val encrypted = Base64.decode(msg.msg)
							val decrypted = contact.decrypt(encrypted).getOrThrow()
							val parts = decrypted.split(",")
							if (parts.size != 2) {
								Log.d("Locodile", "ServerInterface.recv: invalid loc")
								continue
							}
							val timestamp = now - msg.ageSec
							val lat = parts[0].toDouble()
							val lon = parts[1].toDouble()
							messages.add(Message(msg.from, timestamp, lat, lon))
						}
					} catch (e: Exception) {
						Log.d("Locodile", "ServerInterface.recv: e=$e")
					}
				}
				Pair(messages, NeedPrekeys(resp.needPrekeys))
			}
		}
	}
}
