package com.x17jiri.Loky

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "send") var send: Boolean,
    @ColumnInfo(name = "receive") var receive: Boolean,
    @ColumnInfo(name = "public_key") var publicKey: String
)

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts")
    fun getAll(): List<Contact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg contacts: Contact)

    @Delete
    fun delete(contact: Contact)
}

@Database(entities = [Contact::class], version = 1)
abstract class AppDatabase: RoomDatabase() {
    abstract fun contactDao(): ContactDao

    companion object {
        private var instance: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "contacts_database"
                ).build()
            }
            return instance!!
        }
    }
}

class ContactsManager(database: AppDatabase) {
	private val database = database
    private val __contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = __contacts

	suspend fun init() {
		loadContacts()
	}

	suspend fun loadContacts() {
		__contacts.value = database.contactDao().getAll()
	}

	suspend fun addContact(contact: Contact) {
		database.contactDao().insertAll(contact)
		loadContacts()
	}

	suspend fun removeContact(contact: Contact) {
		database.contactDao().delete(contact)
		loadContacts()
	}
}
