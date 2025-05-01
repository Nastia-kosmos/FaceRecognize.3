package com.example.facerecognize.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.facerecognize.data.FaceEntity
import com.example.facerecognize.data.FaceRepository
import com.example.facerecognize.utils.ImageHashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class ImageLibraryLoader(
    private val context: Context,
    private val faceRecognitionService: FaceRecognitionService,
    private val repository: FaceRepository
) {
    suspend fun loadImagesFromLibrary(libraryPath: String) = withContext(Dispatchers.IO) {
        try {
            Log.d("ImageLoader", "=== Начало загрузки библиотеки изображений ===")
            val files = context.assets.list("archive") ?: emptyArray()
            Log.d("ImageLoader", "Найдено файлов в архиве: ${files.size}")
            
            var processedFiles = 0
            val totalFiles = files.size

            files.forEach { fileName ->
                try {
                    if (fileName.lowercase().endsWith(".jpg") || 
                        fileName.lowercase().endsWith(".jpeg") || 
                        fileName.lowercase().endsWith(".png")) {
                        
                        val imagePath = "archive/$fileName"
                        Log.d("ImageLoader", "Обработка файла: $imagePath")
                        
                        // Проверяем, существует ли уже это изображение в базе
                        val existingCount = repository.faceExistsByPath(imagePath)
                        Log.d("ImageLoader", "Количество существующих записей с путем $imagePath: $existingCount")
                        
                        if (!existingCount) {
                            Log.d("ImageLoader", "Начинаем обработку нового изображения: $imagePath")
                            context.assets.open(imagePath).use { inputStream ->
                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                Log.d("ImageLoader", "Изображение успешно декодировано, размер: ${bitmap.width}x${bitmap.height}")
                                
                                val faces = faceRecognitionService.detectFaces(bitmap)
                                Log.d("ImageLoader", "Обнаружено лиц: ${faces.size}")
                                
                                faces.forEach { face ->
                                    val imageHash = ImageHashUtils.calculateImageHash(bitmap)
                                    Log.d("ImageLoader", "Рассчитан хеш изображения: $imageHash")
                                    
                                    val faceEntity = FaceEntity(
                                        name = fileName.substringBeforeLast("."),
                                        imagePath = imagePath,
                                        faceEmbedding = face.embedding,
                                        age = "",
                                        imageHash = imageHash
                                    )
                                    
                                    Log.d("ImageLoader", """
                                        Создана сущность лица:
                                        - Имя: ${faceEntity.name}
                                        - Путь: ${faceEntity.imagePath}
                                        - Размер вектора: ${faceEntity.faceEmbedding.size}
                                        - Хеш: ${faceEntity.imageHash}
                                    """.trimIndent())
                                    
                                    if (!repository.isDuplicate(faceEntity)) {
                                        repository.insertFace(faceEntity)
                                        Log.d("ImageLoader", "✅ Успешно добавлено новое лицо: ${faceEntity.name}")
                                    } else {
                                        Log.d("ImageLoader", "❌ Обнаружен дубликат, лицо не добавлено: ${faceEntity.name}")
                                    }
                                }
                            }
                        } else {
                            Log.d("ImageLoader", "Пропуск $imagePath - уже существует в базе")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ImageLoader", "Ошибка при обработке файла $fileName", e)
                }
                processedFiles++
                onProgressUpdate(processedFiles, totalFiles)
            }
            Log.d("ImageLoader", "=== Загрузка библиотеки завершена ===")
        } catch (e: Exception) {
            Log.e("ImageLoader", "Ошибка при загрузке библиотеки", e)
            throw e
        }
    }

    private fun onProgressUpdate(processed: Int, total: Int) {
        val progress = (processed.toFloat() / total.toFloat() * 100).toInt()
        println("Обработано: $progress%")
    }
} 