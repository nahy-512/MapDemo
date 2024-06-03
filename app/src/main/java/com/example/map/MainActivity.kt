package com.example.map

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.map.databinding.ActivityMainBinding
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.PolylineOverlay
import com.naver.maps.map.util.FusedLocationSource

class MainActivity : AppCompatActivity(), OnMapReadyCallback, LocationUpdateInterface {
    private lateinit var binding: ActivityMainBinding
    private lateinit var naverMap: NaverMap
    private lateinit var locationSource: FusedLocationSource
    private var locationService: LocationService? = null
    private var isBound = false

    private val testPolyline = PolylineOverlay()
    private val userPolyline = PolylineOverlay()

    private val coords = mutableListOf<LatLng>(
        TEST_COORDS[TEST_COORDS.size - 1],
        TEST_COORDS[TEST_COORDS.size - 1]
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        if (!hasPermission()) {
            requestLocationPermission()
        } else {
            initMapView()
        }
        initClickListeners()
        initPolyLine()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindLocationService()
    }

    // 위치 권한이 있을 경우 true, 없을 경우 false 반환
    private fun hasPermission(): Boolean {
        for (permission in PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    // 위치 권한 요청
    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_CODE_LOCATION_PERMISSION
        )
    }

    private fun initClickListeners() {
        binding.buttonStartLocationUpdates.setOnClickListener {
            if (!hasPermission()) {
                requestLocationPermission()
            } else {
                startLocationService()
            }
        }
        binding.buttonStopLocationUpdates.setOnClickListener { stopLocationService() }
    }

    private fun initMapView() {
        val fm = supportFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map_fragment) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map_fragment, it).commit()
            }

        // fragment의 getMapAsync() 메서드로 OnMapReadyCallback 콜백을 등록하면 비동기로 NaverMap 객체를 얻을 수 있다.
        mapFragment.getMapAsync(this)
        locationSource = FusedLocationSource(this, REQUEST_CODE_LOCATION_PERMISSION)
    }

    private fun initPolyLine() {
        // 테스트
        testPolyline.coords = TEST_COORDS
        testPolyline.color = Color.MAGENTA
        testPolyline.width = 8
        // 유저
        userPolyline.coords = coords
        userPolyline.color = Color.DKGRAY
    }

    private fun updateCoords(latitude: Double, longitude: Double) {
        coords.add(LatLng(latitude, longitude))
        userPolyline.coords = coords
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            locationService?.setLocationUpdateInterface(this@MainActivity)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationService()
            } else {
                Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val isLocationServiceRunning: Boolean
        get() {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
                if (LocationService::class.java.name == service.service.className) {
                    if (service.foreground) {
                        return true
                    }
                }
            }
            return false
        }

    private fun startLocationService() {
        if (!isLocationServiceRunning) {
            val intent = Intent(applicationContext, LocationService::class.java)
            intent.setAction(Constants.ACTION_START_LOCATION_SERVICE)
            startService(intent)
            Toast.makeText(this, "Location service started", Toast.LENGTH_SHORT).show()
            bindLocationService()
        }
    }

    private fun stopLocationService() {
        if (isLocationServiceRunning) {
            val intent = Intent(applicationContext, LocationService::class.java)
            intent.setAction(Constants.ACTION_STOP_LOCATION_SERVICE)
            startService(intent)
            Toast.makeText(this, "Location service stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindLocationService() {
        val intent = Intent(this, LocationService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindLocationService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap
        // 현재 위치
        naverMap.locationSource = locationSource
        // 현재 위치 버튼 기능
        naverMap.uiSettings.isLocationButtonEnabled = true
        // 위치를 추적하면서 카메라도 따라 움직인다.
        naverMap.locationTrackingMode = LocationTrackingMode.Follow
        // 선 연결
        testPolyline.map = naverMap
        userPolyline.map = naverMap
    }

    override fun sendLocation(latitude: Double, longitude: Double) {
        Log.d("MAIN_LOCATION", "$latitude, $longitude")
        updateCoords(latitude, longitude)
    }

    companion object {
        private const val REQUEST_CODE_LOCATION_PERMISSION = 1
        private val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        private val TEST_COORDS = mutableListOf(
            LatLng(37.55315, 126.972533), // 서울역
            LatLng(37.561159, 127.035505), // 왕십리역
            LatLng(37.540408, 127.069231), // 건대 입구역
            LatLng(37.54718, 127.047413), // 뚝섬역
            LatLng(37.496068, 127.028506), // 강남역
            LatLng(37.394726159, 127.111209047), // 판교역
        )
    }
}