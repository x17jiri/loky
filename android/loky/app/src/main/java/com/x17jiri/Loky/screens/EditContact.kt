package com.x17jiri.Loky.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.ColorInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.x17jiri.Loky.Contact
import com.x17jiri.Loky.ContactsStore
import com.x17jiri.Loky.ContactsStoreMock
import com.x17jiri.Loky.IconCache
import com.x17jiri.Loky.PublicDHKey
import com.x17jiri.Loky.PublicECKey
import com.x17jiri.Loky.PublicKeyMock
import com.x17jiri.Loky.PublicSigningKey
import com.x17jiri.Loky.R
import com.x17jiri.Loky.ServerInterfaceMock
import com.x17jiri.Loky.ui.theme.X17LokyTheme

fun isColorDark(color: Color): Boolean {
	// Perceived brightness formula
	val brightness = (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114)
	return brightness < 0.5
}

@Composable
fun ColorPicker(colors: List<Color>, selectedColor: Color, onColorSelected: (Color) -> Unit) {
	Column {
		for (i in colors.indices step 5) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceEvenly
			) {
				for (j in 0 until 5) {
					if (i + j < colors.size) {
						val color = colors[i + j]
						Box(
							contentAlignment = Alignment.Center,
							modifier = Modifier
								.weight(1f)
								.aspectRatio(1f)
								.padding(8.dp)
								.border(2.dp, Color(0xFF406080), CircleShape)
								.background(color, CircleShape)
								.clickable { onColorSelected(color) }
						) {
							if (color == selectedColor) {
								// Draw a checkmark inside the circle
								val checkmarkColor = if (isColorDark(color)) Color.White else Color.Black
								Icon(
									imageVector = Icons.Default.Check,
									contentDescription = "Selected",
									tint = checkmarkColor,
									modifier = Modifier.size(24.dp) // Adjust size as needed
								)
							}
						}
					} else {
						Box(
							modifier = Modifier
								.weight(1f)
								.aspectRatio(1f)
								.padding(8.dp)
						)
					}
				}
			}
		}
	}
}

@Composable
fun EditContactScreen(
	navController: NavController,
	contactsStore: ContactsStore,
	iconCache: IconCache,
	contactId: String
) {
	ScreenHeader("Edit Contact", navController) {
		val contacts by contactsStore.flow().collectAsState(emptyList())
		val contact = contacts.find { it.id == contactId }
		if (contact == null) {
			Text("Error: Contact not found")
			return@ScreenHeader
		}
		Column(
			modifier = Modifier
				.padding(20.dp)
				.fillMaxWidth()
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
		) {
			Text(buildAnnotatedString {
				append("Name: ")
				withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
					append(contact.name)
				}
			})
			Spacer(modifier = Modifier.height(20.dp))
			var color by remember { mutableStateOf(Color(contact.color)) }
			Text("Color:")
			Spacer(modifier = Modifier.height(20.dp))
			Row {
				Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
					Image(bitmap = iconCache.get(color.toArgb()).asImageBitmap(), contentDescription = "Contact's marker")
				}
			}
			Spacer(modifier = Modifier.height(20.dp))
			ColorPicker(
				listOf(
					Color(0xFFFF0000), // red
					Color(0xFFFF8000), // orange
					Color(0xFFFFFF00), // yellow
					Color(0xFFA000FF), // purple
					Color(0xFFFF00FF), // magenta

					Color(0xFFFF0070), // pink
					Color(0xFF0000FF), // light blue
					Color(0xFF0080FF), // light blue
					Color(0xFF00FFFF), // light cyan
					Color(0xFF00FF00), // green

					Color(0xFF7F0000), // dark red
					Color(0xFF7F4000), // brown
					Color(0xFF7F7F00), // dark yellow
					Color(0xFF50007F), // dark purple
					Color(0xFF7F007F), // dark magenta

					Color(0xFF7F0048), // dark pink
					Color(0xFF00007F), // dark blue
					Color(0xFF00407F), // dark blue
					Color(0xFF007F7F), // dark cyan
					Color(0xFF007F00), // dark green

					Color.Black,
					Color(0xFF404040), // dark grey
					Color(0xFF808080), // grey
					Color(0xFFC0C0C0), // light grey
					Color.White,
				),
				selectedColor = color
			) {
				color = it
				contactsStore.launchEdit { dao -> dao.setColor(contact, color.toArgb()) }
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
fun EditContactScreenPreview() {
	X17LokyTheme {
		val context = LocalContext.current
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
						color = Color.Yellow.toArgb(),
					),
					"abcd" to Contact(
						id = "abcd",
						name = "zuzka",
						send = true,
						recv = true,
						signKey = PublicSigningKey(PublicECKey(PublicKeyMock())),
						masterKey = PublicDHKey(PublicECKey(PublicKeyMock())),
						color = Color.Magenta.toArgb(),
					),
				)
		)
		EditContactScreen(navController, contactsStore, IconCache(context), "abc")
	}
}
