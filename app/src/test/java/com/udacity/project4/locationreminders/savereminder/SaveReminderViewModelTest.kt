package com.udacity.project4.locationreminders.savereminder

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udacity.project4.R
import com.udacity.project4.locationreminders.data.FakeDataSource
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.`is`
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
class SaveReminderViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var reminderDataSource: FakeDataSource
    private lateinit var application: Application

    // Class under test
    private lateinit var saveReminderViewModel: SaveReminderViewModel


    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        reminderDataSource = FakeDataSource()
        saveReminderViewModel = SaveReminderViewModel(application, reminderDataSource)
    }

    @After
    fun clear() {
        stopKoin()
    }

    @Test
    fun `on save Reminder _ check if the title is not empty`() = mainCoroutineRule.runBlockingTest {
        //GIVEN
        val reminder = ReminderDataItem("", "description", "location", 123.456, 654.321, "id")

        //WHEN
        saveReminderViewModel.validateAndSaveReminder(reminder)

        //THEN
        MatcherAssert.assertThat(saveReminderViewModel.showSnackBarInt.value!!, `is`(R.string.err_enter_title))
    }

    @Test
    fun `on save Reminder _ check if the description is not empty`() = mainCoroutineRule.runBlockingTest {
        //GIVEN
        val reminder = ReminderDataItem("title", "", "location", 123.456, 654.321, "id")

        //WHEN
        saveReminderViewModel.validateAndSaveReminder(reminder)

        //THEN
        MatcherAssert.assertThat(saveReminderViewModel.showSnackBarInt.value!!, `is`(R.string.err_enter_description))
    }

    @Test
    fun `on save Reminder _ check if the location is not empty`() = mainCoroutineRule.runBlockingTest {
        //GIVEN
        val reminder = ReminderDataItem("title", "description", "", 123.456, 654.321, "id")

        //WHEN
        saveReminderViewModel.validateAndSaveReminder(reminder)

        //THEN
        MatcherAssert.assertThat(saveReminderViewModel.showSnackBarInt.value!!, `is`(R.string.err_select_location))
    }
}