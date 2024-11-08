package com.x17jiri.Loky

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

// How often to send a message with our current keys
// We send our keys repeatedly because the server may delete old messages,
// so the recipient may not have received our first message with the keys
val KEY_RESEND_INTERVAL: Duration = 1.minutes // 30.minutes
val KEY_RESEND_SEC: Long = KEY_RESEND_INTERVAL.inWholeSeconds

// How often we try to switch keys
val KEY_SWITCH_INTERVAL: Duration = 2.minutes // 1.days
val KEY_SWITCH_SEC: Long = KEY_SWITCH_INTERVAL.inWholeSeconds

// How long are keys valid after they are fetched from the prekey store
val KEY_EXPIRE_DURATION = 20.days
val KEY_EXPIRE_SEC: Long = KEY_EXPIRE_DURATION.inWholeSeconds

val DATA_EXPIRE_DURATION = 2.hours
val DATA_EXPIRE_SEC: Long = DATA_EXPIRE_DURATION.inWholeSeconds
