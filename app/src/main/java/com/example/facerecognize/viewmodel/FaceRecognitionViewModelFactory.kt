package com.example.facerecognize.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.facerecognize.data.FaceDao
import com.example.facerecognize.data.FaceDatabase
import com.example.facerecognize.data.FaceRepository
import com.example.facerecognize.service.AWSRekognitionService
import com.example.facerecognize.service.FaceRecognitionService
import com.example.facerecognize.service.ImageLibraryLoader
import com.example.facerecognize.utils.AWSCredentialsManager

/**
 * Фабрика для создания FaceRecognitionViewModel
 */
class FaceRecognitionViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FaceRecognitionViewModel::class.java)) {
            // Инициализируем зависимости
            val database = FaceDatabase.getDatabase(application)
            val repository = FaceRepository(database.faceDao())
            val faceRecognitionService = FaceRecognitionService(application)
            val imageLibraryLoader = ImageLibraryLoader(
                context = application,
                faceRecognitionService = faceRecognitionService,
                repository = repository
            )
            val awsRekognitionService = AWSRekognitionService(
                accessKeyId = AWSCredentialsManager.getAccessKey(),
                secretAccessKey = AWSCredentialsManager.getSecretKey(),
                region = AWSCredentialsManager.getRegion()
            )
            
            @Suppress("UNCHECKED_CAST")
            return FaceRecognitionViewModel(
                repository = repository,
                faceRecognitionService = faceRecognitionService,
                imageLibraryLoader = imageLibraryLoader,
                awsRekognitionService = awsRekognitionService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 