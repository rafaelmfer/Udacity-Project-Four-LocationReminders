package com.udacity.project4.locationreminders.data

import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result

class FakeAndroidDataSource(var reminders: MutableList<ReminderDTO>? = mutableListOf()) : ReminderDataSource {

    companion object {
        const val REMINDER_NOT_FOUND = "Reminders could not found"
    }

    private var shouldReturnError = false

    fun shouldReturnError(value: Boolean) {
        shouldReturnError = value
    }

    override suspend fun getReminders(): Result<List<ReminderDTO>> {
        if (shouldReturnError) {
            return Result.Error(REMINDER_NOT_FOUND)
        }

        reminders?.let { return Result.Success(it) }

        return Result.Error(REMINDER_NOT_FOUND)
    }

    override suspend fun saveReminder(reminder: ReminderDTO) {
        reminders?.add(reminder)
    }

    override suspend fun getReminder(id: String): Result<ReminderDTO> {
        val reminder = reminders?.firstOrNull { reminder ->
            reminder.id == id
        }

        if (reminder != null) {
            return Result.Success(reminder)
        }

        return Result.Error(REMINDER_NOT_FOUND)
    }

    override suspend fun deleteAllReminders() {
        reminders?.clear()
    }
}