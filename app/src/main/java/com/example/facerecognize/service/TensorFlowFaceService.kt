package com.example.facerecognize.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import kotlin.math.sqrt

class TensorFlowFaceService(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(160, 160, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    init {
        try {
            // Загружаем модель из assets
            val modelFile = "facenet_model.tflite"
            interpreter = Interpreter(loadModelFile(modelFile))
            Log.d("TensorFlow", "Модель успешно загружена")
        } catch (e: Exception) {
            Log.e("TensorFlow", "Ошибка при загрузке модели: ${e.message}")
        }
    }

    fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray? {
        return try {
            // Подготавливаем изображение
            var tensorImage = TensorImage.fromBitmap(faceBitmap)
            tensorImage = imageProcessor.process(tensorImage)

            // Создаем выходной массив для эмбеддинга
            val embeddings = Array(1) { FloatArray(512) }

            // Запускаем модель
            interpreter?.run(tensorImage.buffer, embeddings)

            // Нормализуем эмбеддинг
            normalizeEmbedding(embeddings[0])

            Log.d("TensorFlow", "Эмбеддинг успешно получен")
            embeddings[0]
        } catch (e: Exception) {
            Log.e("TensorFlow", "Ошибка при получении эмбеддинга: ${e.message}")
            null
        }
    }

    fun compareFaces(embedding1: FloatArray, embedding2: FloatArray): Float {
        return try {
            var dotProduct = 0f
            var norm1 = 0f
            var norm2 = 0f

            for (i in embedding1.indices) {
                dotProduct += embedding1[i] * embedding2[i]
                norm1 += embedding1[i] * embedding1[i]
                norm2 += embedding2[i] * embedding2[i]
            }

            val similarity = dotProduct / (sqrt(norm1) * sqrt(norm2))
            Log.d("TensorFlow", "Сходство лиц: $similarity")
            similarity
        } catch (e: Exception) {
            Log.e("TensorFlow", "Ошибка при сравнении лиц: ${e.message}")
            0f
        }
    }

    private fun loadModelFile(modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun normalizeEmbedding(embedding: FloatArray) {
        var sum = 0f
        for (value in embedding) {
            sum += value * value
        }
        val norm = sqrt(sum)
        for (i in embedding.indices) {
            embedding[i] = embedding[i] / norm
        }
    }

    fun close() {
        interpreter?.close()
    }
} 