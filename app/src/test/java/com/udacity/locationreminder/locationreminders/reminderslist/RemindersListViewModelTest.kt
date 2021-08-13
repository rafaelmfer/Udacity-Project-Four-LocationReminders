package com.udacity.locationreminder.locationreminders.reminderslist

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udacity.locationreminder.locationreminders.data.FakeDataSource
import com.udacity.locationreminder.locationreminders.data.dto.ReminderDTO
import com.udacity.locationreminder.utils.MainCoroutineRule
import com.udacity.locationreminder.utils.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class RemindersListViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var reminderDataSource: FakeDataSource
    private lateinit var application: Application

    // Class under test
    private lateinit var remindersListViewModel: RemindersListViewModel


    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        reminderDataSource = FakeDataSource()
        remindersListViewModel = RemindersListViewModel(application, reminderDataSource)
    }

    @After
    fun clear() {
        stopKoin()
    }

    @Test
    fun `on load Reminders _ save reminder`() = mainCoroutineRule.runBlockingTest {
        // GIVEN
        val reminder = ReminderDTO("title", "description", "location", 123.456, 654.321, "id")
        reminderDataSource.saveReminder(reminder)
        //
        remindersListViewModel.loadReminders()

        //THEN
        MatcherAssert.assertThat(remindersListViewModel.remindersList.value!!.size, Matchers.`is`(1))
        MatcherAssert.assertThat(remindersListViewModel.remindersList.value!!.size, Matchers.`is`(Matchers.not(2)))
    }

    @Test
    fun `on load Reminders _ show Error Message`() = mainCoroutineRule.runBlockingTest {
        //GIVEN
        reminderDataSource.shouldReturnError(true)

        //
        remindersListViewModel.loadReminders()

        //THEN
        MatcherAssert.assertThat(remindersListViewModel.showSnackBar.value, Matchers.`is`(FakeDataSource.REMINDER_NOT_FOUND))
    }

    @Test
    fun `on load Reminders _ no Data Result`() = mainCoroutineRule.runBlockingTest {
        //WHEN
        remindersListViewModel.loadReminders()

        //THEN
        MatcherAssert.assertThat(remindersListViewModel.showNoData.getOrAwaitValue(), Matchers.`is`(true))
    }


    @Test
    fun `on load Reminders _ should loading appears`() = mainCoroutineRule.runBlockingTest {
        //WHEN
        mainCoroutineRule.pauseDispatcher()
        remindersListViewModel.loadReminders()

        //THEN
        MatcherAssert.assertThat(remindersListViewModel.showLoading.getOrAwaitValue(), Matchers.`is`(true))
        mainCoroutineRule.resumeDispatcher()
        MatcherAssert.assertThat(remindersListViewModel.showLoading.getOrAwaitValue(), Matchers.`is`(false))
    }
}