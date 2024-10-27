package com.x17jiri.Loky

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
	entities = [
		ContactDBEntity::class,
		Message::class,
		SendChanStateDBEntity::class,
		RecvChanStateDBEntity::class,
	],
	version = 1
)
abstract class AppDatabase: RoomDatabase() {
	abstract fun contactDao(): ContactDao
	abstract fun messageDao(): MessageDao
}

