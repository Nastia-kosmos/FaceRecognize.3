package com.example.facerecognize.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class FaceNetModel(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val embeddingDim = 512 // FaceNet embedding dimension
    private val inputSize = 160 // Required input size for the model
    private val TAG = "FaceNetModel"

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    init {
        try {
            val modelFile = FileUtil.loadMappedFile(context, "models/facenet.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4) // Оптимизация производительности
            }
            interpreter = Interpreter(modelFile, options)
            Log.d(TAG, "Модель FaceNet успешно инициализирована")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при инициализации модели: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getFaceEmbedding(face: Bitmap): FloatArray? {
        return try {
            // Подготавливаем изображение
            var tensorImage = TensorImage.fromBitmap(face)
            tensorImage = imageProcessor.process(tensorImage)

            // Создаем выходной массив для эмбеддинга
            val embeddings = Array(1) { FloatArray(embeddingDim) }

            // Запускаем модель
            interpreter?.run(tensorImage.buffer, embeddings)

            // Нормализуем эмбеддинг
            normalizeEmbedding(embeddings[0])

            Log.d(TAG, "Эмбеддинг успешно получен")
            embeddings[0]
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при получении эмбеддинга: ${e.message}")
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
            Log.d(TAG, "Сходство лиц: $similarity")
            similarity
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сравнении лиц: ${e.message}")
            0f
        }
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
        try {
            interpreter?.close()
            Log.d(TAG, "Модель успешно освобождена")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при освобождении модели: ${e.message}")
        }
    }
} 