package com.wearify.app.data

enum class DataCollectionActivity(val fileToken: String, val menuLabel: String) {
    Default("default", "Default"),
    Resting("resting", "Resting"),
    Walking("walking", "Walking"),
    Running("running", "Running"),
    Other("other", "Other");

    companion object {
        fun fromStoredValue(value: String?): DataCollectionActivity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Default
    }
}

internal fun buildDataCollectionFileName(
    sessionStamp: String,
    activity: DataCollectionActivity,
    suffix: String,
): String = listOf(
    sessionStamp,
    activity.fileToken,
    suffix,
).joinToString(separator = "_", postfix = ".csv")
