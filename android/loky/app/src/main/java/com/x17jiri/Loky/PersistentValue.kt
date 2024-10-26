package com.x17jiri.Loky

class PersistentValue<T>(
    initialValue: T,
    val save: (T) -> Unit,
) {
    var __value = initialValue

    var value: T
        get() = __value
        set(newValue) {
            __value = newValue
            save(newValue)
        }
}

