package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scanned_ips")
data class ScannedIp(
    @PrimaryKey val ip: String,
    val rtt: Long,                  // Round-trip latency in milliseconds
    val tcpPort: Int,               // Tested port (usually 443)
    val operatorName: String,       // Irancell, MCI (Hamrah Aval), RighTel, WiFi
    val isFavorite: Boolean = false,
    val speedKbList: Double = 0.0,  // Estimated speed in KB/s
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_ranges")
data class CustomRange(
    @PrimaryKey val cidr: String,
    val label: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ScannedIpDao {
    @Query("SELECT * FROM scanned_ips ORDER BY rtt ASC")
    fun getAllScannedIps(): Flow<List<ScannedIp>>

    @Query("SELECT * FROM scanned_ips WHERE isFavorite = 1 ORDER BY rtt ASC")
    fun getFavoriteIps(): Flow<List<ScannedIp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIp(ip: ScannedIp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIps(ips: List<ScannedIp>)

    @Update
    suspend fun updateIp(ip: ScannedIp)

    @Delete
    suspend fun deleteIp(ip: ScannedIp)

    @Query("DELETE FROM scanned_ips")
    suspend fun clearAll()

    @Query("DELETE FROM scanned_ips WHERE isFavorite = 0")
    suspend fun clearNonFavorites()
}

@Dao
interface CustomRangeDao {
    @Query("SELECT * FROM custom_ranges ORDER BY timestamp DESC")
    fun getAllRanges(): Flow<List<CustomRange>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRange(range: CustomRange)

    @Delete
    suspend fun deleteRange(range: CustomRange)
}

@Database(entities = [ScannedIp::class, CustomRange::class], version = 1, exportSchema = false)
abstract class LekDatabase : RoomDatabase() {
    abstract fun scannedIpDao(): ScannedIpDao
    abstract fun customRangeDao(): CustomRangeDao

    companion object {
        @Volatile
        private var INSTANCE: LekDatabase? = null

        fun getDatabase(context: android.content.Context): LekDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LekDatabase::class.java,
                    "lek_scanner_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
