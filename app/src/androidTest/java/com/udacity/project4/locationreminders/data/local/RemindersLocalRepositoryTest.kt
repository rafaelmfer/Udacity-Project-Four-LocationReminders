package com.udacity.project4.locationreminders.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
//Medium Test to test the repository
@MediumTest
class RemindersLocalRepositoryTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: RemindersDatabase

    //Class under test
    private lateinit var remindersLocalRepository: RemindersLocalRepository

    private val reminder = ReminderDTO("title", "description", "location", 123.456, 654.321)

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RemindersDatabase::class.java
        ).allowMainThreadQueries().build()

        remindersLocalRepository = RemindersLocalRepository(database.reminderDao(), Dispatchers.Main)
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun saveReminder_getReminder() = runBlocking {
        remindersLocalRepository.saveReminder(reminder)

        val result = remindersLocalRepository.getReminder(reminder.id)
        result as Result.Success

        MatcherAssert.assertThat(result.data.id, `is`(reminder.id))
        MatcherAssert.assertThat(result.data.title, `is`(reminder.title))
        MatcherAssert.assertThat(result.data.description, `is`(reminder.description))
        MatcherAssert.assertThat(result.data.location, `is`(reminder.location))
        MatcherAssert.assertThat(result.data.latitude, `is`(reminder.latitude))
        MatcherAssert.assertThat(result.data.longitude, `is`(reminder.longitude))
    }

    @Test
    fun saveReminder_getReminderList() = runBlocking {
        remindersLocalRepository.saveReminder(reminder)
        val reminderList = remindersLocalRepository.getReminders()
        reminderList as Result.Success
        MatcherAssert.assertThat(reminderList.data.size, `is`(1))
    }

    @Test
    fun saveReminder_getReminder_thenClearReminderList() = runBlocking {
        remindersLocalRepository.saveReminder(reminder)
        val reminderList = remindersLocalRepository.getReminders()

        remindersLocalRepository.deleteAllReminders()
        val newReminderList = remindersLocalRepository.getReminders()

        reminderList as Result.Success
        newReminderList as Result.Success

        MatcherAssert.assertThat(reminderList.data.size, `is`(1))
        MatcherAssert.assertThat(newReminderList.data.size, `is`(0))
    }
}