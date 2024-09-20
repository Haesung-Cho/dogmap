package com.example.dogmap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.dogmap.ui.theme.DogmapTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
    var selectedPlace by remember { mutableStateOf<Place?>(null) }

    val cameraPositionState = rememberCameraPositionState()
    val context = LocalContext.current
    val scaffoldState = rememberBottomSheetScaffoldState()
    val coroutineScope = rememberCoroutineScope()

    // Firestore 인스턴스 가져오기
    val db = FirebaseFirestore.getInstance()

    // LocationRequest 설정
    val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
        priority = com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
        interval = 5000
        smallestDisplacement = 5f
    }

    // Firestore에서 장소 데이터를 불러오기
    LaunchedEffect(Unit) {
        db.collection("places").get()
            .addOnSuccessListener { result ->
                places = result.map { document ->
                    val instagram = document.getString("instagram") ?: ""
                    Log.d("Firestore", "Instagram Link: $instagram")  // 로그 추가

                    Place(
                        name = document.getString("name") ?: "",
                        latitude = document.getDouble("latitude") ?: 0.0,
                        longitude = document.getDouble("longitude") ?: 0.0,
                        address = document.getString("address") ?: "주소 없음",
                        price = document.getString("price") ?: "가격 정보 없음",
                        category = document.getString("category") ?: "카테고리 없음",
                        url = document.getString("url") ?: "링크 없음",
                        time = document.getString("time") ?: "시간 정보 없음",
                        images = document.get("images") as? List<String> ?: emptyList(),
                        instagram = document.getString("instagram") ?: ""
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
                    override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                        val location = locationResult.lastLocation
                        if (location != null) {
                            currentLocation = LatLng(location.latitude, location.longitude)
                            cameraPositionState.position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                                currentLocation!!, 15f
                            )
                        }
                    }
                }, android.os.Looper.getMainLooper()
            )
        } else {
            Toast.makeText(context, "위치 권한이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

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
                            .padding(bottom = 50.dp)  // 하단에 50dp 패딩 추가
                    ) {
                                                // 텍스트 스타일링 적용
                        Text(
                            text = "업체 이름: ${place.name}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = "카테고리: ${place.category}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                        )
                        Text(
                            text = "영업시간: ${place.time}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                        )
                        Text(
                            text = "주소: ${place.address}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                        )

                        // 상세 페이지 보기 버튼 클릭 시 URL 이동
                        Button(
                            onClick = {
                                if (place.url.isNotEmpty()) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(place.url))
                                    intent.setPackage("com.sec.android.app.sbrowser")

                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                    } else {
                                        val defaultIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(place.url))
                                        context.startActivity(defaultIntent)
                                    }
                                } else {
                                    Toast.makeText(context, "URL이 없습니다", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("상세 페이지 보기")
                        }
                        // Instagram 버튼 추가 (링크가 있을 때만 표시)
                        if (place.instagram.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(place.instagram))
                                    // 삼성 브라우저로 연결
                                    intent.setPackage("com.sec.android.app.sbrowser")

                                    // 삼성 브라우저가 설치되어 있는지 확인
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                    } else {
                                        // 삼성 브라우저가 없으면 기본 브라우저로 연결
                                        val defaultIntent = Intent(Intent.ACTION_VIEW, Uri.parse(place.instagram))
                                        context.startActivity(defaultIntent)
                                    }
                                }
                            ) {
                                Text("Instagram")
                            }
                        }



                    }
                } ?: Text("정보 없음", modifier = Modifier.padding(16.dp))
            },
            sheetPeekHeight = 0.dp,
            modifier = modifier.fillMaxSize()
        ) {
            GoogleMap(
                modifier = modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                currentLocation?.let {location ->
                    val markerState = MarkerState(position = location)
                    var isBlinking by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        while (true) {
                            isBlinking = !isBlinking
                            delay(500)  // 500ms마다 깜빡이기
                        }
                    }

                    Marker(
                        state = markerState,
                        title = "내 위치",
                        icon = if (isBlinking) getBlinkingMarkerIcon(context) else getCustomMarkerIcon(context)
                    )
                }

                places.forEach { place ->
                    Marker(
                        state = MarkerState(position = LatLng(place.latitude, place.longitude)),
                        title = place.name,
                        snippet = "카테고리: ${place.category}",
                        onClick = {
                            selectedPlace = place
                            coroutineScope.launch {
                                scaffoldState.bottomSheetState.expand()
                            }
                            false
                        }
                    )
                }
            }
        }
    }
}
// 커스텀 마커 아이콘

fun getCustomMarkerIcon(context: Context): BitmapDescriptor {
    val drawable: Drawable = ContextCompat.getDrawable(context, R.drawable.my_location_marker)!!
    return bitmapFromDrawable(drawable)
}

// 깜빡이는 마커 아이콘

fun getBlinkingMarkerIcon(context: Context): BitmapDescriptor {
    val drawable: Drawable = ContextCompat.getDrawable(context, R.drawable.my_location_marker_blink)!!
    return bitmapFromDrawable(drawable)
}

// Drawable을 Bitmap으로 변환하는 함수
fun bitmapFromDrawable(drawable: Drawable): BitmapDescriptor {
    val canvas = Canvas()
    val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth / 10, drawable.intrinsicHeight / 10, Bitmap.Config.ARGB_8888)
    canvas.setBitmap(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}


data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val price: String,
    val category: String,
    val url: String,
    val images: List<String>,
    val time: String,
    val instagram: String
)
