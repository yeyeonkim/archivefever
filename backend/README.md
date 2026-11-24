# Archive Fever 서버

Spring Boot 서버입니다. 세 가지 일을 합니다.

- 로그인: 카카오 로그인, 키가 없으면 이름만 적는 개발용 로그인. 서재와 문장은 사용자별로 따로 있습니다.
- REST API: 도서 스캔(바코드 사진 → ISBN → YES24 조회), 내 서재, 책 이미지
- Swing 화면을 브라우저에서 실행하는 페이지 서빙 (CheerpJ 사용)
- 서재·스캔 기록·수집한 문장을 DB에 저장 (MySQL 권장, 설정이 없으면 H2 파일 DB)

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

키 없이 스캔해 "스텁 도서"로 저장된 책은, 키를 넣은 뒤 같은 책을 다시 스캔하면 실제 정보로 바뀝니다.

YES24 약관상 도서 정보를 보여 주는 화면에는 출처(YES24)와 상품 페이지 링크가 있어야 합니다.

## 로그인

주소를 열면 먼저 로그인 화면이 뜹니다. 로그인하면 그 사람의 서재로 Swing 화면이 시작되고, 30일 동안은 다시 묻지 않습니다. 로그아웃은 menu → profile → logout 입니다.

### 개발용 로그인 (카카오 키가 없을 때)

이름(영문 소문자·숫자·밑줄 2~20자)만 적으면 들어갑니다. 같은 이름은 같은 사용자입니다. 비밀번호가 없으니 개발과 시연 준비에만 씁니다. 로그인이 생기기 전에 넣은 책은 `demo` 라는 이름의 서재에 있습니다.

### 카카오 로그인

[developers.kakao.com](https://developers.kakao.com)에서 앱을 만들고 다음을 설정합니다.

1. 앱 키의 **REST API 키**를 `backend/application-local.properties`에 적습니다. 키에 클라이언트 시크릿이 켜져 있으면(새 키는 기본으로 켜져 있습니다) 시크릿도 적습니다.
   ```properties
   archivefever.kakao.rest-api-key=REST_API_키
   archivefever.kakao.client-secret=클라이언트_시크릿
   ```
2. 카카오 로그인을 **사용**으로 켜고, **Redirect URI**에 폰과 브라우저가 서버에 접속하는 주소 그대로 `/auth/kakao/callback`을 붙여 등록합니다. 예: `http://192.168.0.10:8080/auth/kakao/callback`. 글자 하나라도 다르면 카카오가 KOE006 오류를 냅니다. 노트북 IP가 바뀌면 다시 등록해야 하므로, 공유기에서 IP를 고정해 두는 편이 좋습니다.
3. 동의항목에서 **닉네임**을 **필수 동의**로 설정합니다. 받지 않으면 이름이 "카카오 사용자"로 표시됩니다. 선택 동의로 나중에 추가하면, 그 전에 로그인했던 계정에는 동의 화면이 다시 뜨지 않아 닉네임이 오지 않습니다.
4. REST API 키에 **호출 허용 IP**가 걸려 있으면 서버의 공인 IP(공유기 바깥에서 보이는 주소, `192.168…`이 아님)를 등록합니다. 등록되지 않은 곳에서는 로그인 도중 카카오 화면에 "앱 관리자 설정 오류"가 뜹니다. 공인 IP가 바뀌거나 서버를 다른 네트워크로 옮기면 다시 등록해야 합니다.

서버를 다시 시작하면 시작 로그에 `Sign-in uses Kakao Login` 이 나오고, 로그인 화면에 카카오 로그인 버튼이 생기며 개발용 로그인은 사라집니다. 둘 다 두려면 `archivefever.auth.dev-login=true` 를 추가합니다.

폰 앱에서는 카카오 로그인 페이지가 앱 안에서 열립니다. 카카오톡 앱으로 넘어가는 간편로그인은 연결되어 있지 않아, 카카오계정(아이디·비밀번호)으로 로그인합니다.

## 다른 네트워크에서 쓰기 (ngrok)

집 밖에서는 노트북의 주소가 달라지고, 학교 Wi-Fi처럼 기기끼리의 접속을 막는 곳도 있습니다. 터널을 쓰면 폰이 어디서든 같은 https 주소로 접속합니다. 노트북과 폰이 같은 Wi-Fi일 필요도 없습니다.

1. 서버를 켠 뒤 다른 터미널에서 `ngrok http 8080` 을 실행합니다. `Forwarding` 줄에 나오는 `https://….ngrok-free.app` 이 주소입니다. 무료 계정도 계정마다 주소가 하나 정해져 있어, 다시 실행해도 같은 주소가 나옵니다.
2. 폰 앱의 서버 주소에 그 주소를 `https://` 까지 넣어 입력합니다.
3. 카카오 콘솔의 Redirect URI에 `https://그-주소/auth/kakao/callback` 을 추가합니다. 주소가 바뀌지 않으므로 한 번만 하면 됩니다. 집에서 쓰는 `http://192.168…` 주소와 함께 등록해 둘 수 있습니다.

터널로도 해결되지 않는 것이 하나 있습니다. 카카오 키에 호출 허용 IP가 걸려 있으면, 서버가 카카오를 호출할 때 나가는 주소는 여전히 그 장소의 공인 IP입니다. 장소를 옮기면 허용 IP에 그곳의 공인 IP를 추가해야 합니다.

무료 요금제는 한 달에 전송 1GB, 요청 2만 건까지입니다. Swing JAR이 약 46MB라 폰이 새 빌드를 받을 때마다 그만큼 씁니다(같은 빌드는 폰에 남아 다시 받지 않습니다). 집에서는 `192.168…` 주소로 쓰고, 밖에서만 터널을 켜는 편이 좋습니다. 처음 접속할 때 ngrok 안내 화면이 뜨면 Visit Site를 누릅니다.

터널을 켜 두는 동안에는 주소를 아는 누구나 로그인 화면에 닿을 수 있습니다. 서재와 문장은 로그인해야 보이지만, 쓰지 않을 때는 터널을 끄세요.

## 데스크톱 Swing을 서버에 붙이기

IntelliJ에서 `Main`을 실행할 때 VM 옵션에 서버 주소를 주면, 책장과 도서 등록이 서버를 씁니다. 옵션이 없으면 기존 데모 데이터로 동작합니다.

```
-Darchivefever.api=http://localhost:8080
```

데스크톱에서는 scan을 누르면 카메라 대신 파일 선택창이 뜹니다. 뒤표지 바코드 사진을 고르면 됩니다.
문장 수집(책장의 책 → scan → save)도 데스크톱에는 카메라와 글자 인식이 없어서, save를 누르면 문장과 쪽수를 직접 입력하는 창이 뜹니다.
카카오 로그인 버튼을 누르면 이름을 묻는 창이 뜹니다. 데스크톱은 개발용 로그인만 되므로, 서버가 카카오 로그인만 받도록 되어 있으면 로그인할 수 없습니다.

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
| `GET /api/me` | 로그인한 사용자 `{id, nickname, provider}`. 로그인하지 않았으면 401 |
| `GET /api/auth/options` | 로그인 화면이 보여 줄 방법 `{kakao, development}` |
| `POST /api/auth/dev` | 개발용 로그인. JSON `{"name": "…"}`. 꺼져 있으면 404 |
| `POST /api/auth/logout` | 로그아웃 |
| `GET /auth/kakao/login`, `/auth/kakao/callback` | 카카오로 보내고, 돌아온 브라우저를 로그인시킴 |

스캔 상태: `PENDING` → `RESOLVED`(책 찾음) / `NO_BARCODE` / `NOT_FOUND` / `FAILED` / `CANCELLED` / `EXPIRED`(5분), `RESOLVED` → `CONFIRMED`.

수집한 문장은 글자만 저장합니다. 페이지 사진은 폰에서 글자를 읽는 데만 쓰이고 서버로 오지 않습니다.

`/api` 아래는 로그인해야 쓸 수 있고(세션 쿠키 `AF_SESSION`), 로그인하지 않은 요청에는 401로 답합니다. 예외는 서버 확인, 책 이미지, 로그인 자체, 그리고 폰 앱이 세션 없이 보내는 바코드 사진 업로드와 스캔 취소입니다. 뒤의 둘은 로그인한 화면이 만든 스캔의 무작위 id를 아는 것으로 허용합니다. 스캔 조회·확정, 서재, 문장은 자기 것만 보입니다.

## 데이터베이스

서재, 스캔 기록, 수집한 문장이 DB에 저장됩니다. 서버를 껐다 켜도, 노트북을 재부팅해도 남습니다. 테이블은 서버가 처음 뜰 때 자동으로 만들어집니다.

### MySQL (권장)

MySQL 8을 설치한 뒤 DB와 계정을 한 번 만듭니다(Workbench 등에서 실행).

```sql
CREATE DATABASE archivefever CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'archivefever'@'localhost' IDENTIFIED BY '원하는비밀번호';
GRANT ALL PRIVILEGES ON archivefever.* TO 'archivefever'@'localhost';
```

`backend/application-local.properties`(git 제외, YES24 키를 넣는 그 파일)에 접속 정보를 적습니다.

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/archivefever?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=archivefever
spring.datasource.password=원하는비밀번호
```

서버를 다시 시작하면 시작 로그에 `Database driver: MySQL Connector/J` 가 나옵니다. 서재를 통째로 비우려면 서버를 끄고 `DROP DATABASE archivefever;` 후 위의 `CREATE DATABASE`를 다시 실행합니다.

### H2 파일 (설정이 없을 때)

위 세 줄이 없으면 설치가 필요 없는 H2 파일 DB(`backend/data/archivefever.mv.db`)를 씁니다. 바로 실행해 볼 수 있지만, 잃으면 안 되는 데이터에는 권하지 않습니다. 서버가 정상 종료되지 않고 죽었을 때 10분 전에 저장한 문장이 사라진 적이 있습니다. 그 뒤로 DB 주소에 `REUSE_SPACE=FALSE`를 넣어 두었으니 지우지 마세요. 서재를 비우려면 서버를 끄고 `data/` 폴더를 지웁니다.

두 DB의 데이터는 서로 옮겨지지 않습니다. DB를 바꾸면 빈 서재로 시작합니다.

### DB에 들어가지 않는 것

- 표지·책등 이미지(`data/images`)와 올린 바코드 사진(`data/uploads`)은 어느 DB를 쓰든 `backend/data/` 폴더에 있습니다. 이 폴더는 git에 올라가지 않습니다.
- 밑줄·형광펜·메모는 아직 서버로 가지 않습니다. 폰에서는 앱(WebView) 저장소에, 데스크톱에서는 `~/.archivefever/notes`에 남으므로 앱 데이터를 지우면 사라집니다.
- DB는 서버를 실행하는 PC마다 따로입니다. 다른 PC에서 서버를 켜면 빈 서재로 시작합니다.

자동 테스트(`gradlew test`)는 어떤 설정이 있든 메모리 안의 H2만 씁니다.

## 테스트

```powershell
.\gradlew test
```
