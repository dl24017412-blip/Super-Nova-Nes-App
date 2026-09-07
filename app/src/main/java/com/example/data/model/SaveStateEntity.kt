package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "save_states")
data class SaveStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val romId: Long,
    val slot: Int,
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val screenshotPath: String? = null
)
