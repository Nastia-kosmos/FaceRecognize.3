package com.example.facerecognize.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
} 