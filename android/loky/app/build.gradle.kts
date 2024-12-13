plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
	id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
	id("kotlin-kapt")
}

android {
	namespace = "com.x17jiri.Loky"
	compileSdk = 34

	defaultConfig {
		applicationId = "com.x17jiri.Loky"
		minSdk = 31
		targetSdk = 34
		versionCode = 1
		versionName = "0.31-beta"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildFeatures {
		buildConfig = true
	}
	buildTypes {
		all {
			buildConfigField("String", "GIT_COMMIT", "\"${getGitCommit()}\"")
		}
		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
	kotlinOptions {
		jvmTarget = "1.8"
	}
	buildFeatures {
		compose = true
	}
}

fun String.execute(): String {
	return Runtime.getRuntime().exec(this).inputStream.bufferedReader().readText().trim()
}

fun getGitCommit(): String {
	return "git rev-parse HEAD".execute().trim()
}

dependencies {

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)
	implementation(libs.androidx.ui.graphics)
	implementation(libs.androidx.ui.tooling.preview)
	implementation(libs.androidx.material3)
	implementation(libs.androidx.navigation.runtime.ktx)
	implementation(libs.androidx.navigation.compose)
	implementation(libs.play.services.location)
	implementation(libs.androidx.room.common)
	implementation(libs.androidx.room.ktx)
	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.ui.test.junit4)
	debugImplementation(libs.androidx.ui.tooling)
	debugImplementation(libs.androidx.ui.test.manifest)
	implementation("androidx.compose.material:material-icons-extended")
	implementation("androidx.datastore:datastore-preferences:1.0.0")
	implementation("org.bouncycastle:bcprov-jdk15on:1.70")
	implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
	implementation("com.google.code.gson:gson:2.10.1")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
	implementation("com.google.android.gms:play-services-location:21.0.1")

	implementation("androidx.room:room-runtime:2.6.1")
	implementation("androidx.room:room-ktx:2.6.1")
	kapt("androidx.room:room-compiler:2.6.1")
	implementation("androidx.core:core-splashscreen:1.0.1")

	// implementation("com.google.maps.android:maps-compose:2.11.0")
	// implementation("com.google.android.gms:play-services-maps:18.1.0")

	// Google Maps Compose library
	val mapsComposeVersion = "6.1.2"
	implementation("com.google.maps.android:maps-compose:$mapsComposeVersion")
	// Google Maps Compose utility library
	implementation("com.google.maps.android:maps-compose-utils:$mapsComposeVersion")
	// Google Maps Compose widgets library
	implementation("com.google.maps.android:maps-compose-widgets:$mapsComposeVersion")

	implementation("com.mapbox.maps:android:11.7.1")
	implementation("com.mapbox.extension:maps-compose:11.7.1")
}

secrets {
	// Optionally specify a different file name containing your secrets.
	// The plugin defaults to "local.properties"
	propertiesFileName = "secrets.properties"

	// A properties file containing default secret values. This file can be
	// checked in version control.
	defaultPropertiesFileName = "local.defaults.properties"
}
