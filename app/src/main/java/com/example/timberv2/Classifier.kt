package com.example.timberv2

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Classifier(
    private val assetManager: AssetManager,
    private val modelPath: String,
    private val labelPath: String,
    private val inputSize: Int
) {
    private lateinit var interpreter: Interpreter
    private lateinit var labels: List<String>
    private val confidenceThreshold = 0.5f // Set your confidence threshold here

    init {
        try {
            interpreter = Interpreter(loadModelFile(modelPath))
            labels = loadLabelList(labelPath)
            Log.d("Classifier", "Model and labels loaded successfully.")
        } catch (e: Exception) {
            Log.e("Classifier", "Failed to initialize classifier.", e)
        }
    }

    data class Recognition(
        val id: String,
        val title: String,
        val confidence: Float,
        var location: RectF? = null
    )

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabelList(labelPath: String): List<String> {
        return assetManager.open(labelPath).bufferedReader().useLines { it.toList() }
    }

    fun recognizeImage(bitmap: Bitmap): List<Recognition> {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, false)
        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)
        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(byteBuffer, output)
        return decodePredictions(output[0])
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) {
            byteBuffer.putFloat(((pixel shr 16 and 0xFF) - 128f) / 128f)
            byteBuffer.putFloat(((pixel shr 8 and 0xFF) - 128f) / 128f)
            byteBuffer.putFloat(((pixel and 0xFF) - 128f) / 128f)
        }
        return byteBuffer
    }

    private fun decodePredictions(outputs: FloatArray): List<Recognition> {
        val recognitions = mutableListOf<Recognition>()
        outputs.indices.filter { outputs[it] > confidenceThreshold }.forEach {
            recognitions.add(Recognition(it.toString(), labels[it], outputs[it]))
        }
        if (recognitions.isEmpty()) {
            recognitions.add(Recognition("0", "No detection", 0.0f))
        }
        return recognitions
    }
}
