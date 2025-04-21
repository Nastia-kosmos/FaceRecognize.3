package com.example.facerecognize.utils

import android.content.Context
import java.util.Properties

object AWSCredentialsManager {
    private var credentials: Properties? = null

    fun initialize(context: Context) {
        if (credentials == null) {
            credentials = Properties().apply {
                context.assets.open("aws_credentials.properties").use { 
                    load(it)
                }
            }
        }
    }

    fun getAccessKey(): String {
        return credentials?.getProperty("aws_access_key") 
            ?: throw IllegalStateException("AWS credentials not initialized")
    }

    fun getSecretKey(): String {
        return credentials?.getProperty("aws_secret_key")
            ?: throw IllegalStateException("AWS credentials not initialized")
    }

    fun getRegion(): String {
        return credentials?.getProperty("aws_region") ?: "us-east-1"
    }
} 