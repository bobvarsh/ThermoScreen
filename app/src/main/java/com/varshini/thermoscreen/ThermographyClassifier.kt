package com.varshini.thermoscreen

class ThermographyClassifier {package com.varshini.thermoscreen

    import android.content.Context
    import android.graphics.Bitmap
    import org.tensorflow.lite.Interpreter
    import java.io.FileInputStream
    import java.nio.ByteBuffer
    import java.nio.ByteOrder
    import java.nio.MappedByteBuffer
    import java.nio.channels.FileChannel

    class ThermographyClassifier(context: Context) {

        // Must match your Colab preprocessing exactly
        private val imgSize = 128
        private val channels = 3

        private var interpreter: Interpreter

        init {
            val model = loadModelFile(context, "thermography_model.tflite")
            interpreter = Interpreter(model)
        }

        // Loads the .tflite file from the assets folder
        private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
            val fileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }

        // Converts a Bitmap into the exact input format the model expects:
        // resized to 128x128, RGB, pixel values scaled 0-1 (matches img / 255.0 in Colab)
        private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, imgSize, imgSize, true)

            val byteBuffer = ByteBuffer.allocateDirect(4 * imgSize * imgSize * channels)
            byteBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(imgSize * imgSize)
            resizedBitmap.getPixels(intValues, 0, imgSize, 0, 0, imgSize, imgSize)

            var pixel = 0
            for (i in 0 until imgSize) {
                for (j in 0 until imgSize) {
                    val value = intValues[pixel++]

                    // Extract RGB channels and normalize to 0-1 (matches img / 255.0)
                    val r = ((value shr 16) and 0xFF) / 255.0f
                    val g = ((value shr 8) and 0xFF) / 255.0f
                    val b = (value and 0xFF) / 255.0f

                    byteBuffer.putFloat(r)
                    byteBuffer.putFloat(g)
                    byteBuffer.putFloat(b)
                }
            }
            return byteBuffer
        }

        // Runs prediction on a bitmap. Returns a Pair: (label, confidence)
        fun classify(bitmap: Bitmap): Pair<String, Float> {
            val inputBuffer = bitmapToByteBuffer(bitmap)

            // Model outputs a single sigmoid value: probability of malignant (label = 1)
            val output = Array(1) { FloatArray(1) }
            interpreter.run(inputBuffer, output)

            val malignantProbability = output[0][0]
            val label = if (malignantProbability > 0.5f) "Malignant" else "Benign"
            val confidence = if (malignantProbability > 0.5f) malignantProbability else (1 - malignantProbability)

            return Pair(label, confidence)
        }

        // Call this when done with the classifier to free resources
        fun close() {
            interpreter.close()
        }
    }

}