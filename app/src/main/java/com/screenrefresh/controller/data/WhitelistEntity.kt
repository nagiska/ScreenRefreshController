package com.screenrefresh.controller.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whitelist")
data class WhitelistEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val targetRate: Int = 120
)
