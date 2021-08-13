package com.udacity.locationreminder.locationreminders.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.udacity.locationreminder.locationreminders.data.dto.ReminderDTO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
//Unit test the DAO
@SmallTest
class RemindersDaoTest {

    private val reminder = ReminderDTO("title", "description", "location", 123.456, 654.321)
    private lateinit var database: RemindersDatabase

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RemindersDatabase::class.java
        ).build()
    }

    @After
    fun closeDb() = database.close()

    @Test
    fun saveReminderOnDb_getTheSameReminderFromDb() = runBlocking {
        database.reminderDao().saveReminder(reminder)

        val savedReminder = database.reminderDao().getReminderById(reminder.id)

        MatcherAssert.assertThat(savedReminder as ReminderDTO, Matchers.notNullValue())
        MatcherAssert.assertThat(savedReminder.id, `is`(reminder.id))
        MatcherAssert.assertThat(savedReminder.title, `is`(reminder.title))
        MatcherAssert.assertThat(savedReminder.description, `is`(reminder.description))
        MatcherAssert.assertThat(savedReminder.location, `is`(reminder.location))
        MatcherAssert.assertThat(savedReminder.latitude, `is`(reminder.latitude))
        MatcherAssert.assertThat(savedReminder.longitude, `is`(reminder.longitude))
    }

    @Test
    fun saveReminderOnDb_getOnlyOneReminderUsingTheFuctionToGetAllReminders() = runBlocking {
        database.reminderDao().saveReminder(reminder)

        val reminderList = database.reminderDao().getReminders()

        MatcherAssert.assertThat(reminderList.count(), `is`(1))
    }

    @Test
    fun saveReminderOnDb_getThisReminder_ThenDeleteAllReminder() = runBlocking {
        database.reminderDao().saveReminder(reminder)

        val reminderList = database.reminderDao().getReminders()

        database.reminderDao().deleteAllReminders()

        val updatedReminderList = database.reminderDao().getReminders()

        MatcherAssert.assertThat(reminderList.count(), `is`(1))

        MatcherAssert.assertThat(updatedReminderList.count(), `is`(0))
    }
}