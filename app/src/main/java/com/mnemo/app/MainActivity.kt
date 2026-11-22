package com.mnemo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mnemo.app.navigation.MnemoNavHost
import com.mnemo.app.ui.theme.MnemoTheme

/**
 * Main application activity for Mnemo.
 *
 * Implements Pravee's presentation foundation (Android UI & Application Experience Lead).
 * Launches cleanly without requiring any intrusive runtime permissions upfront.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MnemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MnemoNavHost()
                }
            }
        }
    }
}
