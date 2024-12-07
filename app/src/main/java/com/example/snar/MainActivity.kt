package com.example.snar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.PixelFormat
import android.location.Location
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View.GONE
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.snar.common.helpers.ARCoreSessionLifecycleHelper
import com.example.snar.common.helpers.Building
import com.example.snar.common.helpers.GeoPermissionsHelper
import com.example.snar.common.helpers.isARCoreSessionAvailable
import com.example.snar.common.helpers.loadBuildingData
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.ar.sceneform.ArSceneView
import java.util.EnumSet

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ARActivity"
    }
    private lateinit var previewView: SurfaceView
    private lateinit var arSceneView: ArSceneView
    private lateinit var overlayFrame: FrameLayout
    private lateinit var surface: Surface

    private lateinit var surfaceView: SurfaceView
    private lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    private var jsonContent: List<Building>? = null

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
            //startFrameUpdateLoop()
            setupFrameUpdateListener()
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
                surface = holder.surface
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.d(TAG, "Surface changed.")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surface.release()
                Log.d(TAG, "Surface destroyed.")
            }
        })
    }

    private fun configureSession(session: Session) {
        session.configure(
            session.config.apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                geospatialMode = Config.GeospatialMode.ENABLED
                cloudAnchorMode = Config.CloudAnchorMode.ENABLED
            }
        )
        val cameraConfigFilter = CameraConfigFilter(session)
        cameraConfigFilter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30)) // Target 30 FPS
        cameraConfigFilter.setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE)) // Skip depth for performance

        val cameraConfigList = session.getSupportedCameraConfigs(cameraConfigFilter)
        if (cameraConfigList.isNotEmpty()) {
            val matchingConfig = cameraConfigList.minByOrNull { config ->
                val cameraAspectRatio = config.imageSize.width.toFloat() / config.imageSize.height.toFloat()
                val screenAspectRatio = overlayFrame.width.toFloat() / overlayFrame.height.toFloat()
                Math.abs(cameraAspectRatio - screenAspectRatio) // Find the closest match
            }

            if (matchingConfig != null) {
                session.cameraConfig = matchingConfig
                Log.d(TAG, "Selected camera resolution: ${matchingConfig.imageSize.width}x${matchingConfig.imageSize.height}")
            }
        }

    }

    private fun setupFrameUpdateListener() {
        arSceneView.scene.addOnUpdateListener(){ _ ->
            // This will be called on every frame update
            val frame = arSceneView.arFrame
            if (frame != null) {
                processARFrames(frame)  // Process the frame as needed
            }
        }
    }

    private fun processARFrames(frame: Frame) {
        arCoreSessionHelper.session?.let { session ->
            try {
                overlayFrame.removeAllViews()

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

                            if (normalizedX < -1 || normalizedX > 1 || normalizedY < -1 || normalizedY > 1) {
                                // Building is outside the camera frame skip rendering
                                return@forEach
                            }

                            val screenX = ((normalizedX + 1) / 2) * overlayFrame.width
                            val screenY = ((1 - normalizedY) / 2) * overlayFrame.height

                            // Update or create TextView for the building
                            val displayText = getString(
                                R.string.building_display,
                                building.name,
                                distance,
                                building.description
                            )
                            val adjTextSize: Float
                            val maxTextSize = 16f // Base text size for the nearest objects
                            val minTextSize = 8f  // Minimum text size for farthest objects
                            val maxDistance = 500f
                            if (distance > maxDistance) {
                                adjTextSize = minTextSize
                            }
                            else if(distance <= 0){
                                adjTextSize = maxTextSize
                            }
                            else{
                                adjTextSize = maxTextSize - ((maxTextSize - minTextSize) * distance/maxDistance)
                            }

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
                                    textSize = adjTextSize
                                }
                                if (distance > maxDistance){
                                    textView.visibility = GONE
                                }
                                runOnUiThread {
                                    overlayFrame.addView(textView)
                                }
                                buildingTextViews[building.name] = textView
                            }

                            // Mark building as in view

                            buildingsInView.add(building.name)
                        }

                        // Remove TextViews for buildings no longer in view
                        val buildingsToRemove = buildingTextViews.keys - buildingsInView
                        for (buildingName in buildingsToRemove) {
                            val textView = buildingTextViews[buildingName]
                            runOnUiThread {
                                overlayFrame.removeView(textView)
                            }
                            buildingTextViews.remove(buildingName)
                        }
                    }
                    else{
                        //
                    }
                }
                else{
                    //
                }
            } catch (e: CameraNotAvailableException) {
                Log.e(TAG, "Camera not available. Restarting camera.", e)
            } catch (e: Exception) {
                Log.e("ARCore", "Error processing frame", e)
            }
        }
    }

    // Camera permissions and setup (remains as is)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
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

    override fun onResume() {
        super.onResume()
        arSceneView.resume()
        setupFrameUpdateListener()
    }

    override fun onPause() {
        super.onPause()
        arSceneView.pause()
    }
    override fun onDestroy() {
        super.onDestroy()
        arSceneView.destroy()
        arCoreSessionHelper.session?.close()
    }

}