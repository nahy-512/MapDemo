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
    private val coords = mutableListOf<LatLng>() // 관측 위치 리스트
    private var startMarker = Marker() // 경로의 시작 위치 표시
    private val movementMarkers = mutableListOf<Marker>() // 이동 경로 마커 리스트

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        initViews()
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

    private fun initViews() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setLocationButtonUI(false)
    }

    private fun initClickListeners() {
        // 위치 측정 버튼
        binding.locationRecordBtn.setOnClickListener {
            if (!hasPermission()) {
                requestLocationPermission()
            } else {
                setLocationService()
            }
        }
        // 경로 초기화 버튼
        binding.resetRouteBtn.setOnClickListener {
            resetRoute()
        }
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
    private fun initPolyLine(startLatLng: LatLng, isFirst: Boolean) {
        if (isFirst) {
            coords.addAll(listOf(startLatLng, startLatLng))
            userPolyline.color = Color.DKGRAY
        } else {
            coords.add(startLatLng)
        }
        userPolyline.coords = coords
        userPolyline.map = naverMap
    }

    // 유저의 이동 경로 업데이트
    private fun updateCoords(latLng: LatLng) {
        if (coords.isEmpty()) { // 처음 추가된 경우
            // 시작 위치 마커 표시
            setInitialMarker(latLng)
            // 사용자의 현재 위치를 동선에 저장
            initPolyLine(latLng, true)
        }
        coords.add(latLng)
        if (coords.size <= 1) { // 경로가 삭제된 상태
            // polyLine 다시 초기화
            initPolyLine(latLng, false)
        }
        userPolyline.coords = coords
    }

    // 시작 위치를 표시할 마커
    private fun setInitialMarker(latLng: LatLng) {
        startMarker.iconTintColor = Color.MAGENTA
        startMarker.position = latLng
        startMarker.captionText = "시작 위치"
        startMarker.map = naverMap
    }

    // 사용자의 이동 위치를 추적하는 마커
    private fun setMovementMarker(latLng: LatLng) {
        val marker = Marker().apply {
            position = latLng
            width = 50
            height = 75
            captionText = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) // 측정 시각 표시
            map = naverMap
        }
        // 마커 리스트에 추가
        movementMarkers.add(marker)
    }

    // 이동 경로 초기화
    private fun resetRoute() {
        if (!isLocationServiceRunning) { // 측정이 끝난 상태
            // polyLine 제거
            userPolyline.map = null
            coords.clear()
            // marker 제거
            startMarker.map = null
            if (movementMarkers.isNotEmpty()) {
                movementMarkers.forEach { it.map = null }
                movementMarkers.clear()
            }
            Toast.makeText(this, "지금까지 기록된 경로를 삭제했습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "위치 측정이 아직 종료되지 않았습니다.\n종료 후 경로를 삭제해 주세요.", Toast.LENGTH_SHORT).show()
        }
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
                setLocationService()
            } else {
                Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // LocationService가 실행중인지를 판별
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

    // 위치 측정 시작 또는 종료
    private fun setLocationService() {
        val intent = Intent(applicationContext, LocationService::class.java)
        if (!isLocationServiceRunning) { // 실행 X -> 실행하기
            bindLocationService()
            intent.setAction(Constants.ACTION_START_LOCATION_SERVICE)
            startService(intent)
            Toast.makeText(this, "위치 서비스 시작", Toast.LENGTH_SHORT).show()
            setLocationButtonUI(true)
        } else { // 실행 -> 실행 중단하기
            intent.setAction(Constants.ACTION_STOP_LOCATION_SERVICE)
            startService(intent)
            Toast.makeText(this, "위치 서비스 중단", Toast.LENGTH_SHORT).show()
            setLocationButtonUI(false)
        }
    }

    private fun setLocationButtonUI(isRunning: Boolean) {
        with(binding.locationRecordBtn) {
            text = if (isRunning) { // 위치 측정 중이라면 -> 중단 버튼 표시
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_stop, 0, 0, 0)
                getText(R.string.stop_location_updates)
            } else { // 위치 측정 중이 아니라면 -> 시작 버튼 표시
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_start, 0, 0, 0)
                getText(R.string.start_location_update)
            }
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