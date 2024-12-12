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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.x17jiri.Loky.screens.AboutScreen
import com.x17jiri.Loky.screens.ContactsScreen
import com.x17jiri.Loky.screens.EditContactScreen
import com.x17jiri.Loky.screens.LoginScreen
import com.x17jiri.Loky.screens.MyProfileScreen
import com.x17jiri.Loky.screens.RegisterScreen
import com.x17jiri.Loky.screens.SettingsDialog

class MainActivity: ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		var showSplashScreen = true
		val splashScreen = installSplashScreen()
		splashScreen.setKeepOnScreenCondition { showSplashScreen }

		val context = this
		lifecycleScope.launch {
			context.init_singletons()
			withContext(Dispatchers.Main) {
				setContent {
					X17LokyTheme {
						NavigationGraph()
					}
				}
				showSplashScreen = false
			}
		}
	}
}

@Composable
fun NavigationGraph() {
	val context = LocalContext.current
	val model: MainViewModel = viewModel(factory = MainViewModelFactory(context))
	val navController = rememberNavController()
	val firstScreen =
		if (context.__profileStore.isLoggedIn()) {
			"map"
		} else {
			"login"
		}
	NavHost(navController = navController, startDestination = firstScreen) {
		composable("login") {
			LoginScreen(navController, model.profileStore, model.server)
		}
		composable("reg") {
			RegisterScreen(navController, model.profileStore, model.server)
		}
		composable("map") {
			MapViewScreen(navController, model)
		}
		composable("contacts") {
			ContactsScreen(navController, model.contactsStore, model.receiver, model.server)
		}
		composable("myprofile") {
			MyProfileScreen(navController)
		}
		composable("about") {
			AboutScreen(navController)
		}
		composable(
			"editcontact/{contactID}",
			arguments = listOf(navArgument("contactID") { type = NavType.StringType })
		) { backStackEntry ->
			val contactID = backStackEntry.arguments?.getString("contactID") ?: ""
			EditContactScreen(navController, model.contactsStore, model.iconCache, contactID)
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
fun MapViewScreen(navController: NavController, model: MainViewModel) {
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
		val contactsFlow = model.contactsStore.flow().map { list ->
			list.filter { contact -> contact.recv }
		}
		val contacts by contactsFlow.collectAsState(emptyList())
		val dataWithHeartbeat by model.receiver.dataWithHeartbeat.collectAsState()
		val decryptOk by model.receiver.decryptOk.collectAsState()
		val mapViewportState = rememberMapViewportState {}
		Column(Modifier.padding(innerPadding)) {
			Box(
				modifier = Modifier
					.weight(1.0f)
					.fillMaxWidth()
			) {
				MapboxMap(
					Modifier.fillMaxSize(),
					mapViewportState = mapViewportState,
				) {
					val now = dataWithHeartbeat.time
					val data = dataWithHeartbeat.data
					for (contact in contacts) {
						val values = data[contact.id]
						if (values.isNullOrEmpty()) {
							continue
						}
						PolylineAnnotation(
							points = values.map { Point.fromLngLat(it.lon, it.lat) }
						) {
							lineColor = Color(contact.color)
							lineWidth = 5.0
						}

						val lastMsg = values.last()
						val age = now - lastMsg.timestamp
						val marker = remember(contact.color) { IconImage(model.iconCache.get(contact.color)) }
						PointAnnotation(
							point = Point.fromLngLat(lastMsg.lon, lastMsg.lat),
						) {
							iconImage = marker
							iconSize = 0.6
							iconOffset = listOf(0.0, -33.0)
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
			if (!decryptOk) {
				Spacer(modifier = Modifier.height(1.dp))
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.background(Color.Red)
						.padding(20.dp),
				) {
					Text(
						"Some messages couldn't be decrypted",
						color = Color.White,
					)
				}
			}
			if (!dataWithHeartbeat.ok) {
				Spacer(modifier = Modifier.height(1.dp))
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.background(Color.Red)
						.padding(20.dp),
				) {
					Text(
						"Couldn't fetch data from the server",
						color = Color.White,
					)
				}
			}
		}
		if (showSettings) {
			val shareFreq = model.settings.shareFreq.value
			SettingsDialog(
				navController,
				model.settings,
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
