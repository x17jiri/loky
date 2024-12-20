package com.x17jiri.Loky.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.common.util.ClientLibraryUtils.getPackageInfo
import com.x17jiri.Loky.BuildConfig
import com.x17jiri.Loky.ui.theme.X17LokyTheme

@Composable
fun AboutScreen(navController: NavController) {
	val context = LocalContext.current
	val pkgInfo = getPackageInfo(context, "com.x17jiri.Loky")
	val version = pkgInfo?.versionName ?: "unknown"
	ScreenHeader("About", navController) {
		Column(
			modifier = Modifier
				.padding(20.dp)
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
		) {
			Text("version: $version")
			Text("commit: " + BuildConfig.GIT_COMMIT.take(16))
			Spacer(modifier = Modifier.height(20.dp))
			Text("(C) 2024 Jiri Bobek")
		}
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
