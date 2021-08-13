package com.udacity.project4.locationreminders.savereminder.selectreminderlocation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PointOfInterest
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderFragment.Companion.REQUEST_TURN_DEVICE_LOCATION_ON
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import java.util.Locale

class SelectLocationFragment : BaseFragment(), OnMapReadyCallback {

    companion object {
        const val TAG = "SelectLocationFragment"
        private const val REQUEST_GPS_LOCATION_PERMISSION = 9999
        private const val LOCATION_PERMISSION_INDEX = 0
        private const val RETRIEVE_LOCATION_MAXIMUM = 5
        private const val RETRIEVE_LOCATION_DELAY = 1000L
    }

    override val saveReminderViewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSelectLocationBinding

    private lateinit var map: GoogleMap

    private val fusedLocationClient: FusedLocationProviderClient? by lazy { context?.let { LocationServices.getFusedLocationProviderClient(it) } }

    private var reminderSelectedLocationStr = ""
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var marker: Marker? = null
    private var selectedPOI: PointOfInterest? = null

    // Ref: https://developer.android.com/training/location/retrieve-current
    private var retrieveLocationRetry = 0


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_GPS_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && (grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_GRANTED)) {
                    requestGPSLocation()
                } else {
                    Snackbar
                        .make(requireView(), R.string.permission_denied_explanation, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings) {
                            startActivity(Intent().apply {
                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, this@SelectLocationFragment.toString())
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }.show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_TURN_DEVICE_LOCATION_ON -> {
                enableMyLocation(false)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_select_location, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = saveReminderViewModel

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)

        (childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)

        bindClickListenerSaveLocation()

        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Guaruja - SP - Brazil - MY HOUSE
        val latitude = -23.994999483822994
        val longitude = -46.25498303770009
        val latLong = LatLng(latitude, longitude)
        val zoomLevel = 15F
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLong, zoomLevel))

        saveReminderViewModel.selectedPOI.value?.let { poi ->
            updateCurrentPoi(poi)
        }

        setNormalClicks(map)
        setPoiClick(map)
        setMapStyle(map)
        requestGPSLocation()
    }

    private fun setNormalClicks(map: GoogleMap) {
        map.setOnMapClickListener { latLng ->
            setupMarker(map, latLng)
        }

        map.setOnMapLongClickListener { latLng ->
            setupMarker(map, latLng)
        }
    }

    private fun setupMarker(map: GoogleMap, latLng: LatLng) {
        map.clear()
        val addresses = context?.let {
            Geocoder(it, Locale.getDefault())
                .getFromLocation(latLng.latitude, latLng.longitude, 1)
        }
        addresses?.let {
            if (it.size >= 1) {
                val address: String = addresses[0].getAddressLine(0)
                updateCurrentPoi(PointOfInterest(latLng, "", address))
                latitude = latLng.latitude
                longitude = latLng.longitude
                reminderSelectedLocationStr = address
            }
        }
    }

//    private fun setPoiClick(map: GoogleMap) {
//        map.setOnPoiClickListener { poi ->
//            updateCurrentPoi(poi)
//        }
//    }

    private fun updateCurrentPoi(poi: PointOfInterest) {
        marker?.remove()
        marker = map.addMarker(
            MarkerOptions()
                .position(poi.latLng)
                .title(poi.name)
        )
        marker?.showInfoWindow()
        selectedPOI = poi
        reminderSelectedLocationStr = poi.name
    }

    private fun setPoiClick(map: GoogleMap) {
        map.setOnPoiClickListener { poi ->
            map.clear()
            val poiMarker = map.addMarker(
                MarkerOptions()
                    .position(poi.latLng)
                    .title(poi.name)
            )
            poiMarker?.showInfoWindow()
            reminderSelectedLocationStr = poi.name
            latitude = poi.latLng.latitude
            longitude = poi.latLng.longitude
        }
    }

    private fun setOnLongClick(map: GoogleMap) {
        map.setOnMapLongClickListener { latLng ->
            map.clear()
            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.dropped_pin))
            )

            latitude = latLng.latitude
            longitude = latLng.longitude
            reminderSelectedLocationStr = getString(R.string.dropped_pin)
        }
    }

    private fun setMapStyle(map: GoogleMap) {
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestGPSLocation() {
        when {
            isLocationPermissionGranted() -> {
                map.isMyLocationEnabled = true
                enableMyLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Snackbar
                    .make(requireView(), R.string.permission_required, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.settings) {
                        requestPermission()
                    }.show()
            }
            else -> {
                requestPermission()
            }
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun enableMyLocation(needResolve: Boolean = true) {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }

        val taskSettingsLocation: Task<LocationSettingsResponse>? = activity?.let {
            LocationServices
                .getSettingsClient(it)
                .checkLocationSettings(
                    LocationSettingsRequest
                        .Builder()
                        .addLocationRequest(locationRequest)
                        .build()
                )
        }

        taskSettingsLocation
            ?.addOnCompleteListener {
                if (it.isSuccessful) {
                    getLastLocation()
                }
            }?.addOnFailureListener { exception ->
                if (exception is ResolvableApiException && needResolve) {
                    activity?.startIntentSenderForResult(
                        exception.resolution.intentSender,
                        REQUEST_TURN_DEVICE_LOCATION_ON, null, 0, 0, 0, null
                    )
                } else {
                    Snackbar
                        .make(requireView(), R.string.location_required_error, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings) {
                            enableMyLocation()
                        }.show()
                }
            }
    }

    private fun getLastLocation() {
        try {
            if (isLocationPermissionGranted()) {
                fusedLocationClient
                    ?.lastLocation
                    ?.addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15F))
                            retrieveLocationRetry = 0
                        } else {
                            if (retrieveLocationRetry < RETRIEVE_LOCATION_MAXIMUM) {
                                retrieveLocationRetry += 1
                                view?.postDelayed({ getLastLocation() }, RETRIEVE_LOCATION_DELAY)
                            }
                        }
                    }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_GPS_LOCATION_PERMISSION
        )
    }

    private fun bindClickListenerSaveLocation() {
        binding.mbtSaveLocation.setOnClickListener {
            if (latitude != 0.0 && longitude != 0.0 && reminderSelectedLocationStr.isNotBlank()) {
                saveReminderViewModel.onSaveLocation(selectedPOI, latitude, longitude, reminderSelectedLocationStr)
            } else {
                saveReminderViewModel.showSnackBar.postValue(getString(R.string.err_select_location))
            }

        }
    }
}
