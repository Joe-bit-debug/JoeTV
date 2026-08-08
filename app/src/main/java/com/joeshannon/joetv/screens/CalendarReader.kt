package com.joeshannon.joetv.screens

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class JoeTvCalendarEvent(
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val location: String?,
    val allDay: Boolean
)

suspend fun loadNextCalendarEvent(
    context: Context
): JoeTvCalendarEvent? = withContext(Dispatchers.IO) {

    val now = System.currentTimeMillis()

    val searchEnd =
        now + 7L * 24L * 60L * 60L * 1000L

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.ALL_DAY
    )

    val uriBuilder =
        CalendarContract.Instances.CONTENT_URI.buildUpon()

    ContentUris.appendId(
        uriBuilder,
        now
    )

    ContentUris.appendId(
        uriBuilder,
        searchEnd
    )

    context.contentResolver.query(
        uriBuilder.build(),
        projection,
        "${CalendarContract.Instances.END} >= ?",
        arrayOf(now.toString()),
        "${CalendarContract.Instances.BEGIN} ASC"
    )?.use { cursor ->

        val titleIndex =
            cursor.getColumnIndexOrThrow(
                CalendarContract.Instances.TITLE
            )

        val beginIndex =
            cursor.getColumnIndexOrThrow(
                CalendarContract.Instances.BEGIN
            )

        val endIndex =
            cursor.getColumnIndexOrThrow(
                CalendarContract.Instances.END
            )

        val locationIndex =
            cursor.getColumnIndexOrThrow(
                CalendarContract.Instances.EVENT_LOCATION
            )

        val allDayIndex =
            cursor.getColumnIndexOrThrow(
                CalendarContract.Instances.ALL_DAY
            )

        while (cursor.moveToNext()) {
            val title =
                cursor.getString(titleIndex)
                    ?.trim()
                    .orEmpty()

            if (title.isNotBlank()) {
                return@withContext JoeTvCalendarEvent(
                    title = title,
                    startTimeMillis = cursor.getLong(beginIndex),
                    endTimeMillis = cursor.getLong(endIndex),
                    location = cursor.getString(locationIndex),
                    allDay = cursor.getInt(allDayIndex) == 1
                )
            }
        }
    }

    null
}