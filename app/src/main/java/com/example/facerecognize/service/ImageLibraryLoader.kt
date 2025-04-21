package com.example.facerecognize.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.facerecognize.data.FaceEntity
import com.example.facerecognize.data.FaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageLibraryLoader(
    private val context: Context,
    private val faceRecognitionService: FaceRecognitionService,
    private val repository: FaceRepository
) {
    suspend fun loadImagesFromLibrary(libraryPath: String) = withContext(Dispatchers.IO) {
        try {
            // Получаем список файлов из assets/archive
            val files = context.assets.list("archive") ?: emptyArray()
            
            var processedFiles = 0
            val totalFiles = files.size

            files.forEach { fileName ->
                try {
                    if (fileName.lowercase().endsWith(".jpg") || 
                        fileName.lowercase().endsWith(".jpeg") || 
                        fileName.lowercase().endsWith(".png")) {
                        
                        val imagePath = "archive/$fileName"
                        
                        // Проверяем, существует ли уже это изображение в базе
                        if (repository.faceExistsByPath(imagePath) == 0) {
                            // Открываем файл из assets только если его еще нет в базе
                            context.assets.open(imagePath).use { inputStream ->
                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                val faces = faceRecognitionService.detectFaces(bitmap)
                                
                                faces.forEach { face ->
                                    val faceEntity = FaceEntity(
                                        name = fileName.substringBeforeLast("."),
                                        imagePath = imagePath,
                                        faceEmbedding = face.embedding,
                                        age = ""
                                    )
                                    repository.insertFace(faceEntity)
                                }
                            }
                        } else {
                            println("Изображение $imagePath уже существует в базе данных")
                        }
                    }
                } catch (e: Exception) {
                    println("Ошибка при обработке файла $fileName: ${e.message}")
                }
                processedFiles++
                onProgressUpdate(processedFiles, totalFiles)
            }
        } catch (e: Exception) {
            println("Ошибка при загрузке библиотеки: ${e.message}")
            throw e
        }
    }

    private fun onProgressUpdate(processed: Int, total: Int) {
        val progress = (processed.toFloat() / total.toFloat() * 100).toInt()
        println("Обработано: $progress%")
    }
} 