package ai.gyango.chatbot.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local SQLite store for per-turn chat telemetry (user question, curiosity snippet, difficulty).
 */
class TurnTelemetryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recorded_at INTEGER NOT NULL,
                subject_key TEXT NOT NULL,
                user_question TEXT NOT NULL,
                curiosity TEXT,
                difficulty_level INTEGER NOT NULL,
                parse_status TEXT,
                parse_reason TEXT,
                topic_contract_valid INTEGER
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN parse_status TEXT")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN parse_reason TEXT")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN topic_contract_valid INTEGER")
        }
    }

    suspend fun insertTurn(
        subjectKey: String,
        userQuestion: String,
        curiosity: String?,
        difficultyLevel: Int,
        parseStatus: String?,
        parseReason: String?,
        topicContractValid: Boolean?,
    ) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("recorded_at", System.currentTimeMillis())
            put("subject_key", subjectKey.take(48))
            put("user_question", userQuestion.take(2000))
            put("curiosity", curiosity?.take(500))
            put("difficulty_level", difficultyLevel.coerceIn(1, 10))
            put("parse_status", parseStatus?.take(24))
            put("parse_reason", parseReason?.take(120))
            if (topicContractValid == null) {
                putNull("topic_contract_valid")
            } else {
                put("topic_contract_valid", if (topicContractValid) 1 else 0)
            }
        }
        writableDatabase.insert(TABLE, null, cv)
    }

    companion object {
        private const val DB_NAME = "gyango_turn_telemetry.db"
        private const val DB_VERSION = 2
        private const val TABLE = "turn_telemetry"
    }
}
