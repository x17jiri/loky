package com.x17jiri.Loky

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.TextStyle
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class MainActivity: ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		Log.d("Locodile", "onCreate.1")
		super.onCreate(savedInstanceState)
		Log.d("Locodile", "onCreate.2")
		setContent {
			X17LokyTheme {
				Log.d("Locodile", "onCreate.3")
				val model: MainViewModel = viewModel(factory = MainViewModelFactory(this))
				Log.d("Locodile", "onCreate.4")
				val appState by model.appState.collectAsState()
				Log.d("Locodile", "onCreate.5")
				when {
					appState.currentScreen is Screen.Login ->
						LoginScreen(this, model)

					appState.currentScreen is Screen.Loading ->
						LoadingScreen()

					appState.currentScreen is Screen.Other ->
						NavigationGraph(this, model)
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
fun NavigationGraph(context: Context, model: MainViewModel) {
	Log.d("Locodile", "Building nav graph")
	val navController = rememberNavController()
	NavHost(navController = navController, startDestination = "map") {
		composable("map") { MapView(navController, model) }
		composable("contacts") { Contacts(navController, model) }
/*		composable("groups") { Groups(navController, model) }
		composable("groupDetail/{groupId}") { entry ->
			val groupId = entry.arguments?.getString("groupId")?.toInt() ?: 0;
			GroupDetail(navController, model, groupId)
		}*/
	}
}

@Composable
fun LoginScreen(context: Context, model: MainViewModel) {
	val cred by model.credMan.credentials.collectAsState()
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
					model.login()
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
					label = { Text("Name") },
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
						onClick = {
							onConfirm(name)
							onDismiss()
						}
					) {
						Text("Add")
					}
				}
			}
		}
	}
}

@Composable
fun Contacts(navController: NavController, model: MainViewModel) {
	SettingsScreen("Contacts", navController) {
		val contacts by model.contactsMan.contacts.collectAsState()
		var groupToDel by remember { mutableStateOf<Long>(-1) }
		var addDialog by remember { mutableStateOf(false) }
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
						Text(
							text = name,
							modifier = Modifier.weight(1.0f)
						)
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
/*						IconButton(
							onClick = { groupToDel = id }
						) {
							Icon(
								Icons.Filled.Delete, // Trash (delete) icon
								contentDescription = "Delete Item"
							)
						}*/
					}
				}
			}
			var failedDialog by remember { mutableStateOf("") }
			FloatingActionButton(
				onClick = { addDialog = true },
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(20.dp),
			) {
				Icon(
					Icons.Filled.Add,
					contentDescription = "Add"
				)
			}
			/*
			if (groupToDel >= 0) {
				ConfirmDialog(
					"Delete group ${groups[groupToDel].name}?",
					onDismiss = { groupToDel = -1; },
					onConfirm = { model.groupsMan.remove(groupToDel) }
				)
			}*/
			if (addDialog) {
				AddContactDialog(
					onDismiss = { addDialog = false },
					onConfirm = { /* TODO */ }
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
