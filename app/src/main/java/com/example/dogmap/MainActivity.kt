package com.example.dogmap

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.dogmap.ui.theme.DogmapTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
//import androidx.compose.foundation.layout.BoxScopeInstance.align


import androidx.compose.foundation.layout.padding

import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Fused Location Provider 초기화
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 위치 권한 요청 처리
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    // 권한이 승인되었을 때
                    setContent {
                        DogmapTheme {
                            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                MapScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    fusedLocationClient = fusedLocationClient
                                )
                            }
                        }
                    }
                } else {
                    // 권한이 거부되었을 때
                    Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
                }
            }

        // 위치 권한 확인 및 요청
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // 권한이 이미 승인된 경우
                setContent {
                    DogmapTheme {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            MapScreen(
                                modifier = Modifier.padding(innerPadding),
                                fusedLocationClient = fusedLocationClient
                            )
                        }
                    }
                }
            }
            else -> {
                // 권한을 요청
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    fusedLocationClient: FusedLocationProviderClient
) {
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var places by remember { mutableStateOf<List<Place>>(emptyList()) }

    val cameraPositionState = rememberCameraPositionState()
    val context = LocalContext.current

    // Firestore 인스턴스 가져오기
    val db = FirebaseFirestore.getInstance()

    // LocationRequest 설정
    val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
        priority = com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
        interval = 5000 // 5초마다 업데이트
        smallestDisplacement = 5f // 최소 5미터 이동 시 업데이트
    }

    // Firestore에서 장소 데이터를 불러오기
    LaunchedEffect(Unit) {
        db.collection("places").get()
            .addOnSuccessListener { result ->
                val fetchedPlaces = result.map { document ->
                    Place(
                        name = document.getString("name") ?: "",
                        latitude = document.getDouble("latitude") ?: 0.0,
                        longitude = document.getDouble("longitude") ?: 0.0,
                        address = document.getString("address") ?: "주소 없음",
                        price = document.getString("price") ?: "가격 정보 없음",
                        category = document.getString("category") ?: "카테고리 없음"
                    )
                }
                places = fetchedPlaces
            }
            .addOnFailureListener {
                Toast.makeText(context, "장소 데이터를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }

        // 위치 권한이 있는지 확인하고 위치 업데이트 요청
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        val location = locationResult.lastLocation
                        if (location != null) {
                            currentLocation = LatLng(location.latitude, location.longitude)
                            cameraPositionState.position =
                                com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                                    currentLocation!!, 15f
                                )
                        }
                    }
                }, Looper.getMainLooper()
            )
        } else {
            Toast.makeText(context, "위치 권한이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            Box(modifier = Modifier.fillMaxSize()) {
                FloatingActionButton(
                    onClick = {
                        currentLocation?.let {
                            cameraPositionState.position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(it, 15f)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 100.dp, end = 16.dp) // 확대 축소 버튼 위에 내 위치 버튼을 배치
                ) {
                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "현재 위치로 이동")
                }
            }
        }
    ) {

        GoogleMap(
            modifier = modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            // 현재 위치에 마커 표시
            currentLocation?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "내 위치"
                )
            }

            // Firestore에서 불러온 장소에 마커 표시
            places.forEach { place ->
                Marker(
                    state = MarkerState(position = LatLng(place.latitude, place.longitude)),
                    title = place.name,
                    snippet = "카테고리: ${place.category}"
                )
            }
        }
    }
}

// 장소 데이터를 담는 데이터 클래스
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,   // 주소 추가
    val price: String,      // 가격 정보 추가
    val category: String
)