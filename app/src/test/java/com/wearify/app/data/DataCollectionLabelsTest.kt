package com.wearify.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DataCollectionLabelsTest {
    @Test
    fun filenamesContainOnlyTimeActivityAndStream() {
        assertEquals(
            "20000101_000000_000_walking_sensors.csv",
            buildDataCollectionFileName("20000101_000000_000", DataCollectionActivity.Walking, "sensors"),
        )
    }

    @Test
    fun recordingActivitiesRoundTripThroughPreferences() {
        for (activity in DataCollectionActivity.entries) {
            assertEquals(activity, DataCollectionActivity.fromStoredValue(activity.name))
            assertEquals(activity, DataCollectionActivity.fromStoredValue(activity.fileToken))
        }
        assertEquals(DataCollectionActivity.Default, DataCollectionActivity.fromStoredValue("unknown"))
    }
}
