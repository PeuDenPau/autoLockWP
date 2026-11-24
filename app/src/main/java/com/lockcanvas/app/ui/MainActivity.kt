package com.lockcanvas.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lockcanvas.app.ui.editor.EditorScreen
import com.lockcanvas.app.ui.theme.LockCanvasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LockCanvasTheme {
                EditorScreen()
            }
        }
    }
}
