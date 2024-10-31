package com.example.snar

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import com.example.snar.databinding.ActivityMainBinding
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.snar.common.helpers.isARCoreSupported
import com.example.snar.common.helpers.isARCoreSessionAvailable
import androidx.camera.view.PreviewView
import com.google.ar.core.*
import com.google.ar.sceneform.ArSceneView



class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var arSceneView: ArSceneView
    private lateinit var arCoreSession: Session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isCameraSupported(this)) {
            setContentView(R.layout.activity_main)

            // Initialize PreviewView and ArSceneView
            previewView = findViewById(R.id.previewView)
            arSceneView = findViewById(R.id.arSceneView)

            startCamera()
        } else {
            Toast.makeText(this, "Camera Permissions not enabled", Toast.LENGTH_LONG).show()
            finish()
        }

        if (!isARCoreSessionAvailable(this)) {
            Toast.makeText(this, "ARCore not supported.", Toast.LENGTH_LONG).show()
            finish()
        } else {
            startARCore()
        }
    }

    private fun startARCore() {
        arCoreSession = Session(this)
        arSceneView.setupSession(arCoreSession)

        // Load and display the 3D model
    }

    // Camera permissions and setup (remains as is)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startCamera()
        else Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
    }

    private fun showInContextUI(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Camera Permission Needed")
            .setMessage("This app needs camera access to display AR content.")
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("No thanks", null)
            .show()
    }

    private fun isCameraSupported(context: Context): Boolean {
        return when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> true
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) -> {
                showInContextUI(context)
                false
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                false
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: Exception) {
                Log.e("MainActivity", "Camera start failed:", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
}
