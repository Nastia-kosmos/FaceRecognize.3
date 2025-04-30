package com.example.facerecognize.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Dao
interface FaceDao {
    @Insert
    suspend fun insertFace(face: FaceEntity)

    @Query("SELECT * FROM faces")
    fun getAllFaces(): Flow<List<FaceEntity>>

    @Query("SELECT * FROM faces WHERE id = :faceId")
    suspend fun getFaceById(faceId: Long): FaceEntity?

    @Query("SELECT * FROM faces")
    suspend fun getAllFacesForComparison(): List<FaceEntity>
    
    @Query("SELECT COUNT(*) FROM faces WHERE imagePath = :imagePath")
    suspend fun faceExistsByPath(imagePath: String): Int
    
    @Query("SELECT COUNT(*) FROM faces WHERE name = :name")
    suspend fun countFacesByName(name: String): Int
    
    @Query("DELETE FROM faces")
    suspend fun deleteAllFaces()
}

// Функция для загрузки изображений в Firebase Storage
suspend fun uploadImageToFirebase(context: Context, uri: Uri, fileName: String): String? {
    Log.d("FirebaseStorage", "Начинаем загрузку файла: $fileName")
    Log.d("FirebaseStorage", "URI изображения: $uri")
    
    // Проверяем доступность сети
    try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val isConnected = networkCapabilities != null && 
            (networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || 
             networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR))
        
        Log.d("FirebaseStorage", "Соединение с интернетом: $isConnected")
        
        if (!isConnected) {
            Log.e("FirebaseStorage", "Нет соединения с интернетом")
            return null
        }
    } catch (e: Exception) {
        Log.e("FirebaseStorage", "Ошибка при проверке соединения: ${e.message}")
    }
    
    // Проверяем URI
    if (uri.toString().isEmpty()) {
        Log.e("FirebaseStorage", "Ошибка: пустой URI")
        return null
    }
    
    return try {
        val storage = FirebaseStorage.getInstance()
        Log.d("FirebaseStorage", "Firebase Storage инициализирован")
        
        val storageRef = storage.reference
        val imagesRef = storageRef.child("faces/$fileName")
        Log.d("FirebaseStorage", "Подготовлен reference: faces/$fileName")
        
        // Загрузка файла
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Log.e("FirebaseStorage", "Не удалось открыть входной поток для URI: $uri")
            return null
        }
        
        inputStream.use { stream ->
            try {
                Log.d("FirebaseStorage", "Начинаем загрузку...")
                val uploadTask = imagesRef.putStream(stream)
                
                // Отслеживаем прогресс загрузки
                uploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                    Log.d("FirebaseStorage", "Прогресс загрузки: $progress%")
                }
                
                uploadTask.await()
                Log.d("FirebaseStorage", "Загрузка завершена успешно")
                
                // Получаем URL загруженного файла
                try {
                    val downloadUrl = imagesRef.downloadUrl.await()
                    Log.d("FirebaseStorage", "Получен URL: $downloadUrl")
                    return@use downloadUrl.toString()
                } catch (e: Exception) {
                    Log.e("FirebaseStorage", "Ошибка при получении URL: ${e.message}")
                    e.printStackTrace()
                    return@use null
                }
            } catch (e: Exception) {
                Log.e("FirebaseStorage", "Ошибка при загрузке файла: ${e.message}")
                e.printStackTrace()
                return@use null
            }
        }
    } catch (e: Exception) {
        Log.e("FirebaseStorage", "Ошибка при инициализации Firebase Storage: ${e.message}")
        e.printStackTrace()
        null
    }
} 