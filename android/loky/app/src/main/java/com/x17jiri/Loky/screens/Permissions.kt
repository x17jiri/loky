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
import androidx.compose.material.icons.filled.Warning
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
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

@Composable
fun PermissionRow(
	granted: Boolean,
	name: String,
	justification: String,
	onClick: () -> Unit,
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(16.dp)
	) {
		Icon(
			imageVector = if (granted) { Icons.Filled.Check } else { Icons.Filled.Warning },
			contentDescription = null,
			modifier = Modifier.size(24.dp),
			tint = if (granted) Color.Green else Color.Red,
		)

		Column {
			Text(
				buildAnnotatedString {
					withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
						append(name)
					}
				},
				modifier = Modifier.padding(start = 16.dp),
			)
			Text(
				text = justification,
				modifier = Modifier.padding(start = 16.dp),
			)
		}
	}
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(navController: NavController) {
	ScreenHeader("Grant Permissions", navController) {
		val locationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)
		val locationPermissionGranted = locationPermission.status == PermissionStatus.Granted

		val backgroundLocationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
		val backgroundLocationPermissionGranted = backgroundLocationPermission.status == PermissionStatus.Granted

		val notificationPermission = rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
		val notificationPermissionGranted = notificationPermission.status == PermissionStatus.Granted

		val wakeLockPermission = rememberPermissionState(android.Manifest.permission.WAKE_LOCK)
		val wakeLockPermissionGranted = wakeLockPermission.status == PermissionStatus.Granted

		val ignoreBatteryOptimizationPermission = rememberPermissionState(android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
		val ignoreBatteryOptimizationPermissionGranted = ignoreBatteryOptimizationPermission.status == PermissionStatus.Granted

		// Show a list of permissions with checkboxes and justification
		LazyColumn {
			item {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp)
				) {
					Text("Click on the permissions to grant them")
				}
			}
			item {
				PermissionRow(
					granted = locationPermissionGranted,
					name = "Location",
					justification = "Required for basic functionality",
					onClick = { locationPermission.launchPermissionRequest() }
				)
			}
			item {
				PermissionRow(
					granted = backgroundLocationPermissionGranted,
					name = "Background Location",
					justification = "We need to be able to track your location even when you are not using the app",
					onClick = { backgroundLocationPermission.launchPermissionRequest() },
				)
			}
			item {
				PermissionRow(
					granted = notificationPermissionGranted,
					name = "Notifications",
					justification = "A notification will be shown to inform you when location tracking is active",
					onClick = { notificationPermission.launchPermissionRequest() },
				)
			}
			item {
				PermissionRow(
					granted = wakeLockPermissionGranted,
					name = "Prevent Sleeping",
					justification = "Required so background location updates don't stop when the device is idle",
					onClick = { wakeLockPermission.launchPermissionRequest() },
				)
			}
			item {
				PermissionRow(
					granted = ignoreBatteryOptimizationPermissionGranted,
					name = "Ignore Battery Optimization",
					justification = "Required so background location updates don't stop when the device is idle",
					onClick = { ignoreBatteryOptimizationPermission.launchPermissionRequest() },
				)
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
fun PermissionsScreenPreview() {
	X17LokyTheme {
		val navController = rememberNavController()
		PermissionsScreen(navController)
	}
}
