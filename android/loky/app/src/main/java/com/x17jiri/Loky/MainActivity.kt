package com.x17jiri.Loky

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorSpace.Rgb
import android.graphics.Paint.Align
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.x17jiri.Loky.ui.theme.X17LokyTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mapbox.android.core.permissions.PermissionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity: ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			X17LokyTheme {
				NavigationGraph(this, lifecycleScope)
			}
		}
	}
}

@Composable
fun NavigationGraph(context: Context, scope: CoroutineScope) {
	val model: MainViewModel = viewModel(factory = MainViewModelFactory(context))
	val navController = rememberNavController()
	NavHost(navController = navController, startDestination = "loading") {
		composable("loading")  { LoadingScreen(navController, model, scope) }
		composable("login/{message}") {
			var msg = it.arguments?.getString("message") ?: ""
			msg = URLDecoder.decode(msg, StandardCharsets.UTF_8.toString())
			LoginScreen(context, model, navController, msg)
		}
		composable("map") { MapView(navController, model, context) }
		composable("contacts") { Contacts(navController, model, scope) }
	}
}

@Composable
fun LoadingScreen(navController: NavController, model: MainViewModel, scope: CoroutineScope) {
	Box(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center
	) {
		Text("Loading...", fontSize = 24.sp)
		LaunchedEffect(Unit) {
			val cred = model.credMan.credentials.value
			if (cred.user.isNotEmpty() && cred.passwd.isNotEmpty()) {
				scope.launch(Dispatchers.IO) {
					model.server.login(cred).fold(
						onSuccess = {
							Log.d("Locodile", "storing credentials: ${it}")
							model.credMan.credentials.value = it
							withContext(Dispatchers.Main) {
								navController.navigate("map") {
									popUpTo(navController.graph.startDestinationId) { inclusive = true }
								}
							}
						},
						onFailure = {
							Log.d("Locodile", "LoadingScreen: login failed: ${it.message}")
							withContext(Dispatchers.Main) {
								var msg = it.toString()
								msg = URLEncoder.encode(msg, StandardCharsets.UTF_8.toString())
								navController.navigate("login/${msg}")
							}
						}
					)
				}
			} else {
				Log.d("Locodile", "LoadingScreen: LaunchedEffect.5")
				navController.navigate("login/")
			}
		}
	}
}

@Composable
fun LoginScreen(context: Context, model: MainViewModel, navController: NavController, message: String) {
	val cred by model.credMan.credentials.collectAsState()
	var failedDialog by remember { mutableStateOf(message) }
	Column(
		modifier = Modifier.fillMaxSize(),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.weight(0.6f),
			verticalArrangement = Arrangement.Center,
		) {
			TextField(
				value = cred.user,
				onValueChange = { model.credMan.credentials.value = Credentials(it, cred.passwd) },
				label = { Text("Username") },
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp)
			)
			TextField(
				value = cred.passwd,
				onValueChange = { model.credMan.credentials.value = Credentials(cred.user, it) },
				label = { Text("Password") },
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp),
				visualTransformation = PasswordVisualTransformation()
			)
			Button(
				onClick = {
					navController.navigate("loading") {
						popUpTo(navController.graph.startDestinationId) { inclusive = true }
					}
				},
				enabled = cred.user != "" && cred.passwd != "",
				content = { Text("Login") },
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp)
			)
			if (failedDialog != "") {
				MessageDialog(
					failedDialog,
					onDismiss = { failedDialog = "" }
				)
			}
		}
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(0.4f),
		)
	}
}

