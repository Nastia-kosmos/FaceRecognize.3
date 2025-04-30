package com.example.facerecognize.utils

import android.graphics.Bitmap
import android.graphics.Color
import java.security.MessageDigest

object ImageHashUtils {
    /**
     * Вычисляет перцептивный хеш изображения (pHash)
     * Этот метод нечувствителен к небольшим изменениям в изображении
     */
    fun calculateImageHash(bitmap: Bitmap): String {
        // Уменьшаем изображение до 32x32 для ускорения обработки
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        
        // Преобразуем в оттенки серого и вычисляем среднее значение
        var totalBrightness = 0L
        val brightnessMatrix = Array(32) { IntArray(32) }
        
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val pixel = scaledBitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                brightnessMatrix[y][x] = brightness
                totalBrightness += brightness
            }
        }
        
        val averageBrightness = totalBrightness / (32 * 32)
        
        // Создаем бинарный хеш на основе сравнения с средней яркостью
        val hashBuilder = StringBuilder()
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                hashBuilder.append(if (brightnessMatrix[y][x] > averageBrightness) "1" else "0")
            }
        }
        
        // Преобразуем бинарную строку в MD5 хеш для компактности
        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(hashBuilder.toString().toByteArray())
        
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Вычисляет схожесть двух хешей (расстояние Хэмминга)
     * @return значение от 0 до 1, где 1 - полное совпадение
     */
    fun calculateHashSimilarity(hash1: String, hash2: String): Double {
        if (hash1.length != hash2.length) return 0.0
        
        var differences = 0
        for (i in hash1.indices) {
            if (hash1[i] != hash2[i]) {
                differences++
            }
        }
        
        return 1.0 - (differences.toDouble() / hash1.length)
    }
} 