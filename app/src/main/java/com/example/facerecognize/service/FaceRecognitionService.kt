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
        val embedding = ArrayList<Float>()
        
        // 1. Нормализуем координаты относительно центра лица
        val centerX = landmarks.values.map { it.x }.average().toFloat()
        val centerY = landmarks.values.map { it.y }.average().toFloat()
        
        // Добавляем нормализованные координаты ключевых точек
        landmarks.values.forEach { point ->
            // Нормализуем координаты относительно центра
            embedding.add((point.x - centerX) / 100f)
            embedding.add((point.y - centerY) / 100f)
        }
        
        // 2. Добавляем расстояния между ключевыми точками
        val landmarksList = landmarks.values.toList()
        for (i in landmarksList.indices) {
            for (j in i + 1 until landmarksList.size) {
                val distance = calculateDistance(landmarksList[i], landmarksList[j])
                embedding.add(distance / 100f) // Нормализуем расстояния
            }
        }
        
        // 3. Добавляем углы между ключевыми точками
        for (i in 0 until landmarksList.size - 2) {
            val angle = calculateAngle(
                landmarksList[i],
                landmarksList[i + 1],
                landmarksList[i + 2]
            )
            embedding.add(angle / 180f) // Нормализуем углы
        }
        
        // 4. Добавляем нормализованные атрибуты лица
        with(attributes) {
            embedding.add(smilingProbability)
            embedding.add(leftEyeOpenProbability)
            embedding.add(rightEyeOpenProbability)
            embedding.add(headEulerAngleX / 180f)
            embedding.add(headEulerAngleY / 180f)
            embedding.add(headEulerAngleZ / 180f)
        }
        
        return embedding.toFloatArray()
    }

    private fun calculateDistance(p1: PointF, p2: PointF): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun calculateAngle(p1: PointF, p2: PointF, p3: PointF): Float {
        val angle1 = kotlin.math.atan2(p2.y - p1.y, p2.x - p1.x)
        val angle2 = kotlin.math.atan2(p3.y - p2.y, p3.x - p2.x)
        var angle = Math.toDegrees((angle2 - angle1).toDouble()).toFloat()
        if (angle < 0) angle += 360f
        return angle
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