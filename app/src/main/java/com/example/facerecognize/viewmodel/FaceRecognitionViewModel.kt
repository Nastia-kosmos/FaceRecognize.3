package com.example.facerecognize.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.facerecognize.data.FaceEntity
import com.example.facerecognize.data.FaceRepository
import com.example.facerecognize.data.FaceDao
import com.example.facerecognize.data.FaceDatabase
import com.example.facerecognize.service.FaceRecognitionService
import com.example.facerecognize.service.ImageLibraryLoader
import com.example.facerecognize.service.AWSRekognitionService
import com.example.facerecognize.utils.ImageHashUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel для работы с распознаванием лиц
 */
class FaceRecognitionViewModel(
    private val repository: FaceRepository,
    private val faceRecognitionService: FaceRecognitionService,
    private val imageLibraryLoader: ImageLibraryLoader,
    private val awsRekognitionService: AWSRekognitionService
) : ViewModel() {
    private val _uiState = MutableStateFlow(FaceRecognitionUiState())
    val uiState: StateFlow<FaceRecognitionUiState> = _uiState.asStateFlow()

    fun resetDatabaseAndReloadLibrary(libraryPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingLibrary = true,
                error = null
            )

            try {
                // Сначала получаем все лица из базы данных
                val allFaces = repository.getAllFacesSnapshot()
                
                // Фильтруем, оставляя только те, что не из папки assets (пользовательские фото)
                val userFaces = allFaces.filter { !it.imagePath.startsWith("archive/") }
                
                // Очищаем базу данных
                repository.clearDatabase()
                Log.d("FaceRecognition", "База данных очищена")
                
                // Перезагружаем библиотеку
                imageLibraryLoader.loadImagesFromLibrary(libraryPath)
                Log.d("FaceRecognition", "Библиотека перезагружена")
                
                // Восстанавливаем пользовательские фотографии в базе
                userFaces.forEach { face ->
                    repository.insertFace(face)
                }
                Log.d("FaceRecognition", "Восстановлено ${userFaces.size} пользовательских фотографий")
                
                _uiState.value = _uiState.value.copy(
                    isLoadingLibrary = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingLibrary = false,
                    error = "Ошибка сброса базы данных: ${e.message}"
                )
            }
        }
    }

    fun loadImageLibrary(libraryPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingLibrary = true,
                error = null
            )

            try {
                imageLibraryLoader.loadImagesFromLibrary(libraryPath)
                _uiState.value = _uiState.value.copy(
                    isLoadingLibrary = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingLibrary = false,
                    error = "Ошибка загрузки библиотеки: ${e.message}"
                )
            }
        }
    }

    fun onImageSelected(uri: Uri, context: ComponentActivity) {
        _uiState.value = _uiState.value.copy(
            selectedImage = uri,
            showInfoForm = true,
            tempPersonName = "",
            tempPersonAge = "",
            error = null
        )
    }

    fun processSelectedImage(context: ComponentActivity, name: String, age: String) {
        // Реализация метода processSelectedImage
    }

    fun setError(error: String) {
        _uiState.value = _uiState.value.copy(
            error = error
        )
    }
    
    fun createCollection() {
        viewModelScope.launch {
            try {
                // Просто проверяем соединение с AWS
                val testImage = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                awsRekognitionService.detectFaces(testImage)
                Log.d("AWS", "Successfully connected to AWS Rekognition")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка подключения к AWS: ${e.message}"
                )
            }
        }
    }

    fun removeDuplicates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                error = null
            )
            
            try {
                val removedCount = repository.removeDuplicates()
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    message = "Удалено $removedCount дубликатов"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Ошибка при удалении дубликатов: ${e.message}"
                )
            }
        }
    }

    fun setTempPersonName(name: String) {
        _uiState.value = _uiState.value.copy(
            tempPersonName = name
        )
    }

    fun setTempPersonAge(age: String) {
        _uiState.value = _uiState.value.copy(
            tempPersonAge = age
        )
    }

    fun cancelInfoForm() {
        _uiState.value = _uiState.value.copy(
            showInfoForm = false,
            selectedImage = null
        )
    }
}

data class FaceRecognitionUiState(
    val selectedImage: Uri? = null,
    val isProcessing: Boolean = false,
    val isLoadingLibrary: Boolean = false,
    val similarFaces: List<Pair<FaceEntity, Double>> = emptyList(),
    val error: String? = null,
    val awsAnalysisResult: String? = null,
    val showInfoForm: Boolean = false,
    val tempPersonName: String = "",
    val tempPersonAge: String = "",
    val message: String? = null
) 