package com.example.facerecognize.data

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

        // Косинусное сходство
        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }
} 