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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.x17jiri.Loky.ui.theme.X17LokyTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.mapbox.android.core.permissions.PermissionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
		composable("loading")  {
			LoadingScreen(navController, model, scope)
		}
		composable("login/{message}") {
			var msg = it.arguments?.getString("message") ?: ""
			msg = URLDecoder.decode(msg, StandardCharsets.UTF_8.toString())
			LoginScreen(navController, model, scope, msg)
		}
		composable("map") {
			MapView(navController, model, scope)
		}
		composable("contacts") {
			Contacts(navController, model, scope)
		}
		composable("myprofile") {
			MyProfile(navController)
		}
		composable("about") {
			About(navController)
		}
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
			model.inboxMan.launchCleanUp()
			val cred = model.profileStore.cred.value
			if (cred.username.isNotEmpty() && cred.passwd.isNotEmpty()) {
				scope.launch(Dispatchers.IO) {
					model.server.login().fold(
						onSuccess = {
							Log.d("Locodile", "storing credentials: ${it}")
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

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun LoginScreen(navController: NavController, model: MainViewModel, scope: CoroutineScope, message: String) {
	val cred = model.profileStore.cred.value
	var username by remember { mutableStateOf(cred.username) }
	var passwd by remember { mutableStateOf(cred.passwd) }
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
				value = username,
				onValueChange = {
					username = it
					model.profileStore.launchEdit {
						it.setCred(Credentials(username, passwd))
					}
				},
				label = { Text("Username") },
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp)
			)
			TextField(
				value = passwd,
				onValueChange = {
					passwd = it
					model.profileStore.launchEdit {
						it.setCred(Credentials(username, passwd))
					}
				},
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

@Composable
fun MapView(navController: NavController, model: MainViewModel, scope: CoroutineScope) {
	DisposableEffect(Unit) {
		model.receiver.start()
		onDispose {
			model.receiver.stop()
		}
	}

	var showSettings by remember { mutableStateOf(false) }

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
					val isServiceRunning by LocationService.isRunning.collectAsState()
					Switch(
						checked = isServiceRunning,
						onCheckedChange = {
							if (it) {
								//model.requestIgnoreBatteryOptimization()
								LocationService.start(model.context)
							} else {
								LocationService.stop(model.context)
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
				val data by model.receiver.data.collectAsState()
				var mapViewportState = rememberMapViewportState {}
				MapboxMap(
					Modifier.fillMaxSize(),
					mapViewportState = mapViewportState,
				) {
					for (contact in contacts) {
						val values = data[contact.id]
						if (values == null || values.isEmpty()) {
							continue
						}
						PolylineAnnotation(
							points = values.map { Point.fromLngLat(it.lon, it.lat) }
						) {
							lineColor = Color(0xffee4e8b)
							lineWidth = 5.0
						}

						val marker: IconImage = rememberIconImage(R.drawable.red_marker)
						PointAnnotation(
							point = Point.fromLngLat(values.last().lon, values.last().lat)
						) {
							iconImage = marker
							iconSize = 1.5
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
				scope,
				onDismiss = {
					val newShareFreq = model.settings.shareFreq.value
					if (LocationService.isRunning.value && newShareFreq.ms != shareFreq.ms) {
						LocationService.stop(model.context)
						LocationService.start(model.context)
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
						imageVector = Icons.Default.ArrowBack,
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
								text = "Send",
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
						Column(
							horizontalAlignment = Alignment.CenterHorizontally
						) {
							Text(
								text = "Receive",
								style = TextStyle(fontSize = 8.sp)
							)
							Switch(
								checked = contact.recv,
								onCheckedChange = { value ->
									model.contactsStore.launchEdit { store ->
										store.setRecv(contact, value)
									}
								},
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
										model.contactsStore.launchEdit { store ->
											store.insert(
												Contact(
													id = userInfo.id,
													name = userName,
													publicSigningKey = userInfo.publicSigningKey,
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
	scope: CoroutineScope,
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
					var sliderValue by remember { mutableStateOf(closestIndex.toFloat())}
					var textValue by remember { mutableStateOf(shareFreqValues[closestIndex].second) }
					Slider(
						value = sliderValue,
						onValueChange = { newValue ->
							val sec = shareFreqValues[newValue.roundToInt()]
							textValue = sec.second
							model.settings.launchEdit {
								it.setShareFreq(SharingFrequency(sec.first))
							}
							sliderValue = newValue
						},
						valueRange = 0.0f..(shareFreqValues.size - 1).toFloat(),
						steps = shareFreqValues.size - 2,
						modifier = Modifier.fillMaxWidth(),
					)
					Text("Every ${textValue}")
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
fun About(navController: NavController) {
	ScreenHeader("About", navController) {
		Column(
			modifier = Modifier
				.padding(20.dp)
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
		) {
			Text("x17 Loky version 0.1")
			Text("commit: #TODO")
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
