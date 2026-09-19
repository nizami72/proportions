package az.dahcraf.proportions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import az.dahcraf.proportions.ui.ProportionsApp
import az.dahcraf.proportions.ui.theme.ProportionsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as ProportionsApplication).repository
        setContent {
            ProportionsTheme {
                ProportionsApp(repository = repository)
            }
        }
    }
}
