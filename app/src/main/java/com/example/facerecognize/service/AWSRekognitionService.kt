package com.example.facerecognize.service

import android.graphics.Bitmap
import android.util.Log
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.rekognition.AmazonRekognitionClient
import com.amazonaws.services.rekognition.model.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.amazonaws.ClientConfiguration

class AWSRekognitionService(
    private val accessKeyId: String,
    private val secretAccessKey: String,
    private val region: String = "us-east-1"
) {
    private val rekognitionClient: AmazonRekognitionClient by lazy {
        val credentials = BasicAWSCredentials(accessKeyId, secretAccessKey)
        val clientConfig = ClientConfiguration()
        clientConfig.connectionTimeout = 30000 // 30 seconds
        clientConfig.socketTimeout = 30000 // 30 seconds

        AmazonRekognitionClient(credentials, clientConfig).apply {
            endpoint = "https://rekognition.us-east-1.amazonaws.com"
        }
    }

    suspend fun compareFaces(
        sourceImage: Bitmap,
        targetImage: Bitmap,
        similarityThreshold: Float = 70f
    ): List<CompareFacesMatch> = withContext(Dispatchers.IO) {
        try {
            val request = CompareFacesRequest()
                .withSourceImage(convertBitmapToImage(sourceImage))
                .withTargetImage(convertBitmapToImage(targetImage))
                .withSimilarityThreshold(similarityThreshold)

            val result = rekognitionClient.compareFaces(request)
            Log.d("AWS", "Found ${result.faceMatches.size} face matches")
            return@withContext result.faceMatches

        } catch (e: Exception) {
            Log.e("AWS", "Error comparing faces: ${e.message}")
            throw e
        }
    }

    suspend fun recognizeCelebrities(image: Bitmap): List<Celebrity> = withContext(Dispatchers.IO) {
        try {
            val request = RecognizeCelebritiesRequest()
                .withImage(convertBitmapToImage(image))

            val result = rekognitionClient.recognizeCelebrities(request)
            Log.d("AWS", "Found ${result.celebrityFaces.size} celebrities")
            return@withContext result.celebrityFaces

        } catch (e: Exception) {
            Log.e("AWS", "Error recognizing celebrities: ${e.message}")
            throw e
        }
    }

    private fun convertBitmapToImage(bitmap: Bitmap): Image {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArray = stream.toByteArray()
        return Image().withBytes(ByteBuffer.wrap(byteArray))
    }

    suspend fun detectFaces(image: Bitmap): List<FaceDetail> = withContext(Dispatchers.IO) {
        try {
            val request = DetectFacesRequest()
                .withImage(convertBitmapToImage(image))
                .withAttributes(listOf("ALL"))

            val result = rekognitionClient.detectFaces(request)
            Log.d("AWS", "Detected ${result.faceDetails.size} faces")
            return@withContext result.faceDetails

        } catch (e: Exception) {
            Log.e("AWS", "Error detecting faces: ${e.message}")
            throw e
        }
    }
} 