package com.example.snar

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import com.example.snar.databinding.ActivityMainBinding
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.snar.common.helpers.isARCoreSessionAvailable
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.example.snar.common.helpers.ARCoreSessionLifecycleHelper
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.snar.common.helpers.GeoPermissionsHelper
import android.view.View
import androidx.lifecycle.lifecycleScope
import android.opengl.Matrix
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.snar.common.helpers.Building
import com.example.snar.common.helpers.loadBuildingData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch




class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ARActivity"
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var arSceneView: ArSceneView
    //private lateinit var arCoreSession: Session

    private lateinit var surfaceView: SurfaceView
    lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    var jsonContent: List<Building>? = null

    //lateinit var view: HelloGeoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
        arCoreSessionHelper.exceptionCallback = { exception ->
            handleArCoreException(exception)
        }
        arCoreSessionHelper.beforeSessionResume = ::configureSession
        lifecycle.addObserver(arCoreSessionHelper)

        // Ensure Camera and ARCore support
        if (!ensureCameraAndArCoreSupport()) return
        startCamera()
        // Check and request permissions using GeoPermissionsHelper
        if (!GeoPermissionsHelper.hasGeoPermissions(this)) {
            GeoPermissionsHelper.requestPermissions(this)
            return
        }

        try {
            setContentView(R.layout.activity_main)
            initializeViews()
            initializeSurfaceView()

            // Initialize AR Session
            lifecycle.addObserver(arCoreSessionHelper)
            arCoreSessionHelper.beforeSessionResume = { session ->
                configureSession(session)
                arSceneView.setupSession(session)
            }


            arCoreSessionHelper.isSessionPaused = false

            startFrameUpdateLoop()
            jsonContent = loadBuildingData(this) // Load building data
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ARCore session: ${e.localizedMessage}", e)
            Toast.makeText(this, "Failed to initialize ARCore session.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleArCoreException(exception: Exception) {
        val message = when (exception) {
            is UnavailableUserDeclinedInstallationException -> "Please install Google Play Services for AR."
            is UnavailableApkTooOldException -> "Please update ARCore."
            is UnavailableSdkTooOldException -> "Please update this app."
            is UnavailableDeviceNotCompatibleException -> "This device does not support AR."
            is CameraNotAvailableException -> "Camera not available. Try restarting the app."
            else -> "Failed to create AR session: $exception"
        }
        Log.e(TAG, "ARCore threw an exception", exception)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    private fun ensureCameraAndArCoreSupport(): Boolean {
        if (!isCameraSupported(this)) {
            Toast.makeText(this, "Camera permissions not enabled.", Toast.LENGTH_LONG).show()
            finish()
            return false
        }

        if (!isARCoreSessionAvailable(this)) {
            Toast.makeText(this, "ARCore not supported.", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }
    private fun initializeViews() {
        surfaceView = findViewById(R.id.surface_view)
        previewView = findViewById(R.id.previewView)
        arSceneView = findViewById(R.id.arSceneView)
        var overlay = findViewById<FrameLayout>(R.id.overlayFrame)

        surfaceView.setZOrderOnTop(true) // Ensures it's rendered on top
        surfaceView.holder.setFormat(PixelFormat.TRANSLUCENT) // Makes background transparent
    }
    private fun initializeSurfaceView() {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(TAG, "Surface created.")
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.d(TAG, "Surface changed.")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d(TAG, "Surface destroyed.")
            }
        })
    }

    private fun configureSession(session: Session) {
        session.configure(
            session.config.apply {
                // Enable Geospatial Mode.
                geospatialMode = Config.GeospatialMode.ENABLED
            }
        )
    }
    /*
    //@Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)} passing\n      in a {@link RequestMultiplePermissions} object for the {@link ActivityResultContract} and\n      handling the result in the {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (!GeoPermissionsHelper.hasGeoPermissions(this)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(this, "Camera and location permissions are needed to run this application", Toast.LENGTH_LONG)
                .show()
            if (!GeoPermissionsHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                GeoPermissionsHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }*/

    //override fun onWindowFocusChanged(hasFocus: Boolean) {
     //   super.onWindowFocusChanged(hasFocus)
        //FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    //}

    private fun startFrameUpdateLoop() {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e("ARCore", "Unhandled coroutine exception", throwable)
        }
        lifecycleScope.launch {
            while (!arCoreSessionHelper.isSessionPaused) {
                Log.d(TAG, "PROCESSING")
                processFrame(arCoreSessionHelper.session)
                delay(100) // Adjust frame rate as necessary

            }
        }

    }

    /*
    private fun startFrameUpdateLoop() {
        lifecycleScope.launch(CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled coroutine exception", throwable)
        }) {
            while (isActive) {
                arCoreSession?.let { processFrame(it) }
                delay(100)  // Adjust based on desired frame rate
            }
        }
    }
*/
    private fun processFrame(session: Session?) {
        try {
            if (session == null) return
            //val session = arCoreSession ?: return // Ensure the session is not null
            // Perform frame processing
            val frame = session.update()
            val camera = frame.camera
            if (arCoreSessionHelper.isSessionPaused || camera.trackingState == TrackingState.PAUSED) return
            Log.d(TAG, "We are in")
            if (camera.trackingState == TrackingState.TRACKING) {
                Log.d(TAG, "Camera Tracking")
                val earth = session.earth ?: return

                // Map to track TextViews for each building by a unique identifier (e.g., building name or ID)
                val buildingTextViews = mutableMapOf<String, TextView>()

                if (earth.trackingState == TrackingState.TRACKING) {
                    Log.d(TAG, "Earth Tracking")
                    jsonContent?.forEach { building ->
                        val buildingLat: Double = building.latitude
                        val buildingLon: Double = building.longitude
                        val buildingAlt = building.altitude

                        // Compute distance to the building
                        val result = floatArrayOf(0f)
                        Location.distanceBetween(
                            earth.cameraGeospatialPose.latitude,
                            earth.cameraGeospatialPose.longitude,
                            buildingLat,
                            buildingLon,
                            result
                        )
                        val distance = result[0]

                        // Create an identity matrix (4x4) for the transformation
                        val transformationMatrix = FloatArray(16)
                        Matrix.setIdentityM(transformationMatrix, 0)

                        val buildingAnchor = earth.createAnchor(
                            buildingLat,
                            buildingLon,
                            buildingAlt,
                            transformationMatrix
                        )

                        val buildingPose = buildingAnchor.pose // Extract Pose from the anchor
                        val screenPosition = FloatArray(4)
                        val worldPosition = floatArrayOf(
                            buildingPose.tx(),
                            buildingPose.ty(),
                            buildingPose.tz(),
                            1f
                        )
                        val projectionMatrix = FloatArray(16)
                        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

                        // Multiply the projection matrix by the world position
                        Matrix.multiplyMV(screenPosition, 0, projectionMatrix, 0, worldPosition, 0)

                        val overlayFrame = findViewById<FrameLayout>(R.id.overlayFrame)
                        overlayFrame.bringToFront()
                        val screenX = (screenPosition[0] / screenPosition[3]) * overlayFrame.width
                        val screenY = (screenPosition[1] / screenPosition[3]) * overlayFrame.height
                        Log.d(TAG, "Screen Position: x=${screenPosition[0]}, y=${screenPosition[1]}")
                        // Dynamically manage TextView for the building
                        runOnUiThread {
                            val displayText = getString(
                                R.string.building_display,
                                building.name,
                                distance,
                                building.description
                            )

                            if (buildingTextViews.containsKey(building.name)) {
                                // Update existing TextView
                                val textView = buildingTextViews[building.name]!!
                                textView.text = displayText
                                textView.x = screenX
                                textView.y = screenY
                            } else {
                                // Create a new TextView
                                val textView = TextView(this).apply {
                                    text = displayText
                                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                                    textSize = 16f
                                    x = screenX
                                    y = screenY
                                }
                                overlayFrame.addView(textView)
                                buildingTextViews[building.name] = textView
                            }
                            Log.d(TAG, "UUUUUGH")
                        }
                    }
                }

            }
        } catch (e: Exception) {
            Log.e("ARCore", "Error processing frame", e)
        }
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
                it.surfaceProvider = previewView.surfaceProvider
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
