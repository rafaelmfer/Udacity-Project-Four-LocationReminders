package com.udacity.project4.locationreminders.geofence

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.udacity.project4.R
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.sendNotification
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import kotlin.coroutines.CoroutineContext

class GeofenceTransitionsJobIntentService : JobIntentService(), CoroutineScope {

    private var coroutineJob: Job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.IO + coroutineJob

    companion object {
        private const val JOB_ID = 573
        const val ACTION_GEOFENCE_EVENT = "SaveReminderFragment.project4.action.ACTION_GEOFENCE_EVENT"
        const val TAG = "GeofenceTransitions"

        //TODO: call this to start the JobIntentService to handle the geofencing transition events
        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, GeofenceTransitionsJobIntentService::class.java, JOB_ID, intent)
        }
    }

    override fun onHandleWork(intent: Intent) {

        if (intent.action == ACTION_GEOFENCE_EVENT) {
            val geofencingEvent = GeofencingEvent.fromIntent(intent)
            if (geofencingEvent.hasError()) {
                Log.e(TAG, geofencingEvent.errorCode.toString())
                return
            }

            if (geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                Log.d(TAG, applicationContext.getString(R.string.geofence_entered))
                when {
                    geofencingEvent.triggeringGeofences.isNotEmpty() -> {
                        sendNotification(geofencingEvent.triggeringGeofences)
                    }
                    else -> {
                        Log.d(TAG, applicationContext.getString(R.string.no_data))
                        return
                    }
                }
            }
        }
    }

    private fun sendNotification(triggeringGeofences: List<Geofence>) {
        triggeringGeofences.forEach {
            sendNotification(it)
        }
    }

    //TODO: get the request id of the current geofence
    private fun sendNotification(geofence: Geofence) {
        val remindersLocalRepository: ReminderDataSource by inject()
        val requestId = geofence.requestId

        CoroutineScope(coroutineContext).launch(SupervisorJob()) {
            val result = remindersLocalRepository.getReminder(requestId)


            if (result is Result.Success<ReminderDTO>) {

                sendNotification(
                    this@GeofenceTransitionsJobIntentService,
                    ReminderDataItem(
                        title = result.data.title,
                        description = result.data.description,
                        location = result.data.location,
                        latitude = result.data.latitude,
                        longitude = result.data.longitude,
                        id = result.data.id
                    )
                )
            }
        }
    }
}