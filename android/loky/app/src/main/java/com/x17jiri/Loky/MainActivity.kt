package com.x17jiri.Loky

import android.content.Context
import android.content.Intent
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
import com.google.maps.android.compose.*;
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat.startForegroundService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class MainActivity: ComponentActivity() {
	private lateinit var groupsData: GroupsData
	private lateinit var serverInterface: ServerInterface

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		groupsData = GroupsData(dataStore)
		serverInterface = ServerInterface(this)
		setContent {
			X17LokyTheme {
				val model: MainViewModel = viewModel(factory = MainViewModelFactory(this))
				val appState by model.appState.collectAsState()
				when {
					appState.currentScreen is Screen.Login ->
						LoginScreen(this, model)

					appState.currentScreen is Screen.Loading ->
						LoadingScreen()

					appState.currentScreen is Screen.Other ->
						NavigationGraph(this, groupsData, model)
				}
			}
		}
	}
}

@Composable
fun LoadingScreen() {
	Box(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center
	) {
		Text("Loading...", fontSize = 24.sp)
	}
}

@Composable
fun NavigationGraph(context: Context, groupsData: GroupsData, model: MainViewModel) {
	Log.d("Locodile", "Building nav graph")
	val navController = rememberNavController()
	NavHost(navController = navController, startDestination = "map") {
		composable("map") { MapView(navController, model) }
		composable("settings") { Settings(navController) }
		composable("groups") { Groups(navController, groupsData) }
		composable("groupDetail/{groupId}") { entry ->
			val groupId = entry.arguments?.getString("groupId")?.toInt() ?: 0;
			GroupDetail(navController, groupsData, groupId)
		}
	}
}

@Composable
fun LoginScreen(context: Context, model: MainViewModel) {
	val cred = model.credetials.get()
	var username by remember { mutableStateOf(cred.user) }
	var password by remember { mutableStateOf(cred.passwd) }
	var failedDialog by remember { mutableStateOf("") }
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
					model.credetials.set(username, password)
				},
				label = { Text("Username") },
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp)
			)
			TextField(
				value = password,
				onValueChange = {
					password = it
					model.credetials.set(username, password)
				},
				label = { Text("Password") },
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp),
				visualTransformation = PasswordVisualTransformation()
			)
			Button(
				onClick = {
					model.login()
				},
				enabled = username != "" && password != "",
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
fun MapView(navController: NavController, model: MainViewModel) {
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
					IconButton(onClick = { navController.navigate("settings") }) {
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
				GoogleMap(
					modifier = Modifier.fillMaxSize()
					//onMapLoaded = { isMapLoaded = true }
				)
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
fun Settings(navController: NavController) {
	SettingsScreen("Settings", navController) {
		Column(
			modifier = Modifier.verticalScroll(rememberScrollState())
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.clickable { navController.navigate("groups") }
					.padding(10.dp)
			) {
				Text("Who I share with")
			}
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.clickable { navController.navigate("groups") }
					.padding(10.dp)
			) {
				Text("Who shares with me")
			}
		}
	}
}

@Composable
fun Groups(navController: NavController, groupsData: GroupsData) {
	SettingsScreen("Who I share with", navController) {
		var list by remember { mutableStateOf(groupsData.IDsPlusEnabled()) }
		var groupToDel by remember { mutableStateOf(-1) }
		Box(modifier = Modifier.fillMaxSize()) {
			LazyColumn(
				modifier = Modifier.fillMaxWidth()
			) {
				items(list.size) { __i ->
					val id = list[__i].id
					val enabled = list[__i].enabled
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier
							.fillMaxWidth()
							.clickable { navController.navigate("groupDetail/${id}") }
							.padding(10.dp)
					) {
						Box {
							Switch(
								checked = enabled,
								onCheckedChange = {
									groupsData.enable(id, it)
									list = groupsData.IDsPlusEnabled()
								},
								modifier = Modifier.padding(start = 10.dp, end = 10.dp),
							)
						}
						Text(
							text = groupsData[id].name,
							modifier = Modifier.weight(1.0f)
						)
						IconButton(
							onClick = { groupToDel = id }
						) {
							Icon(
								Icons.Filled.Delete, // Trash (delete) icon
								contentDescription = "Delete Item"
							)
						}
					}
				}
			}
			var failedDialog by remember { mutableStateOf("") }
			FloatingActionButton(
				onClick = {
					if (groupsData.add("New Group") != null) {
						list = groupsData.IDsPlusEnabled()
					} else {
						failedDialog = "Maximum number of groups reached"
					}
				},
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(20.dp),
			) {
				Icon(
					Icons.Filled.Add,
					contentDescription = "Add"
				)
			}
			if (groupToDel >= 0) {
				ConfirmDialog(
					"Delete group ${groupsData[groupToDel].name}?",
					onDismiss = { groupToDel = -1; },
					onConfirm = {
						groupsData.remove(groupToDel)
						list = groupsData.IDsPlusEnabled()
					}
				)
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

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun GroupDetail(navController: NavController, groupsData: GroupsData, id: Int) {
	SettingsScreen("Group Detail", navController) {
		Column(
			modifier = Modifier.verticalScroll(rememberScrollState())
		) {
			var groupName by remember { mutableStateOf(groupsData[id].name) }
			TextField(
				value = groupName,
				onValueChange = { groupName = it },
				label = { Text("Group Name") },
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp)
			)

			var isSecretVisible by remember { mutableStateOf(false) }
			TextField(
				value = if (isSecretVisible) { groupsData[id].secretKey.text } else { "*****" },
				enabled = false,
				onValueChange = {},
				label = { Text("Secret Data") },
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp),
				trailingIcon = {
					val image =
						if (isSecretVisible) {
							Icons.Filled.Visibility
						} else {
							Icons.Default.VisibilityOff
						}
					IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
						Icon(
							imageVector = image,
							contentDescription = "Toggle Secret Visibility"
						)
					}
				},
				leadingIcon = {
					IconButton(onClick = { /*TODO*/ }) {
						Icon(
							imageVector = Icons.Filled.ContentCopy,
							contentDescription = "Copy Secret to Clipboard"
						)
					}
				},
			)

			var sharingEnabled by remember { mutableStateOf(groupsData[id].enabled) }
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp)
			) {
				Box {
					Switch(
						checked = sharingEnabled,
						onCheckedChange = { sharingEnabled = it },
						modifier = Modifier.padding(start = 10.dp, end = 10.dp),
					)
				}
				Box(modifier = Modifier.weight(1.0f)) {
					Text("Share location")
				}
			}

			Spacer(modifier = Modifier.height(20.dp))

			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp)
			) {
				Button(
					onClick = {
						groupsData.update(id, groupName, sharingEnabled)
						navController.popBackStack()
					},
					enabled = (
						groupName != ""
						&& (
							groupName != groupsData[id].name
							|| sharingEnabled != groupsData[id].enabled
						)
					),
					content = { Text("Save") },
					modifier = Modifier
						.weight(1.0f)
						.padding(10.dp)
				)
				Button(
					onClick = { navController.popBackStack() },
					content = { Text("Cancel") },
					modifier = Modifier
						.weight(1.0f)
						.padding(10.dp)
				)
			}
		}
	}
}
/*
@Preview(showBackground = true)
@Composable
fun MapViewPreview() {
	X17LokyTheme {
		val navController = rememberNavController()
		MapView(navController)
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