@Composable
fun MapView(navController: NavController, model: MainViewModel, context: Context) {
	DisposableEffect(Unit) {
		model.receiver.startReceiving()
		onDispose {
			model.receiver.stopReceiving()
		}
	}

	Scaffold(
		modifier = Modifier
			.fillMaxSize()
			.statusBarsPadding().navigationBarsPadding(),
		topBar = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.fillMaxWidth()
			) {
				Box {
					val isServiceRunning by model.isLocationServiceRunning.collectAsState()
					Switch(
						checked = isServiceRunning,
						onCheckedChange = {
							if (it) {
								model.requestIgnoreBatteryOptimization()
								model.startLocationService()
							} else {
								model.stopLocationService()
							}
						},
						modifier = Modifier.padding(start = 10.dp, end = 10.dp),
					)
				}
				Box(modifier = Modifier.weight(1.0f)) {
					Text("Share location")
				}
				Box {
					IconButton(onClick = { navController.navigate("contacts") }) {
						Icon(
							imageVector = Icons.Default.Settings,
							contentDescription = "Settings",
						)
					}
				}
			}
		}
	) { innerPadding ->
		Column(Modifier.padding(innerPadding)) {
			Box(
				modifier = Modifier
					.weight(1.0f)
					.fillMaxWidth()
			) {
				val data by model.receiver.data.collectAsState()
				var mapViewportState = rememberMapViewportState {}
				MapboxMap(
					Modifier.fillMaxSize(),
					mapViewportState = mapViewportState,
				) {
					for ((k, v) in data) {
						PolylineAnnotation(
							points = v.map { Point.fromLngLat(it.lon, it.lat) }
						) {
							lineColor = Color(0xffee4e8b)
							lineWidth = 5.0
						}
					}
					
					MapEffect(Unit) { mapView ->
						mapView.location.updateSettings {
							locationPuck = createDefault2DPuck(withBearing = true)
							enabled = true
							puckBearing = PuckBearing.COURSE
							puckBearingEnabled = true
						}
						mapViewportState.transitionToFollowPuckState()
					}
				}
			}
		}
	}
}

@Composable
fun SettingsScreen(
	name: String,
	navController: NavController,
	block: @Composable () -> Unit
) {
	Scaffold(
		modifier = Modifier
			.fillMaxSize()
			.statusBarsPadding().navigationBarsPadding(),
		topBar = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.fillMaxWidth()
			) {
				IconButton(onClick = { navController.popBackStack() }) {
					Icon(
						imageVector = Icons.Default.ArrowBack,
						contentDescription = "Back"
					)
				}
				Text(name)
			}
		},
		//floatingActionButton = floatingActionButton,
	) { innerPadding ->
		Box(modifier = Modifier.padding(innerPadding)) {
			block()
		}
	}
}

@Composable
fun ConfirmDialog(
	text: String,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit
) {
	Dialog(onDismissRequest = onDismiss) {
		Surface {
			Column(
				modifier = Modifier.padding(20.dp).fillMaxWidth()
			) {
				Text(text)
				Spacer(modifier = Modifier.height(20.dp))
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End
				) {
					TextButton(onClick = onDismiss) {
						Text("No")
					}
					Spacer(modifier = Modifier.width(10.dp))
					TextButton(
						onClick = {
							onConfirm()
							onDismiss()
						}
					) {
						Text("Yes")
					}
				}
			}
		}
	}
}

@Composable
fun MessageDialog(
	text: String,
	onDismiss: () -> Unit,
) {
	Dialog(onDismissRequest = onDismiss) {
		Surface {
			Column(
				modifier = Modifier.padding(20.dp).fillMaxWidth()
			) {
				Text(text)
				Spacer(modifier = Modifier.height(20.dp))
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End
				) {
					TextButton(onClick = onDismiss) {
						Text("Ok")
					}
				}
			}
		}
	}
}

@Composable
fun AddContactDialog(
	onDismiss: () -> Unit,
	onConfirm: (String) -> Unit
) {
	var name by remember { mutableStateOf("") }
	Dialog(onDismissRequest = onDismiss) {
		Surface {
			Column(
				modifier = Modifier.padding(20.dp).fillMaxWidth()
			) {
				Text("Add contact")
				Spacer(modifier = Modifier.height(20.dp))
				TextField(
					value = name,
					onValueChange = { name = it },
					label = { Text("User Name") },
					modifier = Modifier
						.fillMaxWidth()
						.padding(10.dp)
				)
				Spacer(modifier = Modifier.height(20.dp))
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End
				) {
					TextButton(onClick = onDismiss) {
						Text("Cancel")
					}
					Spacer(modifier = Modifier.width(10.dp))
					TextButton(
						enabled = name != "",
						onClick = { onConfirm(name) }
					) {
						Text("Add")
					}
				}
			}
		}
	}
}

@Composable
fun InfoDialg(text: String) {
	Dialog(onDismissRequest = { }) {
		Surface {
			Column(
				modifier = Modifier.padding(20.dp).fillMaxWidth()
			) {
				Text(text)
			}
		}
	}
}

enum class AddContactState {
	Hidden,
	TextInput,
	Checking,
}

@Composable
fun Contacts(navController: NavController, model: MainViewModel, scope: CoroutineScope) {
	SettingsScreen("Contacts", navController) {
		val contacts by model.contactsMan.contacts.collectAsState()
		var itemToDel by remember { mutableStateOf<Long?>(null) }
		var addContactState by remember { mutableStateOf(AddContactState.Hidden) }
		Box(modifier = Modifier.fillMaxSize()) {
			LazyColumn(
				modifier = Modifier.fillMaxWidth()
			) {
				items(contacts.size) { __i ->
					val id = contacts[__i].id
					val name = contacts[__i].name
					val send = contacts[__i].send
					val recv = contacts[__i].recv
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier
							.fillMaxWidth()
							.padding(10.dp)
					) {
						Column(
							horizontalAlignment = Alignment.CenterHorizontally
						) {
							Text(
								text = "Send",
								style = TextStyle(fontSize = 8.sp)
							)
							Switch(
								checked = send,
								onCheckedChange = { model.contactsMan.setSend(id, it) },
								colors = SwitchDefaults.colors(
									checkedTrackColor = Color(0.75f, 0.5f, 0.5f),
								),
								modifier = Modifier.padding(start = 10.dp, end = 10.dp),
							)
						}
						Column(
							horizontalAlignment = Alignment.CenterHorizontally
						) {
							Text(
								text = "Receive",
								style = TextStyle(fontSize = 8.sp)
							)
							Switch(
								checked = recv,
								onCheckedChange = { model.contactsMan.setRecv(id, it) },
								colors = SwitchDefaults.colors(
									checkedTrackColor = Color(0.5f, 0.75f, 0.5f),
								),
								modifier = Modifier.padding(start = 10.dp, end = 10.dp),
							)
						}
						Spacer(modifier = Modifier.width(10.dp))
						Box(
							contentAlignment = Alignment.CenterStart,
							modifier = Modifier.weight(1.0f)
						) {
							Text(text = name)
						}
						Spacer(modifier = Modifier.width(20.dp))
						IconButton(
							onClick = { itemToDel = id }
						) {
							Icon(
								Icons.Filled.Delete,
								contentDescription = "Delete Item"
							)
						}
					}
				}
			}
			var failedDialog by remember { mutableStateOf("") }
			FloatingActionButton(
				onClick = { addContactState = AddContactState.TextInput },
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(20.dp),
			) {
				Icon(
					Icons.Filled.Add,
					contentDescription = "Add"
				)
			}
			if (itemToDel != null) {
				val item = contacts.find { it.id == itemToDel }
				if (item == null) {
					itemToDel = null
				} else {
					val id: Long = item.id
					ConfirmDialog(
						"Delete ${item.name}?",
						onDismiss = { itemToDel = null; },
						onConfirm = { model.contactsMan.remove(id) }
					)
				}
			}
			when (addContactState) {
				AddContactState.TextInput -> {
					AddContactDialog(
						onDismiss = { addContactState = AddContactState.Hidden },
						onConfirm = {
							val userName = it
							addContactState = AddContactState.Checking
							scope.launch(Dispatchers.IO) {
								model.server.userInfo(it).fold(
									onSuccess = {
										val id = it
										model.contactsMan.add(id, userName)
										withContext(Dispatchers.Main) {
											addContactState = AddContactState.Hidden
										}
									},
									onFailure = {
										withContext(Dispatchers.Main) {
											failedDialog = "Username not found"
											addContactState = AddContactState.Hidden
										}
									}
								)
							}
						}
					)
				}
				AddContactState.Checking -> {
					InfoDialg("Checking...")
				}
				AddContactState.Hidden -> {}
			}
			if (failedDialog != "") {
				MessageDialog(
					failedDialog,
					onDismiss = { failedDialog = "" }
				)
			}
		}
	}
}
/*
@Preview(showBackground = true)
@Composable
fun NewUserDialogPreview() {
	X17LokyTheme {
		AddContactDialog({}, { _, _ -> })
	}
}*/
/*
@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
	X17LokyTheme {
		val navController = rememberNavController()
		LoginScreen(navController)
	}
}
*/
