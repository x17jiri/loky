package com.x17jiri.Loky.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.x17jiri.Loky.SettingsStore
import com.x17jiri.Loky.SettingsStoreMock
import com.x17jiri.Loky.SharingFrequency
import com.x17jiri.Loky.ui.theme.X17LokyTheme
import kotlin.math.roundToInt

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun SettingsDialog(
	navController: NavController,
	settings: SettingsStore,
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
					val shareFreq = settings.shareFreq.value.seconds
					var closestIndex = 0
					var closestDistance = (shareFreq - shareFreqValues[0].first) * (shareFreq - shareFreqValues[0].first)
					for (i in 1 until shareFreqValues.size) {
						val distance = (shareFreq - shareFreqValues[i].first) * (shareFreq - shareFreqValues[i].first)
						if (distance < closestDistance) {
							closestIndex = i
							closestDistance = distance
						}
					}
					var sliderValue by remember { mutableFloatStateOf(closestIndex.toFloat()) }
					var textValue by remember { mutableStateOf(shareFreqValues[closestIndex].second) }
					Slider(
						value = sliderValue,
						onValueChange = { newValue ->
							val sec = shareFreqValues[newValue.roundToInt()]
							textValue = sec.second
							settings.launchEdit { dao ->
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

@Preview(showBackground = true)
@Composable
fun SettingsDialogPreview() {
	X17LokyTheme {
		val navController = rememberNavController()
		SettingsDialog(navController, SettingsStoreMock()) {}
	}
}
