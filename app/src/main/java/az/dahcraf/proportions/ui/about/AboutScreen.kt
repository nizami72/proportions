package az.dahcraf.proportions.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

// TODO: fill in a real privacy-policy URL and contact address before release.
private const val PRIVACY_POLICY_URL = "https://example.com/proportions-privacy-policy"
private const val CONTACT_EMAIL = "support@example.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Proportions", style = MaterialTheme.typography.headlineSmall)
            Text(
                "A minimalist ratio calculator: save an ingredient list once, then change any " +
                    "amount while cooking and every other amount scales to match.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Version 1.0", style = MaterialTheme.typography.bodySmall)

            TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) {
                Text("Privacy policy")
            }
            TextButton(onClick = { uriHandler.openUri("mailto:$CONTACT_EMAIL") }) {
                Text("Contact: $CONTACT_EMAIL")
            }
        }
    }
}
