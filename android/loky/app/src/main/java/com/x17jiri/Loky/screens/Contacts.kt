package com.x17jiri.Loky.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.x17jiri.Loky.Contact
import com.x17jiri.Loky.ContactsStore
import com.x17jiri.Loky.ContactsStoreMock
import com.x17jiri.Loky.MainViewModel
import com.x17jiri.Loky.PublicDHKey
import com.x17jiri.Loky.PublicECKey
import com.x17jiri.Loky.PublicKeyMock
import com.x17jiri.Loky.PublicSigningKey
import com.x17jiri.Loky.Receiver
import com.x17jiri.Loky.ServerInterface
import com.x17jiri.Loky.ServerInterfaceMock
import com.x17jiri.Loky.monotonicSeconds
import com.x17jiri.Loky.prettyAge
import com.x17jiri.Loky.ui.theme.X17LokyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.PublicKey

enum class AddContactState {
    Hidden,
    TextInput,
    Checking,
}

@Composable
fun ContactsScreen(
    navController: NavController,
    contactsStore: ContactsStore,
    receiver: Receiver,
    server: ServerInterface,
) {
    val scope = rememberCoroutineScope()
    ScreenHeader("Contacts", navController) {
        val contacts by contactsStore.flow().collectAsState(emptyList())
        val lastErr by receiver.lastErr.collectAsState(Pair(0, emptyMap()))
        var itemToDel by remember { mutableStateOf<Contact?>(null) }
        var addContactState by remember { mutableStateOf(AddContactState.Hidden) }
        var failedDialog by remember { mutableStateOf("") }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(contacts.size) { __i ->
                    val contact = contacts[__i]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { navController.navigate("editContact/${contact.id}") }
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
                                    contactsStore.launchEdit { store ->
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
//									contactsStore.launchEdit { store ->
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
                        val err = lastErr.second[contact.id]
                        if (err != null && err != 0L) {
                            IconButton(
                                onClick = {
                                    val now = monotonicSeconds()
                                    val age = prettyAge(now - err)
                                    failedDialog = "The last message (received ${age} ago) couldn't be decrypted."
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,//Default.Warning,
                                    contentDescription = "Warning icon",
                                    tint = Color.Red,
                                )
                            }
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
                        contactsStore.launchEdit { store ->
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
                                server.userInfo(userName).fold(
                                    onSuccess = { userInfo ->
                                        contactsStore.launchEdit { dao ->
                                            dao.insert(
                                                Contact(
                                                    id = userInfo.id,
                                                    name = userName,
                                                    signKey = userInfo.signKey,
                                                    masterKey = userInfo.masterKey,
                                                    send = false,
                                                    recv = true,
													color = Color.Red.toArgb(),
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
/*
@Preview(showBackground = true)
@Composable
fun ContactsScreenPreview() {
    X17LokyTheme {
        val navController = rememberNavController()
        val contactsStore = ContactsStoreMock(
                mutableMapOf(
                    "abc" to Contact(
                        id = "abc",
                        name = "jiri",
                        send = false,
                        recv = true,
                        signKey = PublicSigningKey(PublicECKey(PublicKeyMock())),
                        masterKey = PublicDHKey(PublicECKey(PublicKeyMock())),
                    ),
                    "abcd" to Contact(
                        id = "abcd",
                        name = "zuzka",
                        send = true,
                        recv = true,
                        signKey = PublicSigningKey(PublicECKey(PublicKeyMock())),
                        masterKey = PublicDHKey(PublicECKey(PublicKeyMock())),
                    ),
                )
        )
        val server = ServerInterfaceMock()
        ContactsScreen(navController, contactsStore, server)
    }
}
*/
@Preview(showBackground = true)
@Composable
fun AddContactDialogPreview() {
    X17LokyTheme {
        AddContactDialog({}, {})
    }
}
