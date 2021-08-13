package com.udacity.project4.locationreminders.savereminder

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.PointOfInterest
import com.udacity.project4.R
import com.udacity.project4.base.BaseViewModel
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.SingleLiveEvent
import kotlinx.coroutines.launch

class SaveReminderViewModel(val app: Application, private val dataSource: ReminderDataSource) : BaseViewModel(app) {

    val addGeoFencingRequest = SingleLiveEvent<ReminderDataItem>()

    val selectedPOI = MutableLiveData<PointOfInterest>()
    val reminderTitle = MutableLiveData<String>()
    val reminderDescription = MutableLiveData<String>()
    val reminderSelectedLocationStr = MutableLiveData<String>()
    val latitude = MutableLiveData<Double>()
    val longitude = MutableLiveData<Double>()


    val reminder: ReminderDataItem
        get() {
            val title = reminderTitle.value
            val description = reminderDescription.value
            val location = reminderSelectedLocationStr.value
            val latitude = latitude.value
            val longitude = longitude.value

            return ReminderDataItem(title, description, location, latitude, longitude)
        }

    /**
     * Validate the entered data and show error to the user if there's any invalid data
     */
    private fun validateEnteredData(reminderData: ReminderDataItem): Boolean {
        if (reminderData.title.isNullOrEmpty()) {
            showSnackBarInt.value = R.string.err_enter_title
            return false
        }

        if (reminderData.description.isNullOrEmpty()) {
            showSnackBarInt.value = R.string.err_enter_description
            return false
        }

        if (reminderData.location.isNullOrEmpty()) {
            showSnackBarInt.value = R.string.err_select_location
            return false
        }
        return true
    }

    fun onAddGeofencingSucceeded(reminderData: ReminderDataItem) {
        showSnackBarInt.postValue(R.string.geofence_added)
        saveReminder(reminderData)
    }

    fun onAddGeofencingFailed() {
        showSnackBarInt.postValue(R.string.geofences_not_added)
    }

    fun onSaveLocation(poi: PointOfInterest?, latitude: Double, longitude: Double, location: String) {
        poi?.let {
            selectedPOI.postValue(it)
        }
        this.latitude.postValue(latitude)
        this.longitude.postValue(longitude)
        reminderSelectedLocationStr.postValue(location)
        navigationCommand.postValue(NavigationCommand.Back)
    }

    /**
     * Clear the live data objects to start fresh next time the view model gets called
     */
    fun onClear() {
        reminderTitle.value = null
        reminderDescription.value = null
        reminderSelectedLocationStr.value = null
        selectedPOI.value = null
        latitude.value = null
        longitude.value = null
    }

    /**
     * Validate the entered data then saves the reminder data to the DataSource
     */
    fun validateAndSaveReminder(reminderData: ReminderDataItem) {
        if (validateEnteredData(reminderData)) {
            saveReminder(reminderData)
            addGeoFencingRequest.postValue(reminder)
        }
    }

    /**
     * Save the reminder to the data source
     */
    fun saveReminder(reminderData: ReminderDataItem) {
        showLoading.value = true
        viewModelScope.launch {
            dataSource.saveReminder(
                ReminderDTO(
                    reminderData.title,
                    reminderData.description,
                    reminderData.location,
                    reminderData.latitude,
                    reminderData.longitude,
                    reminderData.id
                )
            )
            showLoading.value = false
            showToast.value = app.getString(R.string.reminder_saved)
            navigationCommand.value = NavigationCommand.Back
        }
    }
}