package com.example.dogmap

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class Place(
    val name: String = "이름 없음",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "주소 없음",
    val price: String = "가격 정보 없음",
    val category: String = "카테고리 없음",
    val url: String = "링크 없음",
    val time: String = "시간 정보 없음",
    val images: List<String> = emptyList(),
    val instagram: String? = null  // Nullable로 변경
)

object FirestoreHelper {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getPlaces(): List<Place> {
        return try {
            val result = db.collection("places").get().await()
            result.documents.map { document ->
                document.toObject(Place::class.java) ?: Place()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
