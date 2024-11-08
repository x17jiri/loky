package com.x17jiri.Loky

import android.util.Log

class PersistentValue<T>(
    initialValue: T,
    val save: (T) -> Unit,
) {
    var __value = initialValue

    var value: T
        get() = __value
        set(newValue) {
			Log.d("Locodile **********", "PersistentValue: set: $newValue")
            __value = newValue
            save(newValue)
        }
}

