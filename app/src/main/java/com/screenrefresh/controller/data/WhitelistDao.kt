package com.screenrefresh.controller.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM whitelist ORDER BY appName")
    fun getAll(): Flow<List<WhitelistEntity>>

    @Query("SELECT * FROM whitelist WHERE packageName = :pkg")
    suspend fun getByPackage(pkg: String): WhitelistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WhitelistEntity)

    @Delete
    suspend fun delete(entity: WhitelistEntity)
}
