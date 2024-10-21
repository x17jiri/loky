package com.x17jiri.Loky

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
	entities = [
		Contact::class,
		Message::class
	],
	version = 1
)
abstract class AppDatabase: RoomDatabase() {
	abstract fun contactDao(): ContactDao
	abstract fun messageDao(): MessageDao
}

