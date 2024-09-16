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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.BoxScopeInstance.align
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.rememberBottomSheetScaffoldState

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope


import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
                    Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
                }
            }

        // 위치 권한 확인 및 요청
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
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
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    fusedLocationClient: FusedLocationProviderClient
) {
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }  // 선택된 장소 정보 저장

    val cameraPositionState = rememberCameraPositionState()
    val context = LocalContext.current
    val scaffoldState = rememberBottomSheetScaffoldState()  // BottomSheetScaffoldState를 사용
    val coroutineScope = rememberCoroutineScope()  // CoroutineScope 사용


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
                places = result.map { document ->
                    Place(
                        name = document.getString("name") ?: "",
                        latitude = document.getDouble("latitude") ?: 0.0,
                        longitude = document.getDouble("longitude") ?: 0.0,
                        address = document.getString("address") ?: "주소 없음",
                        price = document.getString("price") ?: "가격 정보 없음",
                        category = document.getString("category") ?: "카테고리 없음",
                        url = document.getString("url") ?: "url 없음"
                    )
                }

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

    // BottomSheetScaffold에 선택한 장소 정보 표시
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    currentLocation?.let {
                        cameraPositionState.position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(it, 15f)
                    }
                },
                modifier = Modifier
                    .padding(bottom = 100.dp, end = 16.dp)
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "현재 위치로 이동")
            }
        }
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = {
                selectedPlace?.let { place ->
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(text = "업체 이름: ${place.name}")
                        Text(text = "카테고리: ${place.category}")
                        Text(text = "가격: ${place.price}")
                        Text(text = "주소: ${place.address}")
                        Button(onClick = {
                            // 상세 페이지 보기 처리
                        }) {
                            Text("${place.url}")
                        }
                    }
                }?: Text("정보 없음", modifier = Modifier.padding(16.dp))
            },
            sheetPeekHeight = 0.dp,  // BottomSheet가 최소화된 상태에서 보이지 않도록 설정

            modifier = modifier.fillMaxSize()
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
                        snippet = "카테고리: ${place.category}",
                        onClick = {
                            selectedPlace = place  // 마커 클릭 시 장소 정보 저장
                            coroutineScope.launch {
                                scaffoldState.bottomSheetState.expand()  // CoroutineScope에서 expand() 호출
                            }
                            false // 기본 정보창 동작을 유지하도록 설정
                        }
                    )
                }
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
    val category: String,
    val url: String
)