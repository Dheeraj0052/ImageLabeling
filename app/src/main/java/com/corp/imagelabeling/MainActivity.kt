package com.corp.imagelabeling

import ai.fritz.core.Fritz
import ai.fritz.vision.FritzVision
import ai.fritz.vision.FritzVisionImage
import ai.fritz.vision.ImageRotation
import ai.fritz.vision.PredictorStatusListener
import ai.fritz.vision.imagelabeling.FritzVisionLabelPredictor
import ai.fritz.vision.imagelabeling.ImageLabelManagedModelFast
import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.TextureView
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.Executors
private const val REQUEST_CODE_PERMISSIONS = 10
class MainActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var viewFinder: TextureView
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewFinder = findViewById(R.id.view_finder)
        Fritz.configure(this, "7b2617273789407fb86fddfe64ae55bf");
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    private fun startCamera() {


        val previewConfig = PreviewConfig.Builder()
            .apply {

                setTargetResolution(Size(1920, 1080))
            }
            .build()


        val preview = Preview(previewConfig)


        preview.setOnPreviewOutputUpdateListener {

            val parent = view_finder.parent as ViewGroup


            parent.removeView(view_finder)
            parent.addView(view_finder, 0)
            view_finder.surfaceTexture = it.surfaceTexture
        }

        val analyzerConfig = ImageAnalysisConfig.Builder().apply {

            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE
            )
        }.build()

        val imageAnalysis = ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(executor, ImageProcessor())
        }


        CameraX.bindToLifecycle(this, preview, imageAnalysis)
    }
    inner class ImageProcessor : ImageAnalysis.Analyzer {
        var predictor: FritzVisionLabelPredictor? = null


        override fun analyze(image: ImageProxy?, rotationDegrees: Int) {


            val mediaImage = image?.image

            val imageRotation = ImageRotation.getFromValue(rotationDegrees)

            val visionImage = FritzVisionImage.fromMediaImage(mediaImage, imageRotation)

            val managedModel = ImageLabelManagedModelFast()

            FritzVision.ImageLabeling.loadPredictor(
                managedModel,
                object : PredictorStatusListener<FritzVisionLabelPredictor> {
                    override fun onPredictorReady(p0: FritzVisionLabelPredictor?) {
                        Log.d("TAG", "Image Labeling predictor is ready")
                        predictor = p0
                    }
                })

            val labelResult = predictor?.predict(visionImage)

            runOnUiThread {
                labelResult?.resultString?.let {
                    val sname = it.split(":")

                    tv_name.text = sname[0]
                } ?: kotlin.run {
                    tv_name.visibility = TextView.INVISIBLE
                }
            }
        }
    }
}
