package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.geofence.GeofenceTransitionsJobIntentService.Companion.ACTION_GEOFENCE_EVENT
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

@SuppressLint("UnspecifiedImmutableFlag")
class SaveReminderFragment : BaseFragment() {

    companion object {
        private const val LOCATION_PERMISSION_INDEX = 0
        private const val BACKGROUND_LOCATION_PERMISSION_INDEX = 1
        private const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 33
        private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
        const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
    }

    //Get the view model this time as a single to be shared with the another fragment
    override val saveReminderViewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSaveReminderBinding

    private lateinit var geofencingClient: GeofencingClient
    private var reminderData: ReminderDataItem? = null

    private val runningQOrLater =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    private val geofencePendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceBroadcastReceiver::class.java).apply { action = ACTION_GEOFENCE_EVENT },
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = saveReminderViewModel
        setDisplayHomeAsUpEnabled(true)

        activity?.let { geofencingClient = LocationServices.getGeofencingClient(it) }

        if (!foregroundAndBackgroundLocationPermissionApproved()) {
            requestForegroundAndBackgroundLocationPermissions()
        }

        saveReminderViewModel.addGeoFencingRequest.observe(viewLifecycleOwner, {
            checkPermissionsAndStartGeofencing()
        })

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.selectLocation.setOnClickListener {
            saveReminderViewModel.navigationCommand.value =
                NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())
        }

        binding.saveReminder.setOnClickListener {
            val title = saveReminderViewModel.reminderTitle.value
            val description = saveReminderViewModel.reminderDescription.value
            val location = saveReminderViewModel.reminderSelectedLocationStr.value
            val latitude = saveReminderViewModel.latitude.value
            val longitude = saveReminderViewModel.longitude.value
            val selectedPoi = saveReminderViewModel.selectedPOI.value

            val reminderData = ReminderDataItem(title, description, location, latitude, longitude)
            saveReminderViewModel.validateAndSaveReminder(reminderData)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveReminderViewModel.onClear()
    }

    @TargetApi(29)
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {
        val foregroundLocationApproved = (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION))
        val backgroundPermissionApproved =
            if (runningQOrLater) {
                PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                true
            }

        return foregroundLocationApproved && backgroundPermissionApproved
    }

    @TargetApi(29)
    private fun requestForegroundAndBackgroundLocationPermissions() {
        if (foregroundAndBackgroundLocationPermissionApproved()) return

        var permissionArray = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

        val resultCode = when {
            runningQOrLater -> {
                permissionArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE
            }
            else -> REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
        }
        ActivityCompat.requestPermissions(requireActivity(), permissionArray, resultCode)
    }

    private fun checkPermissionsAndStartGeofencing() {
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            checkDeviceLocationSettingsAndStartGeofence()
        } else {
            requestForegroundAndBackgroundLocationPermissions()
        }
    }

    private fun checkDeviceLocationSettingsAndStartGeofence(resolve: Boolean = true) {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }

        val taskLocationSettings: Task<LocationSettingsResponse>? = activity?.let {
            LocationServices
                .getSettingsClient(it)
                .checkLocationSettings(
                    LocationSettingsRequest
                        .Builder()
                        .addLocationRequest(locationRequest)
                        .build()
                )
        }
        taskLocationSettings
            ?.addOnCompleteListener {
                if (it.isSuccessful) {
                    addGeofenceClue(saveReminderViewModel.reminder)
                }
            }
            ?.addOnFailureListener { exception ->
                if (exception is ResolvableApiException && resolve) {
                    try {
                        activity?.let { exception.startResolutionForResult(it, REQUEST_TURN_DEVICE_LOCATION_ON) }

                    } catch (ex: IntentSender.SendIntentException) {
                        ex.printStackTrace()
                        Log.i("warning", "Error getting location settings resolution" + ex.message)
                    }
                } else {
                    Snackbar.make(
                        this.requireView(),
                        R.string.location_required_error,
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(R.string.settings) {
                        checkDeviceLocationSettingsAndStartGeofence()
                    }.show()
                }
            }
    }

    private fun addGeofenceClue(item: ReminderDataItem) {
        if (item.latitude != null && item.longitude != null) {
            val geoFence = Geofence.Builder()
                .setRequestId(item.id)
                .setCircularRegion(item.latitude!!, item.longitude!!, 200f)
                .setExpirationDuration(TimeUnit.HOURS.toMillis(2))
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()

            val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geoFence)
                .build()

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
                    addOnSuccessListener {
                        saveReminderViewModel.showSnackBarInt.postValue(R.string.geofence_added)
                        saveReminderViewModel.navigationCommand.postValue(NavigationCommand.Back)
                    }
                    addOnFailureListener {
                        saveReminderViewModel.showSnackBar.postValue(getString(R.string.geofences_not_added))
                    }
                }
            } else {
                saveReminderViewModel.showSnackBarInt.postValue(R.string.permission_required)
                return
            }
        }
    }
}
