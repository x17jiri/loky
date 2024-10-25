package com.x17jiri.Loky

class StoredValue<T>(
    initialValue: T,
    val storeValue: (T) -> Unit,
) {
    var __value = initialValue

    var value: T
        get() = __value
        set(newValue) {
            __value = newValue
            storeValue(newValue)
        }
}

