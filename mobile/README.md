# Archive Fever 모바일 앱

Flutter(Android) 앱입니다. 화면 자체는 서버가 내려주는 Swing UI를 WebView로 보여 주고, 웹 페이지가 할 수 없는 일만 앱이 맡습니다.

- 카메라로 책 뒤표지 바코드를 찍어 서버에 올리기
- YES24 상품 링크를 브라우저로 열기

## 실행

먼저 노트북에서 서버를 켭니다([backend/README.md](../backend/README.md)).

```powershell
cd mobile
flutter run
```

- 폰은 USB 디버깅을 켜고 연결합니다. 노트북과 같은 Wi-Fi여야 합니다.
- 첫 화면에서 노트북 주소를 입력합니다. 예: `192.168.0.10:8080` (노트북에서 `ipconfig`로 확인)
- 주소가 맞으면 저장되고, 다음부터는 바로 앱 화면이 뜹니다.

APK만 만들려면 `flutter build apk --debug` 를 실행합니다. 결과물은 `build/app/outputs/flutter-apk/app-debug.apk` 입니다.

## 스캔 흐름

1. Swing 화면의 add book → scan을 누르면, 페이지가 `Bridge` 채널로 `{"type":"scan","scanId":"…"}` 를 보냅니다.
2. 앱이 카메라를 열고, 찍은 사진을 `POST /api/scans/{scanId}/image` 로 올립니다.
3. 결과(책 확인 팝업)는 Swing 화면이 서버에서 읽어 보여 줍니다.

촬영을 취소하면 앱이 서버의 스캔을 취소합니다. 업로드에 실패하면 "다시 시도"가 뜹니다.

## 테스트

```powershell
flutter analyze
flutter test
```
