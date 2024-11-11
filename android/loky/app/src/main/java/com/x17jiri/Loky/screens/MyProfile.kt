package com.x17jiri.Loky.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.x17jiri.Loky.ui.theme.X17LokyTheme

@Composable
fun MyProfileScreen(navController: NavController) {
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

@Preview(showBackground = true)
@Composable
fun MyProfileScreenPreview() {
    X17LokyTheme {
        val navController = rememberNavController()
        MyProfileScreen(navController)
    }
}
