package com.example.uberclone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase

class DriverActiity : AppCompatActivity() {

    private var driverLat : Double = 0.0
    private var driverLng : Double = 0.0
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var currentLocation: LatLng
    private lateinit var timer: CountDownTimer

    override fun onBackPressed() {
        super.onBackPressed()
        Firebase.auth.currentUser?.delete()
        Firebase.auth.signOut()
        startActivity(Intent(this,MainActivity::class.java).apply {  })
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults.isNotEmpty() && grantResults[0]== PackageManager.PERMISSION_GRANTED){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                fusedLocationClient.lastLocation
                        .addOnSuccessListener { location : Location? ->
                            // Got last known location. In some rare situations this can be null.
                            if(location == null){
                                currentLocation = LatLng(0.0,0.0)
                            } else {
                                currentLocation = LatLng(location.latitude,location.longitude)
                            }
                        }
                createLocationRequest()
            } else {
                finish()
            }
        }
    }

    private fun findDriverLocation(){
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
                            } else {
                                currentLocation = LatLng(location.latitude,location.longitude)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_actiity)
        currentLocation = LatLng(0.0, 0.0)
        var list : ArrayList<String>
        var key : HashMap<String,Int>
        var listView = findViewById<ListView>(R.id.listViewDriverActivity)
        var adapter: ArrayAdapter<String>
        var points: ArrayList<String>
        var user: ArrayList<String>
        timer = object:  CountDownTimer(Long.MAX_VALUE, 60000) {

            override fun onTick(millisUntilFinished: Long) {
                key = HashMap()
                list = ArrayList()
                points = ArrayList()
                user = ArrayList()
                adapter = ArrayAdapter<String>(applicationContext,android.R.layout.simple_list_item_1,list)
                listView.adapter = adapter
                listView.setOnItemClickListener{ _, _, position, _ ->
                    println(points[position])
                    val driverLocation = currentLocation.latitude.toString()+" "+currentLocation.longitude.toString()
                    val chosenLocation = points[position]
                    startActivity(Intent(applicationContext,DriverShowActivity::class.java).apply{
                        putExtra("driverLocation",driverLocation)
                        putExtra("chosenLocation",chosenLocation)
                        putExtra("user",user[position])
                    })
                    finish()
                }
                findDriverLocation()
                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult?) {
                        locationResult ?: return
                        for (location in locationResult.locations) {
                            // Update UI with location data
                            // ...
                            currentLocation = LatLng(location.latitude, location.longitude)
                            var database = Firebase.database
                            var ref = database.reference
                            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    for (data in snapshot.children) {
                                        val map = data.getValue<HashMap<String, Double>>()
                                        if(key[data.key.toString()] == null && data.key.toString().indexOf("_driver") == -1){
                                            key[data.key.toString()] = 1
                                        } else {
                                            continue
                                        }
                                        val lat = map?.get("latitude")
                                        val lng = map?.get("longitude")
                                        Thread.sleep(3000)
                                        driverLat = currentLocation.latitude
                                        driverLng = currentLocation.longitude
                                        var results = FloatArray(3)
                                        if (lat != null) {
                                            if (lng != null) {
                                                Location.distanceBetween(lat, lng, driverLat, driverLng, results)
                                                points.add("$lat $lng")
                                            }
                                        }
                                        list.add("${Math.round(results[0] / 1000)} km")
                                        user.add(data.key.toString())
                                    }
                                    for(i in 0 until (list.size-1)){
                                        for(j in 0 until (list.size-1-i)){
                                            val a = Integer.parseInt(list[j].split(" ")[0])
                                            val b = Integer.parseInt(list[j+1].split(" ")[0])
                                            if(a>b){
                                                var t = list[j]
                                                list[j] = list[j+1]
                                                list[j+1] = t
                                                t = points[j]
                                                points[j] = points[j+1]
                                                points[j+1] = t
                                                t = user[j]
                                                user[j] = user[j+1]
                                                user[j+1] = t
                                            }
                                        }
                                    }
                                    adapter.notifyDataSetChanged()
                                    fusedLocationClient.removeLocationUpdates(locationCallback)
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Toast.makeText(applicationContext, error.message, Toast.LENGTH_SHORT).show()
                                    Thread.sleep(3000)
                                    startActivity(Intent(applicationContext, MainActivity::class.java).apply { })
                                    finish()
                                }
                            })
                        }
                    }
                }
            }

            override fun onFinish() {

            }
        }.start()
    }
}