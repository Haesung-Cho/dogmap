const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');
const data = require('./places.json');  // places.json 파일에서 데이터 불러오기

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// Firestore에 데이터 추가 함수
async function addDataToFirestore() {
  const places = data.places;  // places.json의 장소 데이터 불러오기

  for (const place of places) {
    await db.collection('places').add(place);  // 자동 생성된 문서 ID로 각 장소를 Firestore에 추가
    console.log(`Added place: ${place.name}`);  // 콘솔에 추가된 장소 출력
  }

  console.log('모든 장소 데이터가 Firestore에 추가되었습니다.');
}

// Firestore 데이터 추가 실행
addDataToFirestore().catch(console.error);
