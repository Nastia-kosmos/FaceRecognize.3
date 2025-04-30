package com.example.facerecognize.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlin.math.sqrt

class FaceRepository(private val faceDao: FaceDao) {
    companion object {
        const val DUPLICATE_THRESHOLD = 0.98
    }

    val allFaces: Flow<List<FaceEntity>> = faceDao.getAllFaces()

    suspend fun insertFace(face: FaceEntity) {
        faceDao.insertFace(face)
    }

    suspend fun deleteFace(face: FaceEntity) {
        faceDao.deleteFace(face)
    }

    suspend fun getFaceById(faceId: Long): FaceEntity? {
        return faceDao.getFaceById(faceId)
    }

    suspend fun faceExistsByPath(imagePath: String): Boolean {
        return faceDao.faceExistsByPath(imagePath) > 0
    }

    suspend fun faceExistsByHash(imageHash: String): Boolean {
        return faceDao.getFaceByHash(imageHash) != null
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
    suspend fun isDuplicate(face: FaceEntity, similarityThreshold: Double = 0.98): Boolean {
        Log.d("DuplicateCheck", "=== Начало проверки на дубликаты ===")
        Log.d("DuplicateCheck", "Проверяем лицо: ${face.name}, возраст: ${face.age}")
        Log.d("DuplicateCheck", "Хеш изображения: ${face.imageHash}")
        
        // Сначала проверяем, есть ли лицо с таким же путем к изображению
        val pathExists = faceExistsByPath(face.imagePath)
        Log.d("DuplicateCheck", "Проверка по пути к файлу: ${face.imagePath} = $pathExists")
        if (pathExists) {
            Log.d("DuplicateCheck", "Найден дубликат по пути к файлу")
            return true
        }
        
        // Проверяем, есть ли изображение с таким же хешем
        val existingFace = faceDao.getFaceByHash(face.imageHash)
        Log.d("DuplicateCheck", "Проверка по хешу: ${existingFace != null}")
        if (existingFace != null) {
            Log.d("DuplicateCheck", "Найден дубликат по хешу изображения: ${existingFace.name}")
            return true
        }
        
        // Затем ищем лица с высоким показателем сходства
        val allFaces = faceDao.getAllFacesForComparison()
        Log.d("DuplicateCheck", "Начинаем сравнение с ${allFaces.size} лицами в базе")
        
        // Проверяем размерность векторов
        Log.d("DuplicateCheck", "Размер вектора нового лица: ${face.faceEmbedding.size}")
        
        for (existingFace in allFaces) {
            Log.d("DuplicateCheck", "Сравнение с ${existingFace.name} (${existingFace.id})")
            Log.d("DuplicateCheck", "Размер вектора существующего лица: ${existingFace.faceEmbedding.size}")
            
            val similarity = calculateSimilarity(face.faceEmbedding, existingFace.faceEmbedding)
            Log.d("DuplicateCheck", """
                Детали сравнения:
                - Новое лицо: ${face.name}
                - Существующее лицо: ${existingFace.name}
                - Сходство: $similarity
                - Порог: $similarityThreshold
                - Разница векторов: ${face.faceEmbedding.size - existingFace.faceEmbedding.size}
            """.trimIndent())
            
            if (similarity >= similarityThreshold) {
                Log.d("DuplicateCheck", "Найден дубликат по сходству векторов!")
                return true
            }
        }
        
        Log.d("DuplicateCheck", "=== Дубликатов не найдено ===")
        return false
    }

    suspend fun clearDatabase() {
        faceDao.deleteAllFaces()
    }

    /**
     * Удаляет дубликаты из базы данных
     * @return Количество удаленных дубликатов
     */
    suspend fun removeDuplicates() {
        Log.d("DuplicateRemoval", "=== Начало процесса удаления дубликатов ===")
        val allFaces = getAllFacesSnapshot()
        Log.d("DuplicateRemoval", "Всего лиц в базе: ${allFaces.size}")

        val duplicatePairs = mutableListOf<Pair<FaceEntity, FaceEntity>>()
        
        for (i in allFaces.indices) {
            for (j in i + 1 until allFaces.size) {
                val face1 = allFaces[i]
                val face2 = allFaces[j]
                
                Log.d("DuplicateRemoval", "Сравнение лиц:")
                Log.d("DuplicateRemoval", "- Лицо 1: ${face1.name} (ID: ${face1.id})")
                Log.d("DuplicateRemoval", "- Лицо 2: ${face2.name} (ID: ${face2.id})")

                val similarity = calculateSimilarity(face1.faceEmbedding, face2.faceEmbedding)
                Log.d("DuplicateRemoval", "Сходство: ${similarity * 100}%")

                if (similarity > DUPLICATE_THRESHOLD) {
                    Log.d("DuplicateRemoval", "Найдена пара дубликатов!")
                    duplicatePairs.add(face1 to face2)
                }
            }
        }

        Log.d("DuplicateRemoval", "Найдено пар дубликатов: ${duplicatePairs.size}")

        // Удаление дубликатов (оставляем более новые записи)
        duplicatePairs.forEach { (face1, face2) ->
            val toDelete = if (face1.timestamp < face2.timestamp) face1 else face2
            Log.d("DuplicateRemoval", """
                Удаление дубликата:
                - ID: ${toDelete.id}
                - Имя: ${toDelete.name}
                - Временная метка: ${toDelete.timestamp}
            """.trimIndent())
            
            deleteFace(toDelete)
        }

        Log.d("DuplicateRemoval", "=== Процесс удаления дубликатов завершен ===")
        Log.d("DuplicateRemoval", "Осталось лиц в базе: ${getAllFacesSnapshot().size}")
    }

    suspend fun getAllFacesSnapshot(): List<FaceEntity> {
        return faceDao.getAllFacesForComparison()
    }

    suspend fun findSimilarFacesWithScores(targetFace: FaceEntity, limit: Int = 5): List<Pair<FaceEntity, Double>> {
        Log.d("FaceComparison", "=== Поиск похожих лиц ===")
        Log.d("FaceComparison", "Целевое лицо: ${targetFace.name}")
        Log.d("FaceComparison", "Лимит результатов: $limit")
        
        val allFaces = faceDao.getAllFacesForComparison()
        Log.d("FaceComparison", "Всего лиц в базе: ${allFaces.size}")
        
        val results = allFaces
            .filter { it.id != targetFace.id }
            .map { face -> 
                val similarity = calculateSimilarity(targetFace.faceEmbedding, face.faceEmbedding)
                Log.d("FaceComparison", "Сравнение с ${face.name}: $similarity")
                Pair(face, similarity)
            }
            .sortedByDescending { it.second }
            .take(limit)
        
        Log.d("FaceComparison", "Найдено похожих лиц: ${results.size}")
        results.forEachIndexed { index, (face, similarity) ->
            Log.d("FaceComparison", "${index + 1}. ${face.name}: ${similarity * 100}%")
        }
        
        return results
    }

    private fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Double {
        Log.d("FaceComparison", "=== Начало сравнения векторов признаков ===")
        Log.d("FaceComparison", "Размер вектора 1: ${embedding1.size}")
        Log.d("FaceComparison", "Размер вектора 2: ${embedding2.size}")
        
        if (embedding1.size != embedding2.size) {
            Log.e("FaceComparison", "Ошибка: разные размеры векторов (${embedding1.size} != ${embedding2.size})")
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
        Log.d("FaceComparison", """
            Результаты сравнения:
            - Скалярное произведение: $dotProduct
            - Норма вектора 1: ${sqrt(norm1)}
            - Норма вектора 2: ${sqrt(norm2)}
            - Косинусное сходство: $similarity
        """.trimIndent())
        
        return similarity
    }

    suspend fun isDuplicate(newFace: FaceEntity): Boolean {
        Log.d("DuplicateCheck", "=== Проверка на дубликаты ===")
        Log.d("DuplicateCheck", "Проверяемое лицо: ${newFace.name}")
        Log.d("DuplicateCheck", "Путь к изображению: ${newFace.imagePath}")

        // Проверка по пути к файлу
        val existsByPath = faceExistsByPath(newFace.imagePath)
        Log.d("DuplicateCheck", "Существует по пути: $existsByPath")
        if (existsByPath) {
            Log.d("DuplicateCheck", "Найден дубликат по пути к файлу")
            return true
        }

        // Проверка по хешу изображения
        val existsByHash = faceExistsByHash(newFace.imageHash)
        Log.d("DuplicateCheck", "Существует по хешу: $existsByHash")
        if (existsByHash) {
            Log.d("DuplicateCheck", "Найден дубликат по хешу изображения")
            return true
        }

        // Проверка по сходству векторов признаков
        val similarFaces = findSimilarFacesWithScores(newFace, limit = 1)
        if (similarFaces.isNotEmpty()) {
            val (mostSimilarFace, similarity) = similarFaces.first()
            Log.d("DuplicateCheck", """
                Наиболее похожее лицо:
                - Имя: ${mostSimilarFace.name}
                - Сходство: ${similarity * 100}%
                - Порог дубликата: ${DUPLICATE_THRESHOLD * 100}%
            """.trimIndent())
            
            if (similarity > DUPLICATE_THRESHOLD) {
                Log.d("DuplicateCheck", "Найден дубликат по сходству векторов признаков")
                return true
            }
        }

        Log.d("DuplicateCheck", "Дубликатов не найдено")
        return false
    }

    /**
     * Проверяет наличие дубликатов в базе данных и выводит подробный отчет
     * @return Список пар дубликатов с информацией о сходстве
     */
    suspend fun checkForDuplicates(): List<Triple<FaceEntity, FaceEntity, Double>> {
        Log.d("DuplicateCheck", "=== Начало полной проверки на дубликаты ===")
        val allFaces = getAllFacesSnapshot()
        Log.d("DuplicateCheck", "Всего лиц в базе: ${allFaces.size}")

        val duplicates = mutableListOf<Triple<FaceEntity, FaceEntity, Double>>()
        
        for (i in allFaces.indices) {
            for (j in i + 1 until allFaces.size) {
                val face1 = allFaces[i]
                val face2 = allFaces[j]
                
                // Проверяем по пути к файлу
                if (face1.imagePath == face2.imagePath) {
                    Log.d("DuplicateCheck", """
                        Найден дубликат по пути к файлу:
                        - Лицо 1: ${face1.name} (ID: ${face1.id})
                        - Лицо 2: ${face2.name} (ID: ${face2.id})
                        - Путь: ${face1.imagePath}
                    """.trimIndent())
                    duplicates.add(Triple(face1, face2, 1.0))
                    continue
                }
                
                // Проверяем по хешу изображения
                if (face1.imageHash == face2.imageHash) {
                    Log.d("DuplicateCheck", """
                        Найден дубликат по хешу изображения:
                        - Лицо 1: ${face1.name} (ID: ${face1.id})
                        - Лицо 2: ${face2.name} (ID: ${face2.id})
                        - Хеш: ${face1.imageHash}
                    """.trimIndent())
                    duplicates.add(Triple(face1, face2, 1.0))
                    continue
                }
                
                // Проверяем по сходству векторов
                val similarity = calculateSimilarity(face1.faceEmbedding, face2.faceEmbedding)
                if (similarity > DUPLICATE_THRESHOLD) {
                    Log.d("DuplicateCheck", """
                        Найден дубликат по сходству векторов:
                        - Лицо 1: ${face1.name} (ID: ${face1.id})
                        - Лицо 2: ${face2.name} (ID: ${face2.id})
                        - Сходство: ${similarity * 100}%
                    """.trimIndent())
                    duplicates.add(Triple(face1, face2, similarity))
                }
            }
        }

        Log.d("DuplicateCheck", "=== Проверка завершена ===")
        Log.d("DuplicateCheck", "Найдено пар дубликатов: ${duplicates.size}")
        
        return duplicates
    }
} 