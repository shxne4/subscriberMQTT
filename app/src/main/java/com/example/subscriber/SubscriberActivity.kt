package com.example.subscriber

import android.content.ContentValues
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.assignment.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class SubscriberActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var googleMap: GoogleMap
    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscriber)

        // Initialize the map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set up MQTT client
        setupMqttClient()

        // Initialize the database helper
        databaseHelper = DatabaseHelper(this)
    }

    private fun setupMqttClient() {
        val clientId = MqttClient.generateClientId()
        mqttClient = MqttAndroidClient(this, "tcp://broker.hivemq.com:1883", clientId)

        val options = MqttConnectOptions()
        options.isCleanSession = true

        try {
            mqttClient.connect(options)
            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.e("MQTT", "Connection lost: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val msg = message?.toString() ?: return
                    Log.d("MQTT", "Message arrived: $msg")
                    handleIncomingMessage(msg)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // complete if needed
                }
            })

            mqttClient.subscribe("assignment/location", 1)
        } catch (e: MqttException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to connect: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleIncomingMessage(message: String) {
        // Parse the message to extract location data
        val parts = message.split(", ")
        if (parts.size >= 3) {
            val latitude = parts[1].split(": ")[1].toDouble()
            val longitude = parts[2].split(": ")[1].toDouble()
            val location = LatLng(latitude, longitude)

            // Add marker to the map
            runOnUiThread {
                googleMap.addMarker(MarkerOptions().position(location).title("New Location"))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))

                // Insert location into the database
                databaseHelper.insertLocation(latitude, longitude)
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Retrieve and display stored locations
        val storedLocations = databaseHelper.getAllLocations()
        for (location in storedLocations) {
            val latLng = LatLng(location.first, location.second)
            googleMap.addMarker(MarkerOptions().position(latLng).title("Stored Location"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mqttClient.disconnect()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}