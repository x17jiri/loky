package com.x17jiri.Loky

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.x17jiri.Loky.ui.theme.X17LokyTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.TextJustify
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

class MainActivity: ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			X17LokyTheme {
				NavigationGraph()
			}
		}
	}
}

@Composable
fun NavigationGraph() {
	val context = LocalContext.current
	val model: MainViewModel = viewModel(factory = MainViewModelFactory(context))
	val navController = rememberNavController()
	NavHost(navController = navController, startDestination = "loading") {
		composable("loading")  {
			LoadingScreen(navController, model)
		}
		composable("login/{message}") { navStackEntry ->
			var msg = navStackEntry.arguments?.getString("message") ?: ""
			msg = URLDecoder.decode(msg, StandardCharsets.UTF_8.toString())
			LoginScreen(navController, model.profileStore, msg)
		}
		composable("map") {
			MapView(navController, model)
		}
		composable("contacts") {
			Contacts(navController, model)
		}
		composable("myprofile") {
			MyProfile(navController)
		}
		composable("about") {
			AboutScreen(navController)
		}
	}
}

@Composable
fun LoadingScreen(navController: NavController, model: MainViewModel) {
	val context = LocalContext.current
	Scaffold(
		modifier = Modifier
			.fillMaxSize()
			.statusBarsPadding()
			.navigationBarsPadding(),
		topBar = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.fillMaxWidth()
			) {
				Box {
					val isServiceRunning by LocationService.isRunning.collectAsState()
					Switch(
						enabled = isServiceRunning,
						checked = isServiceRunning,
						onCheckedChange = {
							LocationService.stop(context)
						},
						modifier = Modifier.padding(start = 10.dp, end = 10.dp),
					)
				}
				Box(modifier = Modifier.weight(1.0f)) {
					Text("Share location")
				}
			}
		}
	) { innerPadding ->
		Box(
			modifier = Modifier
					.fillMaxSize()
					.padding(innerPadding),
			contentAlignment = Alignment.Center
		) {
			Text("Loading...", fontSize = 24.sp)
		}
	}
	LaunchedEffect(Unit) {
		context.init_singletons()
		val cred = model.profileStore.cred.value
		if (cred.username.isNotEmpty() && cred.passwd.isNotEmpty()) {
			withContext(Dispatchers.IO) {
				model.server.login().fold(
					onSuccess = { needPrekeys ->
						if (needPrekeys.value) {
							model.server.addPreKeys()
						}
						withContext(Dispatchers.Main) {
							navController.navigate("map") {
								popUpTo("loading") { inclusive = true }
							}
						}
					},
					onFailure = { e ->
						withContext(Dispatchers.Main) {
							val msg = URLEncoder.encode(e.toString(), StandardCharsets.UTF_8.toString())
							navController.navigate("login/${msg}") {
								popUpTo("loading") { inclusive = true }
							}
						}
					}
				)
			}
		} else {
			navController.navigate("login/") {
				popUpTo("loading") { inclusive = true }
			}
		}
	}
}



@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun LoginScreen(navController: NavController, profileStore: ProfileStore, message: String) {
	val cred = profileStore.cred.value
	var username by remember { mutableStateOf(cred.username) }
	var passwd by remember { mutableStateOf(cred.passwd) }
	var failedDialog by remember { mutableStateOf(message) }
	val context = LocalContext.current
	Scaffold(
		modifier = Modifier
			.fillMaxSize()
			.statusBarsPadding()
			.navigationBarsPadding(),
		topBar = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.fillMaxWidth()
			) {
				Box {
					val isServiceRunning by LocationService.isRunning.collectAsState()
					Switch(
						enabled = isServiceRunning,
						checked = isServiceRunning,
						onCheckedChange = {
							LocationService.stop(context)
						},
						modifier = Modifier.padding(start = 10.dp, end = 10.dp),
					)
				}
				Box(modifier = Modifier.weight(1.0f)) {
					Text("Share location")
				}
			}
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.weight(0.6f),
				verticalArrangement = Arrangement.Center,
			) {
				TextField(
					value = username,
					onValueChange = { newUsername ->
						username = newUsername
						profileStore.launchEdit { dao ->
							dao.setCred(Credentials(newUsername, passwd))
						}
					},
					label = { Text("Username") },
					modifier = Modifier
						.fillMaxWidth()
						.padding(10.dp)
				)
				var passwordVisible by remember { mutableStateOf(false) }
				TextField(
					value = passwd,
					onValueChange = { newPasswd ->
						passwd = newPasswd
						profileStore.launchEdit { dao ->
							dao.setCred(Credentials(username, newPasswd))
						}
					},
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
				Button(
					onClick = {
						navController.navigate("loading") {
							popUpTo("login/") { inclusive = true }
						}
					},
					enabled = username != "" && passwd != "",
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
}

fun prettyAge(_sec: Long): String {
	var sec = _sec
	if (sec < 60) {
		return "${sec} sec"
	}
	var min = sec / 60
	sec %= 60
	if (min < 60) {
		return "${min}:${sec.toString().padStart(2, '0')} min"
	}
	if (sec >= 30) {
		min += 1
	}
	val hour = min / 60
	min %= 60
	return "${hour}:${min.toString().padStart(2, '0')} hour"
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun MapView(navController: NavController, model: MainViewModel) {
	LifecycleResumeEffect(Unit) {
		model.receiver.start()
		onPauseOrDispose {
			model.receiver.stop()
		}
	}

	var showSettings by remember { mutableStateOf(false) }
	val context = LocalContext.current

	Scaffold(
		modifier = Modifier
			.fillMaxSize()
			.statusBarsPadding()
			.navigationBarsPadding(),
		topBar = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.fillMaxWidth()
			) {
				Box {
					val isServiceRunning by LocationService.isRunning.collectAsState()
					Switch(
						checked = isServiceRunning,
						onCheckedChange = { isOn ->
							if (isOn) {
								//model.requestIgnoreBatteryOptimization()
								LocationService.start(context)
							} else {
								LocationService.stop(context)
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
							imageVector = Icons.Default.Contacts,
							contentDescription = "Contacts",
						)
					}
				}
				Box {
					IconButton(onClick = { showSettings = true }) {
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
				val contactsFlow = model.contactsStore.flow().map { list ->
					list.filter { contact -> contact.recv }
				}
				val contacts by contactsFlow.collectAsState(emptyList())
				val dataWithHeartbeat by model.receiver.dataWithHeartbeat.collectAsState()
				val mapViewportState = rememberMapViewportState {}
				MapboxMap(
					Modifier.fillMaxSize(),
					mapViewportState = mapViewportState,
				) {
					val now = dataWithHeartbeat.first
					val data = dataWithHeartbeat.second
					for (contact in contacts) {
						val values = data[contact.id]
						if (values.isNullOrEmpty()) {
							continue
						}
						PolylineAnnotation(
							points = values.map { Point.fromLngLat(it.lon, it.lat) }
						) {
							lineColor = Color(0xffee4e8b)
							lineWidth = 5.0
						}

						val lastMsg = values.last()
						val age = now - lastMsg.timestamp
						val marker: IconImage = rememberIconImage(R.drawable.red_marker)
						PointAnnotation(
							point = Point.fromLngLat(lastMsg.lon, lastMsg.lat),
						) {
							iconImage = marker
							iconSize = 1.5
							iconOffset = listOf(0.0, -16.0)
//							iconAnchor = IconAnchor.BOTTOM
							textField = "${contact.name}\n${prettyAge(age)} ago"
							textOffset = listOf(1.5, -3.5)
							textJustify = TextJustify.LEFT
							textAnchor = TextAnchor.TOP_LEFT
							textColor = Color.Black
							textHaloColor = Color.White
							textHaloWidth = 2.0
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
		if (showSettings) {
			val shareFreq = model.settings.shareFreq.value
			Settings(
				navController,
				model,
				onDismiss = {
					val newShareFreq = model.settings.shareFreq.value
					if (LocationService.isRunning.value && newShareFreq.ms != shareFreq.ms) {
						LocationService.stop(context)
						LocationService.start(context)
					}
					showSettings = false
				}
			)
		}
	}
}

@Composable
fun ScreenHeader(
	name: String,
	navController: NavController,
	block: @Composable () -> Unit
) {
	Scaffold(
		modifier = Modifier
			.fillMaxSize()
			.statusBarsPadding()
			.navigationBarsPadding(),
		topBar = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.fillMaxWidth()
			) {
				IconButton(onClick = { navController.popBackStack() }) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.ArrowBack,
						contentDescription = "Back"
					)
				}
				Text(
					name,
					style = MaterialTheme.typography.bodyLarge.copy(
						fontWeight = FontWeight.Bold,
						fontSize = 24.sp,
					)
				)
			}
		},
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
				modifier = Modifier
					.padding(20.dp)
					.fillMaxWidth()
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
				modifier = Modifier
					.padding(20.dp)
					.fillMaxWidth()
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
				modifier = Modifier
					.padding(20.dp)
					.fillMaxWidth()
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
				modifier = Modifier
					.padding(20.dp)
					.fillMaxWidth()
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
fun Contacts(navController: NavController, model: MainViewModel) {
	val scope = rememberCoroutineScope()
	ScreenHeader("Contacts", navController) {
		val contacts by model.contactsStore.flow().collectAsState(emptyList())
		var itemToDel by remember { mutableStateOf<Contact?>(null) }
		var addContactState by remember { mutableStateOf(AddContactState.Hidden) }
		Box(modifier = Modifier.fillMaxSize()) {
			LazyColumn(
				modifier = Modifier.fillMaxWidth()
			) {
				items(contacts.size) { __i ->
					val contact = contacts[__i]
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier
							.clickable {}
							.fillMaxWidth()
							.padding(10.dp)
					) {
						Column(
							horizontalAlignment = Alignment.CenterHorizontally
						) {
							Text(
								text = "Share",
								style = TextStyle(fontSize = 8.sp)
							)
							Switch(
								checked = contact.send,
								onCheckedChange = { value ->
									model.contactsStore.launchEdit { store ->
										store.setSend(contact, value)
									}
								},
								colors = SwitchDefaults.colors(
									checkedTrackColor = Color(0.75f, 0.5f, 0.5f),
								),
								modifier = Modifier.padding(start = 10.dp, end = 10.dp),
							)
						}
//						Column(
//							horizontalAlignment = Alignment.CenterHorizontally
//						) {
//							Text(
//								text = "Receive",
//								style = TextStyle(fontSize = 8.sp)
//							)
//							Switch(
//								checked = contact.recv,
//								onCheckedChange = { value ->
//									model.contactsStore.launchEdit { store ->
//										store.setRecv(contact, value)
//									}
//								},
//								colors = SwitchDefaults.colors(
//									checkedTrackColor = Color(0.5f, 0.75f, 0.5f),
//								),
//								modifier = Modifier.padding(start = 10.dp, end = 10.dp),
//							)
//						}
						Spacer(modifier = Modifier.width(10.dp))
						Box(
							contentAlignment = Alignment.CenterStart,
							modifier = Modifier.weight(1.0f)
						) {
							Text(text = contact.name)
						}
						Spacer(modifier = Modifier.width(20.dp))
						IconButton(
							onClick = { itemToDel = contact }
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
			val __itemToDel = itemToDel
			if (__itemToDel != null) {
				ConfirmDialog(
					"Delete ${__itemToDel.name}?",
					onDismiss = { itemToDel = null; },
					onConfirm = {
						model.contactsStore.launchEdit { store ->
							store.delete(__itemToDel)
						}
					}
				)
			}
			when (addContactState) {
				AddContactState.TextInput -> {
					AddContactDialog(
						onDismiss = { addContactState = AddContactState.Hidden },
						onConfirm = { userName ->
							addContactState = AddContactState.Checking
							scope.launch(Dispatchers.IO) {
								model.server.userInfo(userName).fold(
									onSuccess = { userInfo ->
										model.contactsStore.launchEdit { dao ->
											dao.insert(
												Contact(
													id = userInfo.id,
													name = userName,
													signKey = userInfo.signKey,
													masterKey = userInfo.masterKey,
													send = false,
													recv = true,
												)
											)
										}
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

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun Settings(
	navController: NavController,
	model: MainViewModel,
	onDismiss: () -> Unit,
) {
	Dialog(onDismissRequest = onDismiss) {
		Surface(
			shape = MaterialTheme.shapes.medium,
			modifier = Modifier
				.fillMaxWidth()
				.wrapContentHeight()
		) {
			Column(
				modifier = Modifier
					.padding(20.dp)
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.fillMaxWidth()
				) {
					Text(
						"Settings",
						style = MaterialTheme.typography.bodyLarge.copy(
							fontWeight = FontWeight.Bold,
							fontSize = 24.sp
						),
						modifier = Modifier.weight(1.0f)
					)
					// "x" icon to close the dialog
					IconButton(
						onClick = onDismiss,
					) {
						Icon(
							Icons.Filled.Close,
							contentDescription = "Close"
						)
					}
				}
				Spacer(modifier = Modifier.height(20.dp))

				Column(
					modifier = Modifier
						.padding(10.dp)
						.fillMaxWidth()
				) {
					Text(
						"Location Sharing Frequency",
						style = MaterialTheme.typography.bodyLarge.copy(
							fontWeight = FontWeight.Bold,
						),
					)
					val shareFreqValues = listOf(
						Pair(5.0, "5 seconds"),
						Pair(15.0, "15 seconds"),
						Pair(30.0, "30 seconds"),
						Pair(60.0, "1 minute"),
						Pair(120.0, "2 minutes"),
						Pair(180.0, "3 minutes"),
					)
					val shareFreq = model.settings.shareFreq.value.seconds
					var closestIndex = 0
					var closestDistance = (shareFreq - shareFreqValues[0].first) * (shareFreq - shareFreqValues[0].first)
					for (i in 1 until shareFreqValues.size) {
						val distance = (shareFreq - shareFreqValues[i].first) * (shareFreq - shareFreqValues[i].first)
						if (distance < closestDistance) {
							closestIndex = i
							closestDistance = distance
						}
					}
					var sliderValue by remember { mutableFloatStateOf(closestIndex.toFloat())}
					var textValue by remember { mutableStateOf(shareFreqValues[closestIndex].second) }
					Slider(
						value = sliderValue,
						onValueChange = { newValue ->
							val sec = shareFreqValues[newValue.roundToInt()]
							textValue = sec.second
							model.settings.launchEdit { dao ->
								dao.setShareFreq(SharingFrequency(sec.first))
							}
							sliderValue = newValue
						},
						valueRange = 0.0f..(shareFreqValues.size - 1).toFloat(),
						steps = shareFreqValues.size - 2,
						modifier = Modifier.fillMaxWidth(),
					)
					Text("Every $textValue")
					Spacer(modifier = Modifier.height(5.dp))
					Text("Note: The more frequent the updates, the more battery usage.")
				}
				Spacer(modifier = Modifier.height(5.dp))
				HorizontalDivider()
				Spacer(modifier = Modifier.height(5.dp))

				Column(
					modifier = Modifier
						.clickable {
							navController.navigate("myprofile")
							onDismiss()
						}
						.padding(10.dp)
						.fillMaxWidth(),
				) {
					Text(
						"My Profile",
						style = MaterialTheme.typography.bodyLarge.copy(
							fontWeight = FontWeight.Bold,
						),
					)
					Text("Edit profile")
				}
				Spacer(modifier = Modifier.height(5.dp))
				HorizontalDivider()
				Spacer(modifier = Modifier.height(5.dp))

				Column(
					modifier = Modifier
						.clickable {
							navController.navigate("about")
							onDismiss()
						}
						.padding(10.dp)
						.fillMaxWidth(),
				) {
					Text(
						"About",
						style = MaterialTheme.typography.bodyLarge.copy(
							fontWeight = FontWeight.Bold,
						),
					)
				}
				Spacer(modifier = Modifier.height(10.dp))

			}
		}
	}
}

@Composable
fun VerticalLine3D(
	lightColor: Color = Color.LightGray,
	darkColor: Color = Color.DarkGray,
	thickness: Dp = 4.dp,
	height: Dp = Dp.Unspecified // You can specify a fixed height or fill the parent
) {
	Row(Modifier.height(height)) {
		// Left side simulating light source
		Box(
			modifier = Modifier
				.width(thickness / 2)
				.fillMaxHeight()
				.background(lightColor)
		)

		// Right side simulating shadow
		Box(
			modifier = Modifier
				.width(thickness / 2)
				.fillMaxHeight()
				.background(darkColor)
		)
	}
}

@Composable
fun MyProfile(navController: NavController) {
	ScreenHeader("My Profile", navController) {
		Column(
			modifier = Modifier
				.padding(20.dp)
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
		) {
			Text("TODO: not implemented")
		}
	}
}

@Composable
fun AboutScreen(navController: NavController) {
	ScreenHeader("About", navController) {
		Column(
			modifier = Modifier
				.padding(20.dp)
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
		) {
			Text("x17 Loky version 0.2")
			Text("commit: " + BuildConfig.GIT_COMMIT.take(16))
			Spacer(modifier = Modifier.height(20.dp))
			Text("(C) 2024 Jiri Bobek")
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

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
	X17LokyTheme {
		val navController = rememberNavController()
		val profileStore = ProfileStoreMock()
		profileStore.launchEdit { dao ->
			dao.setCred(Credentials("jiri", "123"))
		}
		LoginScreen(navController, profileStore, "")
	}
}

@Preview(showBackground = true)
@Composable
fun AboutScreenPreview() {
	X17LokyTheme {
		val navController = rememberNavController()
		AboutScreen(navController)
	}
}

