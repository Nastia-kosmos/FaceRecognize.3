package com.example.facerecognize.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PointF
import android.graphics.Color
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FaceRecognitionService(private val context: Context) {
    private val faceDetector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        
        FaceDetection.getClient(options)
    }

    suspend fun detectFaces(bitmap: Bitmap): List<Face> {
        Log.d("FaceRecognition", "=== Начало процесса распознавания лиц ===")
        Log.d("FaceRecognition", "Размер входного изображения: ${bitmap.width}x${bitmap.height}")
        
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faces = faceDetector.process(inputImage).await()
        Log.d("FaceRecognition", "Обнаружено лиц: ${faces.size}")
        
        val detectedFaces = mutableListOf<Face>()
        
        faces.forEachIndexed { index, face ->
            Log.d("FaceRecognition", """
                Обработка лица #${index + 1}:
                - Уверенность определения: ${face.trackingId}
                - Угол поворота: ${face.headEulerAngleY}
            """.trimIndent())
            
            try {
                val embedding = calculateFaceEmbedding(bitmap, face.boundingBox)
                Log.d("FaceRecognition", "Успешно рассчитан вектор признаков размером: ${embedding.size}")
                detectedFaces.add(Face(embedding))
            } catch (e: Exception) {
                Log.e("FaceRecognition", "Ошибка при расчете вектора признаков для лица #${index + 1}", e)
            }
        }
        
        Log.d("FaceRecognition", "=== Завершение процесса распознавания лиц ===")
        Log.d("FaceRecognition", "Успешно обработано лиц: ${detectedFaces.size}")
        return detectedFaces
    }

    private fun calculateFaceEmbedding(bitmap: Bitmap, boundingBox: Rect): FloatArray {
        Log.d("FaceRecognition", "Начало расчета вектора признаков")
        
        try {
            // Обрезаем изображение по области лица
            val faceBitmap = Bitmap.createBitmap(
                bitmap,
                boundingBox.left.coerceAtLeast(0),
                boundingBox.top.coerceAtLeast(0),
                boundingBox.width().coerceAtMost(bitmap.width - boundingBox.left),
                boundingBox.height().coerceAtMost(bitmap.height - boundingBox.top)
            )
            Log.d("FaceRecognition", "Создано обрезанное изображение лица: ${faceBitmap.width}x${faceBitmap.height}")
            
            // Масштабируем до нужного размера
            val scaledBitmap = Bitmap.createScaledBitmap(faceBitmap, 96, 96, true)
            Log.d("FaceRecognition", "Изображение масштабировано до 96x96")
            
            // Конвертируем в массив float
            val embedding = convertBitmapToEmbedding(scaledBitmap)
            Log.d("FaceRecognition", "Вектор признаков успешно рассчитан")
            
            return embedding
            
        } catch (e: Exception) {
            Log.e("FaceRecognition", "Ошибка при расчете вектора признаков", e)
            throw e
        }
    }

    private fun convertBitmapToEmbedding(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val embedding = FloatArray(width * height * 3)  // RGB channels
        var idx = 0
        
        for (pixel in pixels) {
            embedding[idx++] = Color.red(pixel) / 255f
            embedding[idx++] = Color.green(pixel) / 255f
            embedding[idx++] = Color.blue(pixel) / 255f
        }
        
        return embedding
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                continuation.resume(result)
            }
            addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
        }
    }
}

data class Face(
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Face
        return embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        return embedding.contentHashCode()
    }
}

data class FaceEmbedding(
    val faceBitmap: Bitmap,
    val embedding: FloatArray,
    val confidence: Float,
    val boundingBox: Rect,
    val landmarks: Map<Int, PointF>,
    val attributes: FaceAttributes
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceEmbedding
        return embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        return embedding.contentHashCode()
    }
}

data class FaceAttributes(
    val smilingProbability: Float,
    val leftEyeOpenProbability: Float,
    val rightEyeOpenProbability: Float,
    val headEulerAngleX: Float,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float
)

// End of file