package com.example.map

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.map.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.PolylineOverlay
import com.naver.maps.map.util.FusedLocationSource
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity(), OnMapReadyCallback, LocationUpdateInterface {
    private lateinit var binding: ActivityMainBinding
    private lateinit var naverMap: NaverMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationSource: FusedLocationSource
    private var locationService: LocationService? = null
    private var isBound = false

    private val userPolyline = PolylineOverlay()
    private val coords = mutableListOf<LatLng>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (!hasPermission()) {
            requestLocationPermission()
        } else {
            initMapView()
        }
        initClickListeners()
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

    // 유저의 이동 경로 초기화
    private fun initPolyLine(startLatLng: LatLng) {
        coords.addAll(listOf(startLatLng, startLatLng))
        userPolyline.coords = coords
        userPolyline.color = Color.DKGRAY
        userPolyline.map = naverMap
    }

    // 유저의 이동 경로 업데이트
    private fun updateCoords(latLng: LatLng) {
        coords.add(latLng)
        userPolyline.coords = coords
    }

    // 시작 위치를 표시할 마커
    private fun setInitialMarker() {
        val startMarker = Marker()
        startMarker.iconTintColor = Color.MAGENTA
        startMarker.position = LatLng(
            naverMap.cameraPosition.target.latitude,
            naverMap.cameraPosition.target.longitude
        )
        startMarker.captionText = "시작 위치"
        startMarker.map = naverMap
    }

    // 사용자의 이동 위치를 추적하는 마커
    private fun setMovementMarker(latLng: LatLng) {
        val marker = Marker()
        marker.position = latLng
        marker.width = 50
        marker.height = 75
        marker.captionText = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) // 측정 시각 표시
        marker.map = naverMap
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
        // 내장 위치 추적 기능
        naverMap.locationSource = locationSource
        // 현재 위치 버튼 기능
        naverMap.uiSettings.isLocationButtonEnabled = true
        // 위치를 추적하면서 카메라도 따라 움직인다.
        naverMap.locationTrackingMode = LocationTrackingMode.Follow

        // 사용자 현재 위치 받아오기
        var currentLocation: Location?
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                currentLocation = location
                // 위치 오버레이의 가시성은 기본적으로 false로 지정되어 있습니다. 가시성을 true로 변경하면 지도에 위치 오버레이가 나타납니다.
                // 파랑색 점으로 현재 위치 표시
                naverMap.locationOverlay.run {
                    isVisible = true
                    position = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
                }

                // 카메라 현재위치로 이동
                val cameraUpdate = CameraUpdate.scrollTo(
                    LatLng(
                        currentLocation!!.latitude,
                        currentLocation!!.longitude
                    )
                )
                naverMap.moveCamera(cameraUpdate)
                // 시작 위치 마커 표시
                setInitialMarker()
                // 사용자의 현재 위치를 동선에 저장
                initPolyLine(LatLng(naverMap.cameraPosition.target.latitude, naverMap.cameraPosition.target.longitude))
            }
    }

    override fun sendLocation(latitude: Double, longitude: Double) {
        Log.d("MAIN_LOCATION", "$latitude, $longitude")
        updateCoords(LatLng(latitude, longitude)) // 이동 경로 업데이트
        setMovementMarker(LatLng(latitude, longitude)) // 이동 경로 마커 추가
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