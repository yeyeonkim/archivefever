# Archive Fever 모바일 앱

Flutter(Android) 앱입니다. 화면 자체는 서버가 내려주는 Swing UI를 WebView로 보여 주고, 웹 페이지가 할 수 없는 일만 앱이 맡습니다.

- 카메라로 책 뒤표지 바코드를 찍어 서버에 올리기
- 책 페이지를 찍어 글자를 읽고(폰 안에서 Google ML Kit 한국어 인식), 고른 줄을 문장으로 저장하기
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

## 문장 수집 흐름

1. 책장의 책 → scan을 누르면 Archive sentence 화면의 카메라 그림 안에 실시간 미리보기가 뜹니다.
2. scan을 누르면 페이지가 `{"type":"capturePage"}` 를 보냅니다. 앱이 사진을 찍어 미리보기 자리에 띄우고 글자를 읽습니다. 사진은 폰 밖으로 나가지 않고, 읽은 뒤 지웁니다.
3. 인식된 줄마다 테두리가 보입니다. 줄을 누르면 선택(파란색)되고, 끌면 처음 누른 줄부터 손가락 아래 줄까지 한꺼번에 선택됩니다. "다시 찍기"나 뒤로 가기를 누르면 다시 촬영합니다.
4. save를 누르면 페이지가 `{"type":"savePassage","request":3,"isbn13":"…"}` 를 보냅니다. 앱이 고른 줄을 한 문단으로 이어 붙인 글과, 사진에서 찾은 쪽수를 확인 창에 보여 줍니다. 둘 다 고칠 수 있고, 쪽수를 못 찾았으면 비어 있습니다.
5. 저장을 누르면 앱이 `POST /api/library/books/{isbn13}/passages` 로 보내고, `window.hostReply(request, 결과)` 로 페이지에 알립니다. 저장되면 Swing 화면이 그 책의 read 화면으로 넘어가 수집한 문장을 쪽수 순으로 보여 줍니다.

줄을 이어 붙일 때 띄어쓰기는 추정입니다. 한국어 책은 낱말 중간에서도 줄이 바뀌어서, 줄 끝 글자가 조사·어미처럼 보이면 띄우고 아니면 붙입니다. 틀린 곳은 확인 창에서 고치면 됩니다.

## 테스트

```powershell
flutter analyze
flutter test
```
