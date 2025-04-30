package com.example.facerecognize.models

data class AWSFaceDetail(
    val confidence: Float?,
    val ageRange: AWSAgeRange?,
    val emotions: List<AWSEmotion>?
)

data class AWSAgeRange(
    val low: Int?,
    val high: Int?
)

data class AWSEmotion(
    val type: String?,
    val confidence: Float?
)

data class AWSCelebrity(
    val name: String?,
    val matchConfidence: Float?,
    val urls: List<String>?
)

fun com.amazonaws.services.rekognition.model.FaceDetail.toAWSFaceDetail(): AWSFaceDetail {
    return AWSFaceDetail(
        confidence = this.confidence,
        ageRange = this.ageRange?.let { AWSAgeRange(it.low, it.high) },
        emotions = this.emotions?.map { 
            AWSEmotion(it.type?.toString(), it.confidence)
        }
    )
}

fun com.amazonaws.services.rekognition.model.Celebrity.toAWSCelebrity(): AWSCelebrity {
    return AWSCelebrity(
        name = this.name,
        matchConfidence = this.matchConfidence,
        urls = this.urls
    )
} 