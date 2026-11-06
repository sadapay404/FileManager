package com.moto.files.ui.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.moto.files.data.security.Credentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Device credential is deliberately excluded: cancellation returns to this custom screen. */
@Composable
fun LockScreen(credentials: Credentials, onUnlocked: (String) -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val type = credentials.type()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val biometricAvailable = credentials.biometricEnabled() &&
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Welcome back", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Unlock Moto Files with your $type", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))
        CredentialField(type, input, { input = it }, "Your $type")
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(20.dp))
        Button(enabled = !busy && input.isNotEmpty(), onClick = {
            busy = true
            scope.launch {
                val verified = withContext(Dispatchers.Default) { credentials.verify(input) }
                busy = false
                if (verified) { onUnlocked(input); input = "" } else { error = "Incorrect credential"; input = "" }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Unlock") }
        if (biometricAvailable) {
            TextButton(onClick = {
                val prompt = BiometricPrompt(activity, androidx.core.content.ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onUnlocked("") }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            // Both cancel and negative button leave the in-app field visible.
                            error = if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) null else errString.toString()
                        }
                    })
                prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Moto Files")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButtonText("Use app credential").build())
            }) { Text("Use fingerprint or face") }
        }
    }
}

@Composable
fun CredentialField(type: String, value: String, onValueChange: (String) -> Unit, label: String) {
    if (type == "Pattern") {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tap at least 6 different points in order", style = MaterialTheme.typography.bodySmall)
            repeat(3) { row ->
                Row {
                    repeat(3) { column ->
                        val point = (row * 3 + column + 1).toString()
                        OutlinedButton(onClick = { if (point !in value) onValueChange(value + point) },
                            enabled = point !in value, modifier = Modifier.padding(4.dp).size(64.dp), contentPadding = PaddingValues(0.dp)) {
                            Text(if (point in value) "•" else "○")
                        }
                    }
                }
            }
            TextButton(onClick = { onValueChange("") }) { Text("Reset pattern") }
        }
    } else OutlinedTextField(value = value, onValueChange = {
        onValueChange(if (type == "PIN") it.filter(Char::isDigit).take(12) else it.take(128))
    }, label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (type == "PIN") KeyboardType.NumberPassword else KeyboardType.Password),
        modifier = Modifier.fillMaxWidth())
}
