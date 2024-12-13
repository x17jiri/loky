package com.x17jiri.Loky.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.x17jiri.Loky.Credentials
import com.x17jiri.Loky.LocationService
import com.x17jiri.Loky.ProfileStore
import com.x17jiri.Loky.ProfileStoreMock
import com.x17jiri.Loky.ServerInterface
import com.x17jiri.Loky.ServerInterfaceMock
import com.x17jiri.Loky.ui.theme.X17LokyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun LoginScreen(
	navController: NavController,
	profileStore: ProfileStore,
	server: ServerInterface,
) {
	val cred = profileStore.cred.value
	var username by remember { mutableStateOf(cred.username) }
	var passwd by remember { mutableStateOf(cred.passwd) }
	var errMessage by remember { mutableStateOf("") }
	var working by remember { mutableStateOf(false) }
	val coroutineScope = rememberCoroutineScope()
	Column(
		modifier = Modifier.fillMaxSize()
	) {
		Spacer(modifier = Modifier.height(20.dp))
		Box(
			modifier = Modifier.fillMaxWidth(),
			contentAlignment = Alignment.Center,
		) {
			Text("Welcome!")
		}
		Spacer(modifier = Modifier.height(20.dp))
		TextField(
			value = username,
			onValueChange = { username = it },
			label = { Text("Username") },
			modifier = Modifier
				.fillMaxWidth()
				.padding(10.dp)
		)
		var passwordVisible by remember { mutableStateOf(false) }
		TextField(
			value = passwd,
			onValueChange = { passwd = it },
			label = { Text("Password") },
			modifier = Modifier
				.fillMaxWidth()
				.padding(10.dp),
			visualTransformation =
			if (passwordVisible) {
				VisualTransformation.None
			} else {
				PasswordVisualTransformation()
			},
			trailingIcon = {
				val image =
					if (passwordVisible) {
						Icons.Filled.Visibility
					} else {
						Icons.Filled.VisibilityOff
					}
				val description = if (passwordVisible) "Hide password" else "Show password"
				IconButton(onClick = { passwordVisible = !passwordVisible }) {
					Icon(imageVector = image, contentDescription = description)
				}
			}
		)
		Spacer(modifier = Modifier.height(20.dp))
		Button(
			onClick = {
				working = true
				coroutineScope.launch(Dispatchers.IO) {
					server.login(username, passwd).fold(
						onSuccess = { needPrekeys ->
							if (needPrekeys.value) {
								server.addPreKeys()
							}
							withContext(Dispatchers.Main) {
								navController.navigate("map") {
									popUpTo("login") { inclusive = true }
								}
							}
						},
						onFailure = { e ->
							working = false
							errMessage = e.toString()
						}
					)
				}
			},
			enabled = username != "" && passwd != "",
			content = { Text("Login") },
			modifier = Modifier
				.fillMaxWidth()
				.padding(10.dp)
		)
		Box(
			modifier = Modifier.fillMaxWidth().padding(10.dp),
			contentAlignment = Alignment.Center // Center the text inside the Box
		) {
			Text("or")
		}
		Box(
			modifier = Modifier.fillMaxWidth().padding(10.dp),
			contentAlignment = Alignment.Center // Center the text inside the Box
		) {
			HyperlinkButton(
				text = "Register",
				onClick = {
					navController.navigate("reg")
				}
			)
		}
		if (errMessage != "") {
			MessageDialog(
				errMessage,
				onDismiss = { errMessage = "" }
			)
		} else if (working) {
			InfoDialg("Working...")
		}
	}
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
	X17LokyTheme {
		val navController = rememberNavController()
		val profileStore = ProfileStoreMock()
		profileStore.launchEdit { dao ->
			dao.setCred(Credentials("jiri", "123"))
		}
		LoginScreen(navController, profileStore, ServerInterfaceMock())
	}
}
