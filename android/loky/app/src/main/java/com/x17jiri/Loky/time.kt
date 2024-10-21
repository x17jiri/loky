package com.x17jiri.Loky

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

val referenceTime =
	ZonedDateTime.of(
		2000, 1, 1,
		0, 0, 0, 0,
		ZoneOffset.UTC
	).toInstant()

fun monotonicSeconds(): Long {
	return Duration.between(referenceTime, Instant.now()).seconds
}
