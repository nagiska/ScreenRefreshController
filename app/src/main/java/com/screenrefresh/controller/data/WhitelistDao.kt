package com.screenrefresh.controller.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {

    @Query("SELECT * FROM whitelist ORDER BY app_name ASC")
    fun getAllFlow(): Flow<List<WhitelistEntity>>

    @Query("SELECT * FROM whitelist ORDER BY app_name ASC")
    suspend fun getAll(): List<WhitelistEntity>

    @Query("SELECT package_name FROM whitelist")
    suspend fun getAllPackageNames(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM whitelist WHERE package_name = :packageName)")
    suspend fun isWhitelisted(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WhitelistEntity)

    @Delete
    suspend fun delete(entity: WhitelistEntity)

    @Query("DELETE FROM whitelist WHERE package_name = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("SELECT COUNT(*) FROM whitelist")
    suspend fun count(): Int
}
