package com.example.facerecognize.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlin.math.sqrt

class FaceRepository(private val faceDao: FaceDao) {
    val allFaces: Flow<List<FaceEntity>> = faceDao.getAllFaces()

    suspend fun insertFace(face: FaceEntity) {
        faceDao.insertFace(face)
    }

    suspend fun getFaceById(faceId: Long): FaceEntity? {
        return faceDao.getFaceById(faceId)
    }

    suspend fun faceExistsByPath(imagePath: String): Int {
        return faceDao.faceExistsByPath(imagePath)
    }

    suspend fun countFacesByName(name: String): Int {
        return faceDao.countFacesByName(name)
    }

    /**
     * Проверяет, есть ли уже в базе данных похожее лицо
     * @param face Лицо для проверки
     * @param similarityThreshold Порог сходства (0.0-1.0), при превышении которого лица считаются дубликатами
     * @return true если найден дубликат, false если дубликата нет
     */
    suspend fun isDuplicate(face: FaceEntity, similarityThreshold: Double = 0.90): Boolean {
        // Сначала проверяем, есть ли лицо с таким же путем к изображению
        if (faceExistsByPath(face.imagePath) > 0) {
            Log.d("DuplicateCheck", "Найден дубликат по пути к файлу: ${face.imagePath}")
            return true
        }
        
        // Затем ищем лица с высоким показателем сходства
        val allFaces = faceDao.getAllFacesForComparison()
        Log.d("DuplicateCheck", "Начинаем сравнение с ${allFaces.size} лицами в базе")
        
        for (existingFace in allFaces) {
            val similarity = calculateSimilarity(face.faceEmbedding, existingFace.faceEmbedding)
            Log.d("DuplicateCheck", """
                Сравнение:
                - Новое лицо: ${face.name} (длина вектора: ${face.faceEmbedding.size})
                - Существующее: ${existingFace.name} (длина вектора: ${existingFace.faceEmbedding.size})
                - Сходство: $similarity
                - Порог: $similarityThreshold
            """.trimIndent())
            
            if (similarity >= similarityThreshold) {
                Log.d("DuplicateCheck", "Найден дубликат! Сходство $similarity >= $similarityThreshold")
                return true
            }
        }
        
        Log.d("DuplicateCheck", "Дубликатов не найдено (порог сходства: $similarityThreshold)")
        return false
    }

    suspend fun clearDatabase() {
        faceDao.deleteAllFaces()
    }

    /**
     * Удаляет дубликаты из базы данных
     * @return Количество удаленных дубликатов
     */
    suspend fun removeDuplicates(): Int {
        val allFaces = faceDao.getAllFacesForComparison()
        val facesToKeep = mutableListOf<FaceEntity>()
        var duplicatesRemoved = 0
        
        Log.d("DuplicateRemoval", "Начинаем поиск дубликатов среди ${allFaces.size} лиц")
        
        // Сначала очищаем базу данных
        clearDatabase()
        
        // Проходим по всем лицам
        for (face in allFaces) {
            var isDuplicate = false
            
            // Проверяем, нет ли этого лица среди тех, что мы уже решили оставить
            for (keepFace in facesToKeep) {
                val similarity = calculateSimilarity(face.faceEmbedding, keepFace.faceEmbedding)
                if (similarity >= 0.90) {
                    isDuplicate = true
                    duplicatesRemoved++
                    Log.d("DuplicateRemoval", "Найден дубликат: ${face.name} (${face.id}) похож на ${keepFace.name} (${keepFace.id}), сходство: $similarity")
                    break
                }
            }
            
            // Если это не дубликат, добавляем его в список для сохранения
            if (!isDuplicate) {
                facesToKeep.add(face)
                Log.d("DuplicateRemoval", "Сохраняем уникальное лицо: ${face.name} (${face.id})")
            }
        }
        
        // Сохраняем все уникальные лица обратно в базу
        for (face in facesToKeep) {
            faceDao.insertFace(face)
        }
        
        Log.d("DuplicateRemoval", "Удалено дубликатов: $duplicatesRemoved, сохранено уникальных лиц: ${facesToKeep.size}")
        return duplicatesRemoved
    }

    suspend fun getAllFacesSnapshot(): List<FaceEntity> {
        return faceDao.getAllFacesForComparison()
    }

    suspend fun findSimilarFacesWithScores(targetFace: FaceEntity, limit: Int = 5): List<Pair<FaceEntity, Double>> {
        val allFaces = faceDao.getAllFacesForComparison()
        return allFaces
            .filter { it.id != targetFace.id }
            .map { face -> 
                Pair(face, calculateSimilarity(targetFace.faceEmbedding, face.faceEmbedding))
            }
            .sortedByDescending { it.second }
            .take(limit)
    }

    private fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Double {
        if (embedding1.size != embedding2.size) {
            Log.e("DuplicateCheck", "Ошибка: разные размеры векторов (${embedding1.size} != ${embedding2.size})")
            return 0.0
        }

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        val similarity = dotProduct / (sqrt(norm1) * sqrt(norm2))
        Log.d("DuplicateCheck", "Детали расчета сходства:")
        Log.d("DuplicateCheck", "- Скалярное произведение: $dotProduct")
        Log.d("DuplicateCheck", "- Норма1: ${sqrt(norm1)}")
        Log.d("DuplicateCheck", "- Норма2: ${sqrt(norm2)}")
        Log.d("DuplicateCheck", "- Итоговое сходство: $similarity")
        
        return similarity
    }
} 