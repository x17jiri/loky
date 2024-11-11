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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.x17jiri.Loky.ui.theme.X17LokyTheme

@Composable
fun HyperlinkButton(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = Color(0xFF1E88E5), // Blue color to resemble a hyperlink
        style = TextStyle(
            fontSize = 16.sp,
            textDecoration = TextDecoration.Underline // Underline for hyperlink style
        ),
        modifier = Modifier.clickable(onClick = onClick)
    )
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

@Preview(showBackground = true)
@Composable
fun ConfirmDialogPreview() {
    X17LokyTheme {
        ConfirmDialog("Are you sure?", {}, {})
    }
}

@Preview(showBackground = true)
@Composable
fun MessageDialogPreview() {
	X17LokyTheme {
		MessageDialog("This is a message", {})
	}
}

@Preview(showBackground = true)
@Composable
fun InfoDialogPreview() {
	X17LokyTheme {
		InfoDialg("This is a message")
	}
}
