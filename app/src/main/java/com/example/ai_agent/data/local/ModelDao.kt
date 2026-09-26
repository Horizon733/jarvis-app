package com.example.ai_agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM installed_models ORDER BY dateAdded DESC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM installed_models WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveModel(): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)

    @Update
    suspend fun updateModel(model: ModelEntity)

    @Delete
    suspend fun deleteModel(model: ModelEntity)

    @Query("UPDATE installed_models SET isActive = 0")
    suspend fun deactivateAll()

    @Transaction
    suspend fun activateModel(model: ModelEntity) {
        deactivateAll()
        insertModel(model.copy(isActive = true))
    }
}
