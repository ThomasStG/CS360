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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.TextView
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import androidx.appcompat.app.AppCompatActivity  // Ensure you're importing AppCompatActivity
import com.google.ar.sceneform.math.Vector3  // This import is required for Vector3




//ComponentActivity();
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var arSceneView: ArSceneView
    private lateinit var arCoreSession: Session
    private lateinit var arFragment: ArFragment
    lateinit var myRenderable: ModelRenderable



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

        //Start of text mapping
        setContentView(R.layout.activity_main) // Ensure you have an ARFragment in your layout

        // Set up ARFragment
        arFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment

        // Load 3D model and prepare renderable object
        ModelRenderable.builder()
            .setSource(this, Uri.parse("model.sfb"))  // Path to your 3D model file (e.g., .sfb or .glb)
            .build()
            .thenAccept { renderable ->
                myRenderable = renderable
            }


        // Handle tap on AR plane to place text
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            // Create an anchor at the tapped position
            val anchor = hitResult.createAnchor()
            placeTextInAr(anchor)
        }
    }
    // Load a model and create a renderable object

    private fun placeTextInAr(anchor: Anchor) {
        // Step 1: Generate the bitmap from the text
        val bitmap = createTextBitmap(this, "Hi Jace...", 30f, 500, 200)

        // Step 2: Create a texture from the bitmap
        Texture.builder()
            .setSource(bitmap)
            .build()
            .thenAccept { texture ->
                // Step 3: Load a plane model (we will use a simple plane model here)
                MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(android.graphics.Color.WHITE))
                    .thenAccept { material ->
                        // Step 4: Create the ModelRenderable (with texture)
                        ModelRenderable.builder()
                            .setSource(this, Uri.parse("model_plane.sfb")) // Use your plane model
                            .build()
                            .thenAccept { renderable ->
                                // Step 5: Apply the texture to the renderable
                                renderable.material.setTexture("baseColor", texture)

                                // Step 6: Create a Node to hold the plane
                                val node = Node().apply {
                                    setParent(arFragment.arSceneView.scene)
                                    val anchorPosTranslate = Vector3(anchor.pose.translation[0], anchor.pose.translation[1], anchor.pose.translation[2])
                                    this.worldPosition = anchorPosTranslate // Set position at the anchor
                                    myRenderable = renderable // Attach the plane model with texture
                                }

                                // Step 7: Optionally, make the node transformable (for user interaction)
                                val transformableNode = TransformableNode(arFragment.transformationSystem)
                                transformableNode.setParent(node)
                                transformableNode.renderable = renderable
                            }
                            .exceptionally { throwable ->
                                Toast.makeText(this, "Error loading plane model: ${throwable.message}", Toast.LENGTH_SHORT).show()
                                null
                            }
                    }
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

    fun createTextBitmap(context: Context, text: String, textSize: Float, width: Int, height: Int): Bitmap {
        // Step 1: Create a TextView and set the text
        val textView = TextView(context)
        textView.text = text
        textView.textSize = textSize
        textView.setTextColor(Color.BLACK)  // Set text color

        // Step 2: Measure the TextView
        textView.measure(width, height)  // You may adjust width/height to fit the text
        textView.layout(0, 0, textView.measuredWidth, textView.measuredHeight)  // Layout the text

        // Step 3: Create a Bitmap with the same dimensions as the TextView
        val bitmap = Bitmap.createBitmap(textView.measuredWidth, textView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Step 4: Draw the TextView onto the canvas (bitmap)
        textView.draw(canvas)

        return bitmap
    }




}
