package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.myapplication.ui.MainNavigation
import com.example.myapplication.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.myapplication.data.pen.PenManager

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var penManager: PenManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
        
        // BUILD V27: Trigger Hilt-injected PenManager initialization
        try {
            penManager.toString()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Early PenManager access failed", e)
        }
    }
}