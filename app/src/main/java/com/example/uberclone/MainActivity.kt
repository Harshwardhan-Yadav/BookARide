package com.example.uberclone

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var loginMode: String

    private fun login(){
        val switch = sharedPreferences.getString("mode","rider")
        if (switch.equals("rider")){
            startActivity(Intent(this,RiderActivity::class.java).apply {  })
        } else {
            startActivity(Intent(this,DriverActiity::class.java).apply {  })
        }
        finish()
    }

    fun getStarted(view: View){
        auth.signInAnonymously().addOnCompleteListener { task ->
            if(task.isSuccessful){
                println("Anonymous login successful")
                // Update UI
                if(findViewById<Switch>(R.id.loginSwitch).isChecked){
                    sharedPreferences.edit().putString("mode","driver").commit()
                } else {
                    sharedPreferences.edit().putString("mode","rider").commit()
                }
                login()
            } else {
                println("Login failed.")
                println(task.exception.toString())
                Toast.makeText(this,"Login failed.",Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if(currentUser!=null){
            // Go to corresponding activity of either rider or driver
            println("User signed in already.")
            login()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        auth = Firebase.auth
        sharedPreferences = this.getSharedPreferences("com.example.uberclone", Context.MODE_PRIVATE)

    }
}