package com.example.uberclone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMapLoadedCallback
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.*


class DriverShowActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var driverLocation: String
    private lateinit var chosenLocation: String
    private lateinit var user: String
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private lateinit var timer: CountDownTimer
    private var flip = false
    private var userCancelled = false
    private var driverLat: Double = 0.0
    private var driverLng: Double = 0.0
    private var userLat: Double = 0.0
    private var userLng: Double = 0.0

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults.isNotEmpty() && grantResults[0]== PackageManager.PERMISSION_GRANTED){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED){
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        println(flip)
        if(flip){
            onBackPressed()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        locationManager.removeUpdates(locationListener)
        timer.cancel()
        var ref = Firebase.database.reference
        ref.child("${user}_driver").setValue(null).addOnSuccessListener {
            startActivity(Intent(this, DriverActiity::class.java))
        }
    }

    @SuppressLint("MissingPermission")
    fun acceptRide(view: View){
        var geocoder = Geocoder(applicationContext, Locale.getDefault())
        try {
            var listAddress = geocoder.getFromLocation(userLat, userLng, 1)
            if(listAddress!=null && listAddress.size>0){
                val gmmIntentUri =
                    Uri.parse("google.navigation:q=${listAddress.get(0).getAddressLine(0)}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
                flip = true
            }
        } catch (e: Exception){
            Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_show)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.driverShowMap) as SupportMapFragment
        mapFragment.getMapAsync(this)
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 1)
        }
        driverLocation = intent.getStringExtra("driverLocation").toString()
        chosenLocation = intent.getStringExtra("chosenLocation").toString()
        user = intent.getStringExtra("user").toString()
        userCancelled = false
        println("User key - $user")
        driverLat = driverLocation.split(" ")[0].toDouble()
        driverLng = driverLocation.split(" ")[1].toDouble()
        userLat = chosenLocation.split(" ")[0].toDouble()
        userLng = chosenLocation.split(" ")[1].toDouble()
        locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                driverLat = location.latitude
                driverLng = location.longitude
                var ref = Firebase.database.reference
                ref.child("${user}_driver").setValue(LatLng(driverLat,driverLng))
                println("here")
            }

            //Below 3 methods are needed to be added by own.
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        }
        timer = object: CountDownTimer(Long.MAX_VALUE,5000){
            override fun onTick(p0: Long) {
                var results = FloatArray(3)
                if (userLat != null) {
                    if (userLng != null) {
                        Location.distanceBetween(userLat, userLng, driverLat, driverLng, results)
                        if(results[0]<=100){
                            Toast.makeText(applicationContext,"You have reached the destination",Toast.LENGTH_SHORT).show()
                            return
                        }
                    }
                }
                Toast.makeText(applicationContext,"Ride cancelled by user.",Toast.LENGTH_SHORT).show()
            }
            override fun onFinish() {

            }
        }
        Firebase.database.reference.addValueEventListener(object: ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.child(user).value==null && !userCancelled){
                    locationManager.removeUpdates(locationListener)
                    timer.start()
                    println("timer initiated")
                    userCancelled = true
                }
            }
            override fun onCancelled(error: DatabaseError) {

            }
        })
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
        //fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val markers = arrayOfNulls<MarkerOptions>(2)
        markers[0] = MarkerOptions().position(LatLng(userLat, userLng)).title("User Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        markers[1] = MarkerOptions().position(LatLng(driverLat, driverLng)).title("Your Location")
        mMap.addMarker(markers[0])
        mMap.addMarker(markers[1])
        val builder = LatLngBounds.Builder()
        for (marker in markers) {
            builder.include(marker?.position)
        }
        val bounds = builder.build()
        val padding = 100 // offset from edges of the map in pixels
        val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
        mMap.setOnMapLoadedCallback(OnMapLoadedCallback { mMap.moveCamera(cu) })
    }

}