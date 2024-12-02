package com.example.snar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.location.Location
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
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

//new imports 12/2/2024
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.gson.Gson
import java.io.InputStreamReader

//new code 12/2/2024
data class Building(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val description: String
)


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

private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arSceneView = findViewById(R.id.arSceneView)
        overlayFrame = findViewById(R.id.overlayFrame)

        if (!ensureCameraAndArCoreSupport()) return

        jsonContent = loadBuildingData(this)

        initializeARSession()

        arSceneView.scene.addOnUpdateListener(object : Scene.OnUpdateListener {
            override fun onUpdate(frameTime: FrameTime) {
                val session = arSceneView.session
                if (session != null) {
                    arSceneView.scene.removeOnUpdateListener(this)
                    jsonContent?.let { addAnchorsForBuildings(it) }
                }
            }
        })
    }

    private fun initializeARSession() {
        try {
            val session = Session(this)
            val config = Config(session)
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            session.configure(config)
            arSceneView.setupSession(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ARCore session: ${e.localizedMessage}", e)
            Toast.makeText(this, "Failed to initialize ARCore session.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadBuildingData(context: Context): List<Building> {
        val inputStream = context.assets.open("building_info.json")
        val reader = InputStreamReader(inputStream)
        val gson = Gson()

        val buildings = gson.fromJson(reader, Array<Building>::class.java).toList()
        reader.close()
        return buildings
    }

    private fun ensureCameraAndArCoreSupport(): Boolean {
        if (!isCameraSupported(this)) {
            Toast.makeText(this, "Camera permissions not enabled.", Toast.LENGTH_LONG).show()
            return false
        }
        return true
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

    private fun showInContextUI(context: Context) {
        // Add rationale for requesting camera permissions
    }

    override fun onResume() {
        super.onResume()
        arSceneView.resume()
    }

    override fun onPause() {
        super.onPause()
        arSceneView.pause()
    }

    private fun addAnchorsForBuildings(buildings: List<Building>) {
        val frame = arSceneView.arFrame ?: return

        // Ensure planes are available and being tracked
        val planes = arSceneView.session?.getAllTrackables(Plane::class.java)
        if (planes.isNullOrEmpty() || planes.none { it.trackingState == TrackingState.TRACKING }) {
            Log.e(TAG, "No tracking planes available.")
            return
        }

        buildings.forEach { building ->
            val screenX = (arSceneView.width / 2f)
            val screenY = (arSceneView.height / 2f)

            val hitResults = frame.hitTest(screenX, screenY)
            if (hitResults.isNotEmpty()) {
                val hitResult = hitResults[0]
                val anchor = hitResult.createAnchor()

                val anchorNode = AnchorNode(anchor).apply {
                    setParent(arSceneView.scene)
                }

                ViewRenderable.builder()
                    .setView(this, createBuildingSignView(building))
                    .build()
                    .thenAccept { renderable ->
                        anchorNode.renderable = renderable
                    }
                    .exceptionally {
                        Log.e(TAG, "Error creating renderable: ${it.localizedMessage}", it)
                        null
                    }
            } else {
                Log.e(TAG, "No hit result for building: ${building.name}")
            }
        }
    }

    private fun createBuildingSignView(building: Building): TextView {
        return TextView(this).apply {
            text = "${building.name}\n${building.description}"
            textSize = 16f
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_light))
        }
    }

    companion object {
        private const val TAG = "ARActivity"
        private const val METERS_PER_DEGREE_LATITUDE = 111320.0 // Approximation
        private const val METERS_PER_DEGREE_LONGITUDE = 111320.0 // Varies based on latitude
    }

    data class Location(val latitude: Double, val longitude: Double, val altitude: Double)
    data class Translation(val x: Float, val y: Float, val z: Float)

}

    
/* 12/2/2024





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
    private fun processFrame(session: Session?) {
        try {
            if (session == null) return
            //val session = arCoreSession ?: return // Ensure the session is not null
            // Perform frame processing
            val frame = session.update()
            val camera = frame.camera
            //if (arCoreSessionHelper.isSessionPaused || camera.trackingState == TrackingState.PAUSED) return
            Log.d(TAG, "We are in")
            if (camera.trackingState == TrackingState.TRACKING) {
                Log.d(TAG, "Camera Tracking")
                val earth = session.earth ?: return

                // Map to track TextViews for each building by a unique identifier (e.g., building name or ID)
                val buildingTextViews = mutableMapOf<String, TextView>()

                if (earth.trackingState == TrackingState.TRACKING) {
                    Log.d(TAG, "Earth Tracking")

                    val cameraGeospatialPose = earth.cameraGeospatialPose
                    jsonContent?.forEach { building ->
                        val buildingLat: Double = building.latitude
                        val buildingLon: Double = building.longitude
                        val buildingAlt = building.altitude

                        // Compute distance to the building
                        val result = floatArrayOf(0f)
                        Location.distanceBetween(
                            cameraGeospatialPose.latitude,
                            cameraGeospatialPose.longitude,
                            buildingLat,
                            buildingLon,
                            result
                        )
                        val distance = result[0]
                        val pose = cameraGeospatialPose.getEastUpSouthQuaternion()
                        val p = earth.getPose(43.03982659796824, -71.4539532578147, 50.0, pose.component1(), pose.component2(),pose.component3(),pose.component4())
                        Log.d(TAG, pose.toString())
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


                        val screenX = (screenPosition[0] / screenPosition[3]) * overlayFrame.width
                        val screenY = (screenPosition[1] / screenPosition[3]) * overlayFrame.height
                        Log.d("POSITION", "Screen Position: x=${screenPosition[0]}, y=${screenPosition[1]}")
                        // Dynamically manage TextView for the building
                        var displayText = getString(
                            R.string.building_display,
                            building.name,
                            distance,
                            building.description
                        )
                        val textView = TextView(this).apply {
                            text = displayText
                            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.black))
                            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                            textSize = 16f
                            x = screenX
                            y = screenY
                        }
                        overlayFrame.addView(textView)
                        buildingTextViews[building.name] = textView
                        runOnUiThread {
                            displayText = getString(
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
                                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.black))
                                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.white))
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
                else Log.d(TAG, "EARTHHHHHH")

            }

        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available. Restarting camera.", e)
            //stopCamera() // Stop the camera
            //startCamera() // Restart the camera
        }
        catch (e: Exception) {
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
            // Get available camera IDs
            val cameraId = cameraManager.cameraIdList[0] // Assuming back camera
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

            // Create a surface from the preview texture
            surfaceTexture = CreateSurface(previewView.holder.surface) // or use previewView.getSurfaceTexture()
            surface = Surface(surfaceTexture)

            // Open the camera
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice?.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera Error: $error")
                }
            }, null)
        }
        catch(e: SecurityException){
            e.printStackTrace()
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPreview() {
        try {
            // Create a CameraCaptureSession to start preview
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder?.addTarget(surface)

            // Create CameraCaptureSession
            cameraDevice?.createCaptureSession(
                listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val captureRequest = builder?.build()
                            captureSession?.setRepeatingRequest(captureRequest!!, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure camera capture session.")
                    }
                }, null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        // Release camera resources when activity is paused
        cameraDevice?.close()
        captureSession?.close()
    }


*/

