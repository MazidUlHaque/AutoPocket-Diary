package com.mazidulhaque.autopocket.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mazidulhaque.autopocket.security.DatabaseKey
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deleted = 0 AND hidden = :hidden AND timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun inRange(from: Long, to: Long, hidden: Boolean): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND timestamp >= :from AND timestamp < :to")
    suspend fun inRangeOnce(from: Long, to: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE deleted = 0")
    suspend fun allOnce(): List<Transaction>

    @Query("SELECT DISTINCT source FROM transactions WHERE deleted = 0 AND hidden = 0 ORDER BY source")
    fun sources(): Flow<List<String>>

    @Query("SELECT * FROM transactions WHERE category IS NULL")
    suspend fun uncategorized(): List<Transaction>

    /** Already-imported SMS are skipped, so the user's edits are kept. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<Transaction>): List<Long>

    @Insert
    suspend fun insert(item: Transaction): Long

    @Update
    suspend fun update(item: Transaction)

    @Update
    suspend fun updateAll(items: List<Transaction>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteForever(id: Long)

    @Query("UPDATE transactions SET transfer = 1 WHERE id IN (:ids)")
    suspend fun markTransfers(ids: List<Long>)

    @Query("UPDATE transactions SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    /** Used when the hidden zone is reset after a forgotten PIN. */
    @Query("DELETE FROM transactions WHERE hidden = 1 AND smsId IS NULL")
    suspend fun deleteHiddenManual()

    @Query("UPDATE transactions SET deleted = 1, rawMessage = NULL, title = NULL, note = NULL, counterparty = NULL WHERE hidden = 1")
    suspend fun deleteHiddenSms()

    @Query("SELECT * FROM category_rules")
    suspend fun rules(): List<CategoryRule>

    @Query("SELECT * FROM budgets ORDER BY category")
    fun budgets(): Flow<List<Budget>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(budget: Budget)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteBudget(category: String)

    // ----- accounts -----

    @Query("SELECT * FROM accounts ORDER BY source, isCard, last4")
    fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts")
    suspend fun accountsOnce(): List<Account>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAccounts(items: List<Account>)

    @Update
    suspend fun updateAccount(item: Account)

    @Query("UPDATE accounts SET lastBalance = :balance WHERE `key` = :key")
    suspend fun setAccountBalance(key: String, balance: Double)

    // ----- review -----

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND hidden = 0 AND review IS NOT NULL ORDER BY timestamp DESC")
    fun needsReview(): Flow<List<Transaction>>

    @Query("UPDATE transactions SET review = :review, pairId = :pairId WHERE id = :id")
    suspend fun setReview(id: Long, review: String?, pairId: Long?)

    @Query("UPDATE transactions SET transfer = :transfer, review = NULL, pairId = NULL, edited = 1 WHERE id IN (:ids)")
    suspend fun settleTransfer(ids: List<Long>, transfer: Boolean)

    /** SMS entries from before accounts existed, to be read again with the newer reader. */
    @Query("SELECT * FROM transactions WHERE smsId IS NOT NULL AND accountKey IS NULL AND rawMessage IS NOT NULL")
    suspend fun needingRepair(): List<Transaction>

    @Query("UPDATE transactions SET otherBank = :bank WHERE id = :id AND otherBank IS NULL")
    suspend fun setOtherBank(id: Long, bank: String)

    @Query("UPDATE transactions SET category = :to WHERE category = :from")
    suspend fun renameCategory(from: String, to: String)

    /** Emits whenever entries change (used to refresh the home-screen widget). */
    @Query("SELECT COUNT(*) FROM transactions")
    fun changes(): Flow<Int>

    /** Everything, including deleted SMS markers (so a restore on the same phone doesn't re-import them). */
    @Query("SELECT * FROM transactions")
    suspend fun everything(): List<Transaction>

    /** Date, amount and account of every SMS entry, to avoid duplicates after restoring on a new phone. */
    @Query("SELECT timestamp, amount, source FROM transactions WHERE smsId IS NOT NULL")
    suspend fun smsKeys(): List<SmsKey>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM category_rules")
    suspend fun deleteAllRules()

    @Insert
    suspend fun insertMany(items: List<Transaction>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRules(rules: List<CategoryRule>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(rule: CategoryRule)
}

data class SmsKey(val timestamp: Long, val amount: Double, val source: String)

@Database(entities = [Transaction::class, CategoryRule::class, Budget::class, Account::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao

    companion object {
        private const val NAME = "autopocket.db"

        /** Version 1 → 2: categories, transfers, deleted and edited flags, remembered categories. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN transfer INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN edited INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_rules` (`merchant` TEXT NOT NULL, `category` TEXT NOT NULL, PRIMARY KEY(`merchant`))",
                )
            }
        }

        /** Version 2 → 3: hidden zone. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Version 3 → 4: monthly budgets. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`category` TEXT NOT NULL, `amount` REAL NOT NULL, PRIMARY KEY(`category`))")
            }
        }

        /** Version 4 → 5: accounts and cards, foreign currency, balances, the review list. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("accountKey TEXT", "currency TEXT", "foreignAmount REAL", "balance REAL", "otherBank TEXT", "review TEXT", "pairId INTEGER")
                    .forEach { db.execSQL("ALTER TABLE transactions ADD COLUMN $it") }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (`key` TEXT NOT NULL, `source` TEXT NOT NULL, `sourceType` TEXT NOT NULL, " +
                        "`last4` TEXT, `isCard` INTEGER NOT NULL, `creditCard` INTEGER, `type` TEXT, `name` TEXT, `parentKey` TEXT, " +
                        "`confirmed` INTEGER NOT NULL DEFAULT 0, `firstSeen` INTEGER NOT NULL, `lastBalance` REAL, PRIMARY KEY(`key`))",
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: open(context.applicationContext).also { instance = it }
        }

        private fun open(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val key = DatabaseKey.get(context) ?: run {
                // The phone's keystore lost our key (very rare, e.g. after some system resets).
                // The old encrypted file can't be read any more, so start fresh.
                context.deleteDatabase(NAME)
                DatabaseKey.reset(context)
                DatabaseKey.get(context)!!
            }
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }
    }
}
