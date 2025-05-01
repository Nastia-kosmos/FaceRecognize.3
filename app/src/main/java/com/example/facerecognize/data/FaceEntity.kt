package com.example.facerecognize.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "faces")
data class FaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val imagePath: String,
    val faceEmbedding: FloatArray,
    val age: String = "",
    val imageHash: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceEntity
        if (id != other.id) return false
        if (name != other.name) return false
        if (imagePath != other.imagePath) return false
        if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false
        if (age != other.age) return false
        if (imageHash != other.imageHash) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + imagePath.hashCode()
        result = 31 * result + faceEmbedding.contentHashCode()
        result = 31 * result + age.hashCode()
        result = 31 * result + imageHash.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
} 