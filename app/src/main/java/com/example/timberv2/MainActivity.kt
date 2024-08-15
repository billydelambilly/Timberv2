package com.example.timberv2

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val IMAGE_CAPTURE_CODE = 654
    private val IMAGE_PICK_CODE = 655
    private val PERMISSION_CODE = 321
    private lateinit var innerImage: ImageView
    private lateinit var captureButton: Button
    private lateinit var uploadButton: Button
    private var imageUri: Uri? = null
    private lateinit var classifier: Classifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        innerImage = findViewById(R.id.imageView2)
        captureButton = findViewById(R.id.captureButton)
        uploadButton = findViewById(R.id.uploadButton)

        classifier = Classifier(assets, "model.tflite", "labels.txt", 224)

        captureButton.setOnClickListener {
            if (checkPermission(Manifest.permission.CAMERA)) {
                openCamera()
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CODE)
            }
        }

        uploadButton.setOnClickListener {
            if (checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                pickImageFromGallery()
            } else {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_CODE)
            }
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun openCamera() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "New Picture")
            put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")
        }
        imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        }, IMAGE_CAPTURE_CODE)
    }

    private fun pickImageFromGallery() {
        startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), IMAGE_PICK_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                IMAGE_CAPTURE_CODE -> imageUri?.let { processImage(it) }
                IMAGE_PICK_CODE -> data?.data?.let { processImage(it) }
            }
        }
    }

    private fun processImage(uri: Uri) {
        try {
            innerImage.setImageURI(uri)
            val originalBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            val bitmap = scaleBitmap(originalBitmap, 1024)  // Scale the bitmap down to a width of 1024px
            val results = classifier.recognizeImage(bitmap)
            Log.d("MainActivity", "Detection results: $results")
            drawResultsOnBitmap(bitmap, results)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "An error occurred", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val height = (maxWidth * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
    }

    private fun drawResultsOnBitmap(bitmap: Bitmap, results: List<Classifier.Recognition>) {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 60f
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 2f
        }

        val resultsText = StringBuilder("Results:\n")
        results.forEachIndexed { index, result ->
            val text = if (result.title == "No detection") {
                "No detection"
            } else {
                "${result.title} (${String.format("%.2f", result.confidence * 100)}%)"
            }
            canvas.drawText(text, 10f, (100 + index * 70).toFloat(), textPaint)
            resultsText.append("$text\n")
        }

        runOnUiThread {
            innerImage.setImageBitmap(mutableBitmap)
            findViewById<TextView>(R.id.resultTextView).text = resultsText.toString()
        }
    }
}
