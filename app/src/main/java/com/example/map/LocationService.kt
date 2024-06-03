package com.example.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices


class LocationService : Service() {
    private val mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            if (locationResult.lastLocation != null) {
                val latitude = locationResult.lastLocation!!.latitude
                val longitude = locationResult.lastLocation!!.longitude
                Log.v("LOCATION_UPDATE", "$latitude, $longitude")
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException("Not yet implemented")
    }

    @SuppressLint("ForegroundServiceType")
    private fun startLocationService() {
        val channelId = "location_notification_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val resultIntent = Intent()
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            resultIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(applicationContext, channelId)
        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.setContentTitle("Location Service")
        builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        builder.setContentText("Running")
        builder.setContentIntent(pendingIntent)
        builder.setAutoCancel(false)
        builder.setPriority(NotificationCompat.PRIORITY_MAX)
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val notificationChannel = NotificationChannel(
                channelId,
                "Location Service",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationChannel.description = "This channel is used by location service"
            notificationManager.createNotificationChannel(notificationChannel)
        }
        val locationRequest = LocationRequest.create()
        locationRequest.setInterval(4000)
        locationRequest.setFastestInterval(2000)
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        LocationServices.getFusedLocationProviderClient(this)
            .requestLocationUpdates(locationRequest, mLocationCallback, Looper.getMainLooper())
        startForeground(Constants.LOCATION_SERVICE_ID, builder.build())
    }

    private fun stopLocationService() {
        LocationServices.getFusedLocationProviderClient(this)
            .removeLocationUpdates(mLocationCallback)
        stopForeground(true)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.action
        if (action != null) {
            if (action == Constants.ACTION_START_LOCATION_SERVICE) {
                startLocationService()
            } else if (action == Constants.ACTION_STOP_LOCATION_SERVICE) {
                stopLocationService()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }
}