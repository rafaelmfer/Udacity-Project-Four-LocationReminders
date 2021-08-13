package com.udacity.locationreminder.locationreminders

import android.app.Application
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import com.udacity.locationreminder.R
import com.udacity.locationreminder.locationreminders.data.ReminderDataSource
import com.udacity.locationreminder.locationreminders.data.local.LocalDB
import com.udacity.locationreminder.locationreminders.data.local.RemindersLocalRepository
import com.udacity.locationreminder.locationreminders.reminderslist.RemindersListViewModel
import com.udacity.locationreminder.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.locationreminder.util.DataBindingIdlingResource
import com.udacity.locationreminder.util.monitorActivity
import com.udacity.locationreminder.utils.EspressoIdlingResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get

@RunWith(AndroidJUnit4::class)
@LargeTest
//END TO END test to black box test the app
class RemindersActivityTest : KoinTest {

    // Extended Koin Test - embed autoclose @after method to close Koin after every test

    private lateinit var repository: ReminderDataSource
    private lateinit var viewModel: SaveReminderViewModel
    private lateinit var appContext: Application

    @get:Rule
    var activityScenarioRule = ActivityScenarioRule(RemindersActivity::class.java)
    private lateinit var decorView: View

    @get:Rule
    var locationPermission = GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION)

    @get:Rule
    var backgroundLocationPermission = GrantPermissionRule.grant(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    // An idling resource that waits for Data Binding to have no pending bindings.
    private val dataBindingIdlingResource = DataBindingIdlingResource()

    /**
     * As we use Koin as a Service Locator Library to develop our code, we'll also use Koin to test our code.
     * at this step we will initialize Koin related code to be able to use it in out testing.
     */
    @Before
    fun init() {
        stopKoin()//stop the original app koin
        appContext = getApplicationContext()
        val myModule = module {
            viewModel {
                RemindersListViewModel(
                    appContext,
                    get() as ReminderDataSource
                )
            }
            single {
                SaveReminderViewModel(
                    appContext,
                    get() as ReminderDataSource
                )
            }
            single { RemindersLocalRepository(get()) as ReminderDataSource }
            single { LocalDB.createRemindersDao(appContext) }
        }
        //declare a new koin module
        startKoin {
            modules(listOf(myModule))
        }
        //Get our real repository
        repository = get()
        viewModel = get()

        //clear the data to start fresh
        runBlocking {
            repository.deleteAllReminders()
        }

        activityScenarioRule.scenario.onActivity { activity ->
            decorView = activity.window.decorView
        }
    }

    /**
     * Idling resources tell Espresso that the app is idle or busy. This is needed when operations
     * are not scheduled in the main Looper (for example when executed on a different thread).
     */
    @Before
    fun registerIdlingResource() {
        IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource)
        IdlingRegistry.getInstance().register(dataBindingIdlingResource)
    }

    /**
     * Unregister your Idling Resource so it can be garbage collected and does not leak any memory.
     */
    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.countingIdlingResource)
        IdlingRegistry.getInstance().unregister(dataBindingIdlingResource)
    }

    @Test
    fun addReminder_verifyNewItemInTheList() {
        val typingTitle = "Title espresso"
        val typingDescription = "Description espresso"
        val activityScenario = ActivityScenario.launch(RemindersActivity::class.java)
        dataBindingIdlingResource.monitorActivity(activityScenario)

        // Verify: no data is shown
        onView(withId(R.id.noDataTextView)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

        // Click add new task
        onView(withId(R.id.addReminderFAB)).perform(ViewActions.click())

        // Type data
        onView(withId(R.id.reminderTitle)).perform(ViewActions.typeText(typingTitle))
        onView(withId(R.id.reminderDescription)).perform(ViewActions.typeText(typingDescription))
        closeSoftKeyboard()

        // Select location
        onView(withId(R.id.selectLocation)).perform(ViewActions.click())

        // Click any position in the map
        onView(withId(R.id.map)).perform(ViewActions.longClick())
        runBlocking {
            delay(1000)
        }
        // Save location
        onView(withId(R.id.mbt_save_location)).perform(ViewActions.click())

        // Get selected location
        val selectedLocation = viewModel.reminderSelectedLocationStr.value

        // Save
        onView(withId(R.id.saveReminder)).perform(ViewActions.click())

        // Verify: One item is created
        onView(withText(typingTitle)).check(matches(isDisplayed()))
        onView(withText(typingDescription)).check(matches(isDisplayed()))
        onView(withText(selectedLocation)).check(matches(isDisplayed()))

        // Make sure the activity is closed before resetting the db:
        activityScenario.close()
    }

    @Test
    fun addReminder_EmptyTitle_verifyShowErrorMessage() {
        val activityScenario = ActivityScenario.launch(RemindersActivity::class.java)
        dataBindingIdlingResource.monitorActivity(activityScenario)

        // Verify: no data is shown
        onView(withId(R.id.noDataTextView)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

        // Click add new task
        onView(withId(R.id.addReminderFAB)).perform(ViewActions.click())

        // Set location manually
        viewModel.selectedPOI.postValue(PointOfInterest(LatLng(48.859605453592486, 2.294072402754437), null, "Eiffel Tower"))

        // Typing description
        onView(withId(R.id.reminderDescription)).perform(ViewActions.typeText("Description espresso"))
        closeSoftKeyboard()

        // Save reminder
        onView(withId(R.id.saveReminder)).perform(ViewActions.click())

        // Verify error message is shown
        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(matches(withText(R.string.err_enter_title)))

        // Make sure the activity is closed before resetting the db:
        activityScenario.close()
    }

    @Test
    fun addReminder_EmptyDescription_verifyShowErrorMessage() {
        val activityScenario = ActivityScenario.launch(RemindersActivity::class.java)
        dataBindingIdlingResource.monitorActivity(activityScenario)

        // Verify: no data is shown
        onView(withId(R.id.noDataTextView)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

        // Click add new task
        onView(withId(R.id.addReminderFAB)).perform(ViewActions.click())

        // Set location manually
        viewModel.selectedPOI.postValue(PointOfInterest(LatLng(48.859605453592486, 2.294072402754437), null, "Eiffel Tower"))

        // Typing description
        onView(withId(R.id.reminderTitle)).perform(ViewActions.typeText("Title espresso"))
        closeSoftKeyboard()

        // Save reminder
        onView(withId(R.id.saveReminder)).perform(ViewActions.click())

        // Verify error message is shown
        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(matches(withText(R.string.err_enter_description)))

        // Make sure the activity is closed before resetting the db:
        activityScenario.close()
    }

    @Test
    fun addReminder_EmptyLocation_verifyShowErrorMessage() {
        val activityScenario = ActivityScenario.launch(RemindersActivity::class.java)
        dataBindingIdlingResource.monitorActivity(activityScenario)

        // Verify: no data is shown
        onView(withId(R.id.noDataTextView)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

        // Click add new task
        onView(withId(R.id.addReminderFAB)).perform(ViewActions.click())

        // Typing title & description
        onView(withId(R.id.reminderTitle)).perform(ViewActions.typeText("Title espresso"))
        onView(withId(R.id.reminderDescription)).perform(ViewActions.typeText("Description espresso"))
        closeSoftKeyboard()

        // Save reminder
        onView(withId(R.id.saveReminder)).perform(ViewActions.click())

        // Verify error message is shown
        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(matches(withText(R.string.err_select_location)))

        // Make sure the activity is closed before resetting the db:
        activityScenario.close()
    }
}