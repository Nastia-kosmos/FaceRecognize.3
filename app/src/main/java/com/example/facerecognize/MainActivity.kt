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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import android.content.Context
import java.util.UUID

// Перемещаем функцию вне классов, чтобы она была доступна всем классам в файле
private fun saveImageToStorage(context: Context, uri: Uri, fileName: String): String? {
    try {
        // Создаем директорию для хранения фотографий
        val directory = File(context.filesDir, "user_photos")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        // Создаем файл для сохранения
        val file = File(directory, fileName)
        
        // Копируем содержимое из Uri в файл
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        return file.absolutePath
    } catch (e: Exception) {
        Log.e("ImageSave", "Ошибка при сохранении изображения: ${e.message}")
        return null
    }
}

// Функция для загрузки изображений в Firebase Storage
suspend fun uploadImageToFirebase(context: Context, uri: Uri, fileName: String): String? {
    return try {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference
        val imagesRef = storageRef.child("faces/$fileName")
        
        // Загрузка файла
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val uploadTask = imagesRef.putStream(inputStream)
            uploadTask.await()
            
            // Получаем URL загруженного файла
            val downloadUrl = imagesRef.downloadUrl.await()
            Log.d("FirebaseStorage", "Изображение успешно загружено: $downloadUrl")
            downloadUrl.toString()
        }
    } catch (e: Exception) {
        Log.e("FirebaseStorage", "Ошибка при загрузке изображения: ${e.message}", e)
        null
    }
}

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
fun PersonInfoForm(
    name: String,
    age: String,
    onNameChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Информация о человеке",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Имя") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true
            )
            
            OutlinedTextField(
                value = age,
                onValueChange = onAgeChange,
                label = { Text("Возраст") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Отмена")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank()
                ) {
                    Text("Готово")
                }
            }
        }
    }
}

@Composable
fun FaceRecognitionScreen(
    viewModel: FaceRecognitionViewModel,
    onSelectImage: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

        // Кнопки управления (показываем только если не отображается форма)
        if (!uiState.showInfoForm) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onSelectImage,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoadingLibrary
                ) {
                    Text("Выбрать изображение")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = { viewModel.resetDatabaseAndReloadLibrary("archive") },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoadingLibrary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Обновить библиотеку")
                }
            }
        }

        // Показываем форму для ввода информации, если нужно
        if (uiState.showInfoForm) {
            uiState.selectedImage?.let { uri ->
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = "Выбранное изображение",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                PersonInfoForm(
                    name = uiState.tempPersonName,
                    age = uiState.tempPersonAge,
                    onNameChange = { viewModel.setTempPersonName(it) },
                    onAgeChange = { 
                        // Проверяем, что ввод - число
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            viewModel.setTempPersonAge(it)
                        }
                    },
                    onSubmit = { 
                        (context as? ComponentActivity)?.let { activity ->
                            viewModel.processSelectedImage(
                                activity, 
                                uiState.tempPersonName, 
                                uiState.tempPersonAge
                            )
                        }
                    },
                    onCancel = { viewModel.cancelInfoForm() }
                )
            }
        } else {
            // Отображаем результаты только если не показывается форма
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
            // Загружаем изображение в зависимости от источника
            val imageModel = when {
                // Изображение из assets
                face.imagePath.startsWith("archive/") -> {
                    ImageRequest.Builder(LocalContext.current)
                        .data("file:///android_asset/${face.imagePath}")
                        .build()
                }
                // Изображение из Firebase Storage (URL)
                face.imagePath.startsWith("http") -> {
                    ImageRequest.Builder(LocalContext.current)
                        .data(face.imagePath)
                        .build()
                }
                // Локальное изображение
                else -> {
                    ImageRequest.Builder(LocalContext.current)
                        .data(File(face.imagePath))
                        .build()
                }
            }
            
            Image(
                painter = rememberAsyncImagePainter(imageModel),
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
            
            if (face.age.isNotEmpty()) {
                Text(
                    text = "${face.age} лет",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
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
        viewModelScope.launch {
            val uri = _uiState.value.selectedImage ?: return@launch
            
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                showInfoForm = false,
                error = null
            )

            try {
                val bitmap = MediaStore.Images.Media.getBitmap(
                    context.contentResolver,
                    uri
                )
                
                // Создаем уникальное имя файла с UUID
                val uuid = UUID.randomUUID().toString()
                val fileName = "face_${name.replace(" ", "_")}_${uuid}.jpg"
                
                // Загружаем изображение в Firebase Storage
                val firebaseImageUrl = uploadImageToFirebase(context, uri, fileName)
                
                // Определяем путь к изображению (Firebase URL или локальный путь)
                val imagePath = if (firebaseImageUrl != null) {
                    // Успешно загружено в Firebase
                    firebaseImageUrl
                } else {
                    // Если загрузка в Firebase не удалась, сохраняем локально
                    Log.w("FaceRecognition", "Не удалось загрузить в Firebase, используем локальное хранилище")
                    val savedImagePath = saveImageToStorage(context, uri, fileName)
                    
                    if (savedImagePath == null) {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            error = "Не удалось сохранить изображение"
                        )
                        return@launch
                    }
                    savedImagePath
                }
                
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
                    
                    val storageInfo = if (firebaseImageUrl != null) {
                        "\n\nИзображение сохранено в Firebase"
                    } else {
                        "\n\nИзображение сохранено локально"
                    }
                    
                    val userInfo = "\n\nИмя: $name" + 
                        (if (age.isNotEmpty()) "\nВозраст: $age лет" else "") +
                        storageInfo
                    
                    val awsInfo = "$confidenceMessage\n$ageMessage\n$emotionMessage$celebrityInfo$userInfo"

                    // Находим похожие лица в базе данных
                    val faces = faceRecognitionService.detectFaces(bitmap)
                    if (faces.isNotEmpty()) {
                        val face = faces.first()
                        val faceEntity = FaceEntity(
                            name = name,
                            imagePath = imagePath,  // Используем путь к сохраненному файлу (Firebase URL или локальный)
                            faceEmbedding = face.embedding,
                            age = age
                        )
                        
                        // Сохраняем в базу данных
                        repository.insertFace(faceEntity)
                        
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
    val tempPersonAge: String = ""
)