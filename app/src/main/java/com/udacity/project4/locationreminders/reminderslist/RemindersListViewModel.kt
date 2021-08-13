package com.udacity.project4.locationreminders.reminderslist

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.udacity.project4.base.BaseViewModel
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import kotlinx.coroutines.launch

class RemindersListViewModel(app: Application, private val dataSource: ReminderDataSource) : BaseViewModel(app) {

    val remindersList = MutableLiveData<List<ReminderDataItem>>()

    /**
     * Inform the user that there's not any data if the remindersList is empty
     */
    private fun checkIfThereIsReminders() {
        showNoData.value = remindersList.value.isNullOrEmpty()
    }

    /**
     * Get all the reminders from the DataSource and add them to the remindersList to be shown on the UI,
     * or show error if any
     */
    fun getReminders() {
        showLoading.value = true

        viewModelScope.launch {

            when (val result = dataSource.getReminders()) {
                is Result.Success<List<ReminderDTO>> -> {
                    val reminders = result.data.map {
                        ReminderDataItem(
                            title = it.title,
                            description = it.description,
                            location = it.location,
                            latitude = it.latitude,
                            longitude = it.longitude,
                            id = it.id
                        )
                    }

                    remindersList.postValue(reminders)
                }
                is Result.Error -> showSnackBar.postValue(result.message)
            }

            showLoading.postValue(false)
            checkIfThereIsReminders()
        }
    }
}