package com.example.ai_agent.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_models")
data class ModelEntity(
    @PrimaryKey val path: String,
    val name: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)
