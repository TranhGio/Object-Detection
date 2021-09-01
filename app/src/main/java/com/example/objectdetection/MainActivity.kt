package com.example.objectdetection

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.objectdetection.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class MainActivity : AppCompatActivity() {

    companion object {
        private const val RESULT_LOAD_IMAGE = 123
        private const val MAX_FONT_SIZE = 96F
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1222)
        }
        initListeners()
    }

    private fun initListeners() {
        binding.btnChooseImage.setOnClickListener {
            val intent = Intent().apply {
                type = "image/*"
                action = Intent.ACTION_GET_CONTENT
            }
            startActivityForResult(Intent.createChooser(intent, "Choose a picture"), RESULT_LOAD_IMAGE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == RESULT_LOAD_IMAGE) {
            val selectedUriImage = data?.data
            selectedUriImage?.let { binding.imgLoaded.setImageURI(selectedUriImage) }

            //Run on background thread
            lifecycleScope.launch(Dispatchers.Default) { runObjectDetection(selectedUriImage) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun runObjectDetection(uriImg: Uri?) {
        uriImg?.run {
            val source = ImageDecoder.createSource(contentResolver, uriImg)
            val bitmap = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true)
            // Step 1: Create TFLite's TensorImage object
            val image = TensorImage.fromBitmap(bitmap)

            // Step 2: Initialize the detector object
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(0.3f)  // discard all result less than it
                .build()
            val detector = ObjectDetector.createFromFileAndOptions(
                this@MainActivity,
                "salad.tflite",
                options
            )

            // Step 3: Feed given image to the detector
            val results = detector.detect(image)

            // Step 4: Parse the detection result and show it
            val resultToDisplay = results.map {
                // Get the top-1 category and craft the display text
                val category = it.categories.first()
                val text = "${category.label}, ${category.score.times(100).toInt()}%"

                // Create a data object to display the detection result
                DetectionResult(it.boundingBox, text)
            }
            // Draw the detection result on the bitmap and show it.
            val imgWithResult = drawDetectionResult(bitmap, resultToDisplay)
            runOnUiThread {
                binding.imgLoaded.setImageBitmap(imgWithResult)
            }
        }

    }

    private fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: List<DetectionResult>
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

        detectionResults.forEach {
            // draw bounding box
            pen.color = Color.RED
            pen.strokeWidth = 5F
            pen.style = Paint.Style.STROKE
            val box = it.boundingBox
            canvas.drawRect(box, pen)


            val tagSize = Rect(0, 0, 0, 0)

            // calculate the right font size
            pen.style = Paint.Style.FILL_AND_STROKE
            pen.color = Color.YELLOW
            pen.strokeWidth = 2F

            pen.textSize = MAX_FONT_SIZE
            pen.getTextBounds(it.text, 0, it.text.length, tagSize)
            val fontSize: Float = pen.textSize * box.width() / tagSize.width()

            // adjust the font size so texts are inside the bounding box
            if (fontSize < pen.textSize) pen.textSize = fontSize

            var margin = (box.width() - tagSize.width()) / 2.0F
            if (margin < 0F) margin = 0F
            canvas.drawText(
                it.text, box.left + margin,
                box.top + tagSize.height().times(1F), pen
            )
        }
        return outputBitmap
    }
}
