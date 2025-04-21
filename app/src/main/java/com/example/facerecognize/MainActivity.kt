package com.example.facerecognize

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.facerecognize.data.FaceDatabase
import com.example.facerecognize.data.FaceEntity
import com.example.facerecognize.data.FaceRepository
import com.example.facerecognize.service.FaceRecognitionService
import com.example.facerecognize.service.ImageLibraryLoader
import com.example.facerecognize.service.AWSRekognitionService
import com.example.facerecognize.ui.theme.FaceRecognizeTheme
import com.example.facerecognize.utils.AWSCredentialsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val faceRecognitionService = FaceRecognitionService()
    private val database by lazy { FaceDatabase.getDatabase(this) }
    private val repository by lazy { FaceRepository(database.faceDao()) }
    private val imageLibraryLoader by lazy { 
        ImageLibraryLoader(this, faceRecognitionService, repository)
    }
    private val awsRekognitionService by lazy {
        AWSRekognitionService(
            accessKeyId = AWSCredentialsManager.getAccessKey(),
            secretAccessKey = AWSCredentialsManager.getSecretKey(),
            region = AWSCredentialsManager.getRegion()
        )
    }
    
    val viewModel: FaceRecognitionViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FaceRecognitionViewModel(repository, faceRecognitionService, imageLibraryLoader, awsRekognitionService) as T
            }
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.onImageSelected(it, this) }
    }

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) {
            startImageLibraryLoading()
        } else {
            viewModel.setError("Необходимо предоставить разрешения для работы с файлами")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализация AWS credentials
        AWSCredentialsManager.initialize(this)
        
        // Проверяем соединение с AWS
        viewModel.createCollection()
        
        checkAndRequestPermissions()
        
        setContent {
            FaceRecognizeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FaceRecognitionScreen(
                        viewModel = viewModel,
                        onSelectImage = { getContent.launch("image/*") }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        val hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermissions) {
            startImageLibraryLoading()
        } else {
            requestPermissions.launch(permissions)
        }
    }

    private fun startImageLibraryLoading() {
        // Загружаем библиотеку из assets
        viewModel.loadImageLibrary("archive")
    }
}

@Composable
fun FaceRecognitionScreen(
    viewModel: FaceRecognitionViewModel,
    onSelectImage: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (uiState.isLoadingLibrary) {
            Text("Загрузка библиотеки изображений...")
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }

        Button(
            onClick = onSelectImage,
            modifier = Modifier.padding(vertical = 16.dp),
            enabled = !uiState.isLoadingLibrary
        ) {
            Text("Выбрать изображение")
        }

        uiState.selectedImage?.let { uri ->
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = "Выбранное изображение",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Показываем результаты AWS анализа
            uiState.awsAnalysisResult?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Результаты анализа AWS Rekognition:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodyMedium
    )
}
                }
            }
            
            // Показываем похожие лица
            if (uiState.similarFaces.isNotEmpty()) {
                Text(
                    text = "Похожие лица:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.similarFaces) { (face, similarity) ->
                        SimilarFaceCard(face = face, similarity = similarity)
                    }
                }
            }
        }

        if (uiState.isProcessing) {
            CircularProgressIndicator()
        }

        uiState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun SimilarFaceCard(face: FaceEntity, similarity: Double) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("file:///android_asset/${face.imagePath}")
                        .build()
                ),
                contentDescription = "Похожее лицо",
                modifier = Modifier
                    .size(120.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = face.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            
            Text(
                text = "Сходство: ${(similarity * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

class FaceRecognitionViewModel(
    private val repository: FaceRepository,
    private val faceRecognitionService: FaceRecognitionService,
    private val imageLibraryLoader: ImageLibraryLoader,
    private val awsRekognitionService: AWSRekognitionService
) : ViewModel() {
    private val _uiState = MutableStateFlow(FaceRecognitionUiState())
    val uiState: StateFlow<FaceRecognitionUiState> = _uiState.asStateFlow()

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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedImage = uri,
                isProcessing = true,
                error = null
            )

            try {
                val bitmap = MediaStore.Images.Media.getBitmap(
                    context.contentResolver,
                    uri
                )
                
                // Используем AWS Rekognition для определения лиц и знаменитостей
                val awsFaces = awsRekognitionService.detectFaces(bitmap)
                val celebrities = awsRekognitionService.recognizeCelebrities(bitmap)
                
                if (awsFaces.isNotEmpty()) {
                    // Получаем первое найденное лицо
                    val awsFace = awsFaces[0]
                    
                    // Анализируем результаты AWS
                    val confidenceMessage = "Уверенность определения: ${awsFace.confidence}%"
                    val ageMessage = "Возраст: ${awsFace.ageRange.low}-${awsFace.ageRange.high} лет"
                    val emotionMessage = awsFace.emotions.maxByOrNull { it.confidence }?.let {
                        "Эмоция: ${it.type} (${it.confidence}%)"
                    } ?: "Эмоции не определены"
                    
                    val celebrityInfo = if (celebrities.isNotEmpty()) {
                        val celebrity = celebrities[0]
                        "\n\nРаспознана знаменитость: ${celebrity.name}" +
                        "\nУверенность: ${String.format("%.2f", celebrity.matchConfidence)}%" +
                        "\nИМДб: ${celebrity.urls.firstOrNull() ?: "Нет данных"}"
                    } else {
                        "\n\nЗнаменитости не обнаружены"
                    }
                    
                    val awsInfo = "$confidenceMessage\n$ageMessage\n$emotionMessage$celebrityInfo"

                    // Находим похожие лица в базе данных
                    val faces = faceRecognitionService.detectFaces(bitmap)
                    if (faces.isNotEmpty()) {
                        val face = faces.first()
                        val faceEntity = FaceEntity(
                            name = "Новое лицо",
                            imagePath = uri.toString(),
                            faceEmbedding = face.embedding
                        )
                        
                        val similarFacesWithScores = repository.findSimilarFacesWithScores(faceEntity)
                        
                        _uiState.value = _uiState.value.copy(
                            similarFaces = similarFacesWithScores,
                            isProcessing = false,
                            awsAnalysisResult = awsInfo
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            awsAnalysisResult = awsInfo,
                            error = "Лица не обнаружены локальным сервисом"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = "Лица не обнаружены на изображении"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Ошибка анализа: ${e.message}"
                )
            }
        }
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
}

data class FaceRecognitionUiState(
    val selectedImage: Uri? = null,
    val isProcessing: Boolean = false,
    val isLoadingLibrary: Boolean = false,
    val similarFaces: List<Pair<FaceEntity, Double>> = emptyList(),
    val error: String? = null,
    val awsAnalysisResult: String? = null
)