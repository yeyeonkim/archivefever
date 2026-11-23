# Archive Fever 서버

Spring Boot 서버입니다. 세 가지 일을 합니다.

- REST API: 도서 스캔(바코드 사진 → ISBN → YES24 조회), 내 서재, 책 이미지
- Swing 화면을 브라우저에서 실행하는 페이지 서빙 (CheerpJ 사용)
- H2 파일 DB에 서재 저장 (`data/` 폴더, git 제외)

## 실행

JDK 21이 필요합니다. Gradle은 설치하지 않아도 됩니다.

```powershell
cd backend
.\gradlew bootRun
```

- `bootRun`은 `../src`의 Swing 코드를 브라우저용 JAR로 먼저 빌드합니다.
- 브라우저에서 `http://localhost:8080/` 을 열면 Swing 화면이 뜹니다.
- 폰에서는 같은 Wi-Fi에서 `http://<노트북 IP>:8080/` 으로 접속합니다. 첫 실행 때 Windows 방화벽 허용 창이 뜨면 허용해 주세요.
- 무료 CheerpJ는 CDN에서 런타임을 받으므로, 접속하는 기기가 인터넷에 연결되어 있어야 합니다.

## YES24 API 키

키가 없으면 "스텁 도서"로 응답합니다. 나머지 흐름은 그대로 동작합니다.

키는 [developers.yes24.com](https://developers.yes24.com)에서 발급받아 둘 중 한 곳에 넣습니다. 키는 git에 올리지 않습니다.

- 환경변수 `YES24_API_KEY`
- `backend/application-local.properties` 파일 (git 제외)에 `archivefever.yes24.api-key=발급받은키`

키를 넣거나 바꾼 뒤에는 서버를 다시 시작해야 합니다. 시작 로그에 `Book lookups use the YES24 Open API` 가 나오면 적용된 것입니다.

키 없이 스캔해 "스텁 도서"로 저장된 책은, 키를 넣은 뒤 같은 책을 다시 스캔하면 실제 정보로 바뀝니다. 서재를 통째로 비우려면 서버를 끄고 `data/` 폴더를 지웁니다.

YES24 약관상 도서 정보를 보여 주는 화면에는 출처(YES24)와 상품 페이지 링크가 있어야 합니다.

## 데스크톱 Swing을 서버에 붙이기

IntelliJ에서 `Main`을 실행할 때 VM 옵션에 서버 주소를 주면, 책장과 도서 등록이 서버를 씁니다. 옵션이 없으면 기존 데모 데이터로 동작합니다.

```
-Darchivefever.api=http://localhost:8080
```

데스크톱에서는 scan을 누르면 카메라 대신 파일 선택창이 뜹니다. 뒤표지 바코드 사진을 고르면 됩니다.
문장 수집(책장의 책 → scan → save)도 데스크톱에는 카메라와 글자 인식이 없어서, save를 누르면 문장과 쪽수를 직접 입력하는 창이 뜹니다.

## API

| 요청 | 설명 |
|---|---|
| `POST /api/scans` | 스캔 생성. 상태 `PENDING` |
| `POST /api/scans/{id}/image` | 사진 업로드(multipart `file`). 응답에 판독·조회 결과 포함 |
| `GET /api/scans/{id}` | 스캔 상태 조회 |
| `POST /api/scans/{id}/cancel` | 사진을 기다리는 스캔 취소 |
| `POST /api/scans/{id}/confirm` | 조회된 책을 내 서재에 추가 |
| `GET /api/library/books` | 내 서재 |
| `GET /api/library/books/{isbn13}/passages` | 그 책에서 수집한 문장. 쪽수 순(쪽수 없는 것은 마지막), 같은 쪽은 수집한 순 |
| `POST /api/library/books/{isbn13}/passages` | 문장 수집. JSON `{"page": 127, "text": "…"}`, `page`는 생략 가능(1~9999), `text`는 2000자 이내. 서재에 없는 책은 404 |
| `GET /api/images/{isbn13}/cover.png`, `spine.png` | 표지, 책등 이미지 |
| `GET /api/health` | 서버 확인 |

스캔 상태: `PENDING` → `RESOLVED`(책 찾음) / `NO_BARCODE` / `NOT_FOUND` / `FAILED` / `CANCELLED` / `EXPIRED`(5분), `RESOLVED` → `CONFIRMED`.

수집한 문장은 글자만 저장합니다. 페이지 사진은 폰에서 글자를 읽는 데만 쓰이고 서버로 오지 않습니다.

아직 로그인이 없어서 모든 요청은 데모 사용자 한 명의 것으로 처리됩니다.

## 테스트

```powershell
.\gradlew test
```
