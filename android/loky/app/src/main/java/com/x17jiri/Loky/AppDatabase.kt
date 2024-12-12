package com.x17jiri.Loky

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
	entities = [
		ContactDBEntity::class,
		PreKeyDBEntity::class,
		Message::class,
		SendChanStateDBEntity::class,
		RecvChanStateDBEntity::class,
	],
	version = 2,
)
abstract class AppDatabase: RoomDatabase() {
	abstract fun contactDao(): ContactDao
	abstract fun preKeyDao(): PreKeyDao
	abstract fun messageDao(): MessageDao
	abstract fun sendChanStateDao(): SendChanStateDao
	abstract fun recvChanStateDao(): RecvChanStateDao
}
