package com.example.data

import android.content.Context
import androidx.room.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

// --- Models ---

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val personaId: String,
    val isDeepThinking: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val messagesJson: String = "[]" // Moshi-serialized list of ChatMessage
)

// --- Moshi Serializer Helper ---

object ChatSerializer {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, ChatMessage::class.java)
    private val adapter = moshi.adapter<List<ChatMessage>>(listType)

    fun serialize(messages: List<ChatMessage>): String {
        return try {
            adapter.toJson(messages)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun deserialize(json: String): List<ChatMessage> {
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// --- DAO ---

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessionsFlow(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Update
    suspend fun updateSession(session: ChatSession)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()
}

// --- Database ---

@Database(entities = [ChatSession::class], version = 1, exportSchema = false)
abstract class DinoDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao

    companion object {
        @Volatile
        private var INSTANCE: DinoDatabase? = null

        fun getDatabase(context: Context): DinoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DinoDatabase::class.java,
                    "dino_ai_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
