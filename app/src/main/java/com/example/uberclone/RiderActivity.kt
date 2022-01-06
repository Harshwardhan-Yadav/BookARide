package com.example.uberclone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase

class RiderActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var currentLocation: LatLng
    private lateinit var sharedPreferences: SharedPreferences
    private var textType : Int = 0
    private var displayMode = 0
    private var driverLat = 0.0
    private var driverLng = 0.0

    override fun onBackPressed() {
        super.onBackPressed()
        val database = Firebase.database
        val ref = database.reference
        ref.child(Firebase.auth.currentUser?.uid.toString()).setValue(null)
        sharedPreferences.edit().putInt("text",0).commit()
        textType = 0
        updateText()
        Firebase.auth.currentUser?.delete()
        Firebase.auth.signOut()
        startActivity(Intent(this,MainActivity::class.java).apply {  })
        finish()
    }

    private fun updateText(){
        if(textType==0){
            findViewById<Button>(R.id.riderRequest).text = "Call Uber"
        } else {
            findViewById<Button>(R.id.riderRequest).text = "Cancel Request"
        }
    }

    fun request(view: View){
        val database = Firebase.database
        val ref = database.reference
        if(textType == 0) {
            ref.child(Firebase.auth.currentUser?.uid.toString()).setValue(currentLocation)
            sharedPreferences.edit().putInt("text",1).commit()
            textType = 1
            updateText()
        } else {
            sharedPreferences.edit().putInt("text",0).commit()
            textType = 0
            updateText()
            ref.child(Firebase.auth.currentUser?.uid.toString()).setValue(null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode==3 && resultCode== RESULT_OK){
            createLocationRequest()
        }
    }

    private fun updateMap(latLng: LatLng){
        if(displayMode == 0) {
            mMap.clear()
            mMap.addMarker(MarkerOptions().position(latLng).title("Your location"))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15F))
        } else {
            mMap.clear()
            val markers = arrayOfNulls<MarkerOptions>(2)
            markers[0] = MarkerOptions().position(latLng).title("Your Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            markers[1] = MarkerOptions().position(LatLng(driverLat, driverLng)).title("Driver's Location")
            mMap.addMarker(markers[0])
            mMap.addMarker(markers[1])
            val builder = LatLngBounds.Builder()
            for (marker in markers) {
                builder.include(marker?.position)
            }
            val bounds = builder.build()
            val padding = 100 // offset from edges of the map in pixels
            val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            mMap.moveCamera(cu)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_GRANTED){
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location : Location? ->
                        // Got last known location. In some rare situations this can be null.
                        if(location == null){
                            currentLocation = LatLng(0.0,0.0)
                            updateMap(currentLocation)
                        } else {
                            currentLocation = LatLng(location.latitude,location.longitude)
                            updateMap(currentLocation)
                        }
                    }
                createLocationRequest()
            } else {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        sharedPreferences = this.getSharedPreferences("com.example.uberclone", Context.MODE_PRIVATE)
        textType = sharedPreferences.getInt("text",0)
        println(textType)
        updateText()
        val ref = Firebase.database.reference
        ref.addValueEventListener(object: ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = snapshot.child("${Firebase.auth.currentUser?.uid}_driver").getValue<HashMap<String,Double>>()
                if(map!=null && findViewById<Button>(R.id.riderRequest).text.toString().equals("Cancel Request")){
                    displayMode = 1
                    driverLat = map?.get("latitude")!!
                    driverLng = map?.get("longitude")!!
                    var lat = currentLocation.latitude
                    var lng = currentLocation.longitude
                    var results = FloatArray(3)
                    if (lat != null) {
                        if (lng != null) {
                            Location.distanceBetween(lat, lng, driverLat, driverLng, results)
                            if(results[0]<=100){
                                findViewById<TextView>(R.id.textView).text = "Your ride is here"
                                sharedPreferences.edit().putInt("text",0).commit()
                                textType = 0
                                updateText()
                                ref.child(Firebase.auth.currentUser?.uid.toString()).setValue(null)
                                findViewById<Button>(R.id.riderRequest).visibility = View.INVISIBLE
                            }
                        }
                    }
                } else {
                    displayMode = 0
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations){
                    // Update UI with location data
                    // ...
                    currentLocation = LatLng(location.latitude,location.longitude)
                    updateMap(currentLocation)
                }
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),1)
        } else {
            // Take location
            var apiCheck = GoogleApiAvailability() // Used to check if api services is available
            if(apiCheck.isGooglePlayServicesAvailable(this)==0){
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location : Location? ->
                        // Got last known location. In some rare situations this can be null.
                        if(location == null){
                            currentLocation = LatLng(0.0, 0.0)
                            updateMap(currentLocation)
                        } else {
                            currentLocation = LatLng(location.latitude,location.longitude)
                            updateMap(currentLocation)
                        }
                    }
                createLocationRequest()
            } else {
                apiCheck.getErrorDialog(this,apiCheck.isGooglePlayServicesAvailable(this),2)?.show()
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun createLocationRequest() {
        val locationRequest = LocationRequest.create()?.apply {
            interval = 1000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsResponse ->
            // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...

            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback,
                    Looper.getMainLooper())
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException){
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(this,3)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                    onBackPressed()
                }
            }
        }
    }
}