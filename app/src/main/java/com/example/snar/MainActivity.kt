package com.example.snar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.location.Location
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.snar.common.helpers.ARCoreSessionLifecycleHelper
import com.example.snar.common.helpers.Building
import com.example.snar.common.helpers.GeoPermissionsHelper
import com.example.snar.common.helpers.isARCoreSessionAvailable
import com.example.snar.common.helpers.loadBuildingData
import com.example.snar.databinding.ActivityMainBinding
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.ar.sceneform.ArSceneView
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ARActivity"
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: SurfaceView
    private lateinit var arSceneView: ArSceneView
    private lateinit var overlayFrame: FrameLayout
    //private lateinit var arCoreSession: Session
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var surface: Surface

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
        // startCamera()
        // Check and request permissions using GeoPermissionsHelper
        if (!GeoPermissionsHelper.hasGeoPermissions(this)) {
            GeoPermissionsHelper.requestPermissions(this)
            return
        }

        try {
            setContentView(R.layout.activity_main)
            initializeViews()
            val config = arCoreSessionHelper.session?.config
            if (config != null) {
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                arCoreSessionHelper.session?.configure(config)
            } else {
                Log.e("ARCore", "Config is null")
            }

            initializeSurfaceView()

            // Initialize AR Session
            lifecycle.addObserver(arCoreSessionHelper)
            arCoreSessionHelper.beforeSessionResume = { session ->
                configureSession(session)
                arSceneView.setupSession(session)

            }

            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager


            arCoreSessionHelper.isSessionPaused = false

            startFrameUpdateLoop()
            jsonContent = loadBuildingData(this) // Load building data
        } catch (e: UnavailableDeviceNotCompatibleException) {
        Log.e(TAG, "Device not compatible", e)
    } catch (e: UnavailableApkTooOldException) {
        Log.e(TAG, "ARCore APK too old", e)
    } catch (e: UnavailableSdkTooOldException) {
        Log.e(TAG, "SDK too old", e)
    } catch (e: CameraNotAvailableException) {
        Log.e(TAG, "Camera not available", e)
    } catch (e: Exception) {
        Log.e(TAG, "General exception: ${e.localizedMessage}", e)
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
        previewView = findViewById(R.id.surfaceView)
        arSceneView = findViewById(R.id.arSceneView)
        surfaceView.setZOrderOnTop(true) // Ensures it's rendered on top
        surfaceView.holder.setFormat(PixelFormat.TRANSLUCENT) // Makes background transparent
        overlayFrame = findViewById(R.id.overlayFrame)
        overlayFrame.bringToFront()


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
                cloudAnchorMode = Config.CloudAnchorMode.ENABLED
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
    //val buildingTextViews = mutableMapOf<String, TextView>()
    private fun processFrame(session: Session?) {
        try {
            overlayFrame.removeAllViews()
            if (session == null) return

            val frame = session.update()
            val camera = frame.camera

            if (camera.trackingState == TrackingState.TRACKING) {
                val earth = session.earth ?: return

                if (earth.trackingState == TrackingState.TRACKING) {
                    val cameraGeospatialPose = earth.cameraGeospatialPose
                    val buildingTextViews = mutableMapOf<String, TextView>()
                    // Determine which buildings are currently in view
                    val buildingsInView = mutableSetOf<String>()
                    if (jsonContent == null){
                        loadBuildingData(this)
                    }
                    jsonContent?.forEach { building ->
                        val buildingLat = building.latitude
                        val buildingLon = building.longitude
                        val buildingAlt = building.altitude

                        val result = floatArrayOf(0f)
                        Location.distanceBetween(
                            cameraGeospatialPose.latitude,
                            cameraGeospatialPose.longitude,
                            buildingLat,
                            buildingLon,
                            result
                        )
                        val distance = result[0]
                        Log.d("DEBUG1", "Camera Coordinates: lat=${cameraGeospatialPose.latitude}, lon=${cameraGeospatialPose.longitude}")
                        Log.d("DEBUG1", "Building Coordinates: lat=$buildingLat, lon=$buildingLon")
                        Log.d("DEBUG1", "Distance: lat=${result[0]}")

                        val pose = cameraGeospatialPose.getEastUpSouthQuaternion()
                        val buildingPose = earth.getPose(
                            buildingLat,
                            buildingLon,
                            buildingAlt,
                            pose.component1(),
                            pose.component2(),
                            pose.component3(),
                            pose.component4()
                        )

                        val screenPosition = FloatArray(4)
                        val worldPosition = floatArrayOf(
                            buildingPose.tx(),
                            buildingPose.ty(),
                            buildingPose.tz(),
                            1f
                        )
                        val viewMatrix = FloatArray(16)
                        val projectionMatrix = FloatArray(16)
                        val viewProjectionMatrix = FloatArray(16)
                        camera.getViewMatrix(viewMatrix, 0)
                        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

                        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
                        Matrix.multiplyMV(screenPosition, 0, viewProjectionMatrix, 0, worldPosition, 0)

                        val normalizedX = (screenPosition[0] / screenPosition[3])//.coerceIn(-1f, 1f)
                        val normalizedY = (screenPosition[1] / screenPosition[3])//.coerceIn(-1f, 1f)

                        var screenX = ((normalizedX + 1) / 2) * overlayFrame.width
                        var screenY = ((1 - normalizedY) / 2) * overlayFrame.height

                        // Update or create TextView for the building
                        val displayText = getString(
                            R.string.building_display,
                            building.name,
                            distance,
                            building.description
                        )
                        Log.d("TEXTSIZE", "size: ${16f - (distance/50)}, name: ${building.name}")

                        if (buildingTextViews.containsKey(building.name)) {
                            // Update existing TextView
                            val textView = buildingTextViews[building.name]!!
                            textView.text = displayText
                            textView.x = screenX
                            textView.y = screenY
                        } else {
                            // Create new TextView
                            val textView = TextView(this).apply {
                                text = displayText
                                maxWidth =
                                    (Resources.getSystem().displayMetrics.widthPixels * 0.6).toInt()
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT
                                )
                                setTextColor(
                                    ContextCompat.getColor(
                                        this@MainActivity,
                                        R.color.teal_700
                                    )
                                )
                                maxHeight =
                                    (Resources.getSystem().displayMetrics.heightPixels * 0.3).toInt()
                                setBackgroundColor(
                                    ContextCompat.getColor(
                                        this@MainActivity,
                                        R.color.white
                                    )
                                )
                                //textSize = 16f
                                x = screenX
                                y = screenY
                                z = -distance
                                textSize = (16f - (distance / 6.25)).toFloat()
                            }
                            //if (distance <= 500){
                                overlayFrame.addView(textView)
                                buildingTextViews[building.name] = textView
                            //}
                        }

                        // Mark building as in view
                        buildingsInView.add(building.name)
                    }

                    // Remove TextViews for buildings no longer in view
                    val buildingsToRemove = buildingTextViews.keys - buildingsInView
                    for (buildingName in buildingsToRemove) {
                        val textView = buildingTextViews[buildingName]
                        overlayFrame.removeView(textView)
                        buildingTextViews.remove(buildingName)
                    }
                }
            }
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available. Restarting camera.", e)
        } catch (e: Exception) {
            Log.e("ARCore", "Error processing frame", e)
        }
    }


    // Camera permissions and setup (remains as is)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        //if (isGranted) startCamera()
        if (!isGranted) Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
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

    private fun stopCamera() {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            cameraProvider.unbindAll() // Unbind all use cases to release resources
            Log.d(TAG, "Released all camera resources.")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera resources", e)
        }
    }

    private fun releaseCameraResources() {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            cameraProvider.unbindAll() // Unbind all use cases to release resources
            Log.d(TAG, "Released all camera resources.")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera resources", e)
        }
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

    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0] // Use back camera
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

            // Create a SurfaceTexture and set it up
            surfaceTexture = SurfaceTexture(0)
            surfaceTexture.setDefaultBufferSize(previewView.width, previewView.height)
            surface = Surface(surfaceTexture)

            // Open the camera
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice?.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                }
            }, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
        }
    }


    private fun startPreview() {
        try {
            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)

            // Create a CameraCaptureSession
            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val captureRequest = captureRequestBuilder?.build()
                            captureSession?.setRepeatingRequest(captureRequest!!, null, null)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start camera preview", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure camera capture session.")
                    }
                },
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start preview", e)
        }
    }


    override fun onPause() {
        super.onPause()
        cameraDevice?.close()
        captureSession?.close()
    }

    override fun onResume() {
        super.onResume()
        if (previewView.holder.surface.isValid) {
            openCamera()
        }
    }
}