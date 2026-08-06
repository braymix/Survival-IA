package com.survivalwiki.core.data.saved

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Risposta salvata dall'utente per consultazione offline. Il testo delle fonti è denormalizzato
 * qui dentro così che i salvati restino leggibili anche se il corpus cambia versione.
 */
@Entity(tableName = "saved_answers")
data class SavedAnswer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val answerText: String,
    /** Fonti serializzate (JSON) associate alla risposta al momento del salvataggio. */
    val sourcesJson: String,
    val createdAt: Long,
)

@Dao
interface SavedAnswerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(answer: SavedAnswer): Long

    @Query("SELECT * FROM saved_answers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedAnswer>>

    /** Ricerca full-text semplice sui salvati (LIKE; sufficiente per volumi personali). */
    @Query(
        "SELECT * FROM saved_answers WHERE question LIKE '%' || :q || '%' " +
            "OR answerText LIKE '%' || :q || '%' ORDER BY createdAt DESC",
    )
    fun search(q: String): Flow<List<SavedAnswer>>

    @Query("DELETE FROM saved_answers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM saved_answers")
    suspend fun deleteAll()
}

/** Database Room dell'app (cronologia/salvati/note). Il corpus è un DB SQLite separato, sola lettura. */
@Database(entities = [SavedAnswer::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedAnswerDao(): SavedAnswerDao

    companion object {
        const val NAME = "survivalwiki-app.db"
    }
}
