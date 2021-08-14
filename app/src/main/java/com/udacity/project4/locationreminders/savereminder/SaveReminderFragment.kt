package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
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
import com.udacity.project4.BuildConfig
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

    private val runningQOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

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

        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.Q)
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

            val reminderData = ReminderDataItem(title, description, location, latitude, longitude)

            if (saveReminderViewModel.validateEnteredData(reminderData)) {
                checkPermissionsAndStartGeofencing(reminderDataItem = reminderData)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveReminderViewModel.onClear()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TURN_DEVICE_LOCATION_ON) {
            checkDeviceLocationSettingsAndStartGeofence(false)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.isEmpty() || grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED ||
            (requestCode == REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE && grantResults[BACKGROUND_LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED)
        ) {
            // Permission denied.
            Snackbar
                .make(requireView(), R.string.permission_denied_explanation, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.settings) {
                    startActivity(Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }.show()
        } else {
            checkDeviceLocationSettingsAndStartGeofence()
        }
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                AlertDialog.Builder(context)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs the Location permission, please accept to use location functionality")
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        //Prompt the user once explanation has been shown
                        requestLocationPermission()
                    }
                    .create()
                    .show()
            } else {
                // No explanation needed, we can request the permission.
                requestLocationPermission()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            )
        }
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkPermissionsAndStartGeofencing(reminderDataItem: ReminderDataItem) {
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            checkDeviceLocationSettingsAndStartGeofence(resolve = true, reminderDataItem = reminderDataItem)
        } else {
//            requestForegroundAndBackgroundLocationPermissions()
            checkLocationPermission()
        }
    }

    private fun checkDeviceLocationSettingsAndStartGeofence(resolve: Boolean = true, reminderDataItem: ReminderDataItem? = null) {
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
                if (it.isSuccessful && reminderDataItem?.latitude != null) {
                    addGeofenceClue(reminderDataItem)
                } else {
                    saveReminderViewModel.showToast.postValue(getString(R.string.select_location))
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
                        requireView(),
                        R.string.location_required_error,
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(R.string.settings) {
                        checkDeviceLocationSettingsAndStartGeofence()
                    }.show()
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun addGeofenceClue(reminderDataItem: ReminderDataItem) {
        val geoFence = Geofence.Builder()
            .setRequestId(reminderDataItem.id)
            .setCircularRegion(reminderDataItem.latitude!!, reminderDataItem.longitude!!, 200f)
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
                    saveReminderViewModel.validateAndSaveReminder(reminderDataItem)
                    saveReminderViewModel.showSnackBarInt.postValue(R.string.geofence_added)
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
