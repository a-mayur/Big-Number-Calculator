package info.mayuragarkar.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import info.mayuragarkar.calculator.ui.BigNumberTheme
import info.mayuragarkar.calculator.ui.CalcScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BigNumberTheme {
                CalcScreen()
            }
        }
    }
}
