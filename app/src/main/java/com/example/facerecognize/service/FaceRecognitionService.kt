package com.example.facerecognize.service

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PointF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FaceRecognitionService {
    private val faceDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    suspend fun detectFaces(bitmap: Bitmap): List<FaceEmbedding> = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faces = suspendCancellableCoroutine { continuation ->
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    continuation.resume(faces)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
        
        faces.map { face ->
            val faceBitmap = extractFaceBitmap(bitmap, face.boundingBox)
            val landmarks = extractFacialLandmarks(face)
            val attributes = extractFaceAttributes(face)
            
            FaceEmbedding(
                faceBitmap = faceBitmap,
                embedding = generateEmbedding(landmarks, attributes),
                confidence = face.trackingId?.toFloat() ?: 0f,
                boundingBox = face.boundingBox,
                landmarks = landmarks,
                attributes = attributes
            )
        }
    }

    private fun extractFaceBitmap(originalBitmap: Bitmap, boundingBox: Rect): Bitmap {
        // Добавляем отступ вокруг лица для лучшего распознавания
        val padding = (boundingBox.width() * 0.2f).toInt()
        
        val left = (boundingBox.left - padding).coerceAtLeast(0)
        val top = (boundingBox.top - padding).coerceAtLeast(0)
        val width = (boundingBox.width() + 2 * padding)
            .coerceAtMost(originalBitmap.width - left)
        val height = (boundingBox.height() + 2 * padding)
            .coerceAtMost(originalBitmap.height - top)

        return Bitmap.createBitmap(
            originalBitmap,
            left, top, width, height
        )
    }

    private fun extractFacialLandmarks(face: Face): Map<Int, PointF> {
        return mapOf(
            FaceLandmark.NOSE_BASE to face.getLandmark(FaceLandmark.NOSE_BASE)?.position,
            FaceLandmark.LEFT_EYE to face.getLandmark(FaceLandmark.LEFT_EYE)?.position,
            FaceLandmark.RIGHT_EYE to face.getLandmark(FaceLandmark.RIGHT_EYE)?.position,
            FaceLandmark.MOUTH_LEFT to face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position,
            FaceLandmark.MOUTH_RIGHT to face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position,
            FaceLandmark.MOUTH_BOTTOM to face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        ).filterValues { it != null }.mapValues { it.value!! }
    }

    private fun extractFaceAttributes(face: Face): FaceAttributes {
        return FaceAttributes(
            smilingProbability = face.smilingProbability ?: 0f,
            leftEyeOpenProbability = face.leftEyeOpenProbability ?: 0f,
            rightEyeOpenProbability = face.rightEyeOpenProbability ?: 0f,
            headEulerAngleX = face.headEulerAngleX,
            headEulerAngleY = face.headEulerAngleY,
            headEulerAngleZ = face.headEulerAngleZ
        )
    }

    private fun generateEmbedding(
        landmarks: Map<Int, PointF>,
        attributes: FaceAttributes
    ): FloatArray {
        // Создаем вектор признаков на основе ключевых точек и атрибутов лица
        val embedding = ArrayList<Float>()

        // Добавляем нормализованные координаты ключевых точек
        landmarks.values.forEach { point ->
            embedding.add(point.x)
            embedding.add(point.y)
        }

        // Добавляем атрибуты лица
        embedding.add(attributes.smilingProbability)
        embedding.add(attributes.leftEyeOpenProbability)
        embedding.add(attributes.rightEyeOpenProbability)
        embedding.add(attributes.headEulerAngleX)
        embedding.add(attributes.headEulerAngleY)
        embedding.add(attributes.headEulerAngleZ)

        return embedding.toFloatArray()
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