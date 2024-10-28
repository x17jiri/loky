package com.x17jiri.Loky

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

// How often we try to switch keys
val SWITCH_KEYS_INTERVAL: Duration = 1.hours

// How long are keys valid after they are fetched from the prekey store
val KEY_EXPIRE = 30.days

