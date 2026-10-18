package com.github.e0ipso.tokkl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokklTheme {
                TokklApp()
            }
        }
    }
}

private enum class Screen { LAUNCH, LICENCES }

@Composable
private fun TokklApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.LAUNCH) }
    when (screen) {
        Screen.LAUNCH -> LaunchScreen(onOpenLicences = { screen = Screen.LICENCES })
        Screen.LICENCES -> {
            BackHandler { screen = Screen.LAUNCH }
            LicencesScreen(onBack = { screen = Screen.LAUNCH })
        }
    }
}

@Composable
fun LaunchScreen(onOpenLicences: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.launch_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOpenLicences) {
                Text(stringResource(R.string.licences))
            }
        }
    }
}
