# Archive Fever 서버

Spring Boot 서버입니다. 세 가지 일을 합니다.

- 로그인: 카카오 로그인, 키가 없으면 이름만 적는 개발용 로그인. 서재와 문장은 사용자별로 따로 있습니다.
- REST API: 도서 스캔(바코드 사진 → ISBN → YES24 조회), 제목으로 도서 검색, 내 서재, 수집한 문장과 그 위의 밑줄·형광펜·메모, 책 이미지
- Swing 화면을 브라우저에서 실행하는 페이지 서빙 (CheerpJ 사용)
- 서재·스캔 기록·수집한 문장·밑줄·형광펜·메모를 DB에 저장 (MySQL 권장, 설정이 없으면 H2 파일 DB)

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

제목 검색은 YES24의 상품 검색(`/goods/itemList`)을 쓰고, 결과에서 ISBN-13이 없는 것(세트 상품 등)과 eBook은 뺍니다. 검색 결과는 저장하지 않고, 고른 책만 ISBN으로 다시 조회해 저장합니다. 키가 없으면 무엇을 검색하든 같은 스텁 도서 세 권이 나옵니다("없는 책"이 들어간 검색어는 결과 없음).

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

IntelliJ에서 `Main`을 실행할 때 VM 옵션에 서버 주소를 주면, 책장과 도서 등록, 문장 수집, 친구·교환독서·알림이 서버를 씁니다. 옵션이 없으면 기존 데모 데이터로 동작합니다.

```
-Darchivefever.api=http://localhost:8080
```

데스크톱에서는 scan을 누르면 카메라 대신 파일 선택창이 뜹니다. 뒤표지 바코드 사진을 고르면 됩니다. 바코드 없이 추가하려면 Add book 화면의 **or search by title**로 제목을 검색합니다.
문장 수집(책장의 책 → scan → save)도 데스크톱에는 카메라와 글자 인식이 없어서, save를 누르면 문장과 쪽수를 직접 입력하는 창이 뜹니다.
독서 화면에서 필기구를 들지 않은 채(든 필기구를 한 번 더 누르면 내려놓습니다) 문장을 누르면 그 문장을 고치거나 지우는 창이 뜹니다. 폰과 브라우저에서는 페이지가 띄우는 입력 창이고, 데스크톱에서는 대화 상자입니다.
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
| `GET /api/books?query=…` | 제목·저자로 도서 검색. `[{isbn13, title, author, publisher, link, onShelf}]`, 최대 20권. `onShelf`는 이미 내 서재에 있는지 |
| `GET /api/books/{isbn13}` | 책 한 권을 조회(없으면 YES24에 물어봄). 추가하기 전에 표지와 함께 보여 줄 때 씀 |
| `POST /api/library/books` | 검색으로 고른 책을 내 서재에 추가. JSON `{"isbn13": "…"}` |
| `DELETE /api/library/books/{isbn13}` | 책을 서재에서 뺌. 그 책에서 수집한 문장과 표시도 함께 지움. 친구와 교환독서 중인 책은 409 `SWAPPING` |
| `GET /api/library/books/{isbn13}/passages` | 그 책에서 수집한 문장. 쪽수 순(쪽수 없는 것은 마지막), 같은 쪽은 수집한 순. 문장마다 내 표시 `marks: [{sentence, underlined, highlighted, note}]` |
| `POST /api/library/books/{isbn13}/passages` | 문장 수집. JSON `{"page": 127, "text": "…"}`, `page`는 생략 가능(1~9999), `text`는 2000자 이내. 서재에 없는 책은 404 |
| `PUT /api/library/books/{isbn13}/passages/{id}` | 문장 수정(글과 쪽수). 같은 JSON, `page`를 빼면 쪽수가 없어짐. 표시는 그대로 남음 |
| `DELETE /api/library/books/{isbn13}/passages/{id}` | 문장 삭제. 그 문장의 표시(교환 상대가 쓴 메모 포함)도 함께 지움 |
| `POST /api/library/books/{isbn13}/passages/{id}/marks` | 문장 안의 한 문장에 밑줄·형광펜·메모를 지금부터 이렇게 둔다는 요청. JSON `{"sentence": 0, "underlined": true, "highlighted": false, "note": "…"}`. 모두 끄고 메모가 비면 표시가 없어짐 |
| `GET /api/images/{isbn13}/cover.png`, `spine.png` | 표지, 책등 이미지 |
| `GET /api/health` | 서버 확인 |
| `GET /api/me` | 로그인한 사용자 `{id, nickname, provider, handle}`. 로그인하지 않았으면 401 |
| `GET /api/auth/options` | 로그인 화면이 보여 줄 방법 `{kakao, development}` |
| `POST /api/auth/dev` | 개발용 로그인. JSON `{"name": "…"}`. 꺼져 있으면 404 |
| `POST /api/auth/logout` | 로그아웃 |
| `GET /auth/kakao/login`, `/auth/kakao/callback` | 카카오로 보내고, 돌아온 브라우저를 로그인시킴 |
| `GET /api/users?query=…` | 사용자 검색(닉네임이나 아이디에 포함, `@`로 시작하면 아이디만). `[{handle, name, status}]`, 최대 20명 |
| `GET /api/friends` | 내 친구 목록 |
| `POST /api/friends/{handle}/request` | 친구 신청 |
| `POST /api/friends/{handle}/accept`, `/decline` | 받은 신청 수락·거절 |
| `DELETE /api/friends/{handle}` | 친구 삭제. 둘 사이의 교환독서도 끝나고, 그때까지의 기록은 남음 |
| `GET /api/friends/{handle}/books` | 친구의 서재. 친구가 아니면 403 |
| `GET /api/swaps` | 진행 중인 교환독서, 내가 신청해 답을 기다리는 것, 친구가 끝나 기록만 남은 것 |
| `GET /api/swaps/candidates?friend={handle}` | 그 친구와 교환독서를 신청할 수 있는 책(둘 다 가진 책) |
| `POST /api/swaps` | 교환독서 신청. JSON `{"friend": "아이디", "isbn13": "…"}` |
| `POST /api/swaps/{id}/accept`, `/decline` | 받은 신청 수락·거절 |
| `GET /api/swaps/{id}/records` | 두 사람이 그 책에서 수집한 문장과 메모 `{me, partner, book, passages, notes}`. 끝난 교환독서는 끝난 때까지의 것 |
| `POST /api/swaps/{id}/notes` | 교환독서 기록의 문장에 내 표시 두기. JSON `{"passageId": 3, "sentence": 0, "underlined": false, "highlighted": false, "text": "…"}`. `text`는 메모(1000자 이내), 모두 끄고 비면 삭제 |
| `GET /api/notifications` | 알림. 최신순 30개 |

스캔 상태: `PENDING` → `RESOLVED`(책 찾음) / `NO_BARCODE` / `NOT_FOUND` / `FAILED` / `CANCELLED` / `EXPIRED`(5분), `RESOLVED` → `CONFIRMED`.

수집한 문장은 글자만 저장합니다. 페이지 사진은 폰에서 글자를 읽는 데만 쓰이고 서버로 오지 않습니다.

밑줄·형광펜·메모는 수집한 문장(`passage`) 안의 몇 번째 문장(`sentence`, 0부터)에 붙습니다. 화면이 문장을 나누는 방식(마침표 뒤, 줄바꿈)과 같은 번호입니다. 문장을 고쳐서 문장 수가 줄면 남는 번호의 표시는 화면에 나오지 않습니다.

서재·문장·검색 요청이 거절될 때의 코드: `INVALID_ISBN`(400), `BOOK_NOT_FOUND`(404, YES24에 없는 ISBN), `NOT_ON_SHELF`(404), `PASSAGE_NOT_FOUND`(404, 남의 문장도 이렇게 답함), `SWAPPING`(409), 그리고 YES24에 물어볼 수 없을 때 502와 함께 `LOOKUP_UNAVAILABLE`(YES24 장애·한도) 또는 `CONFIG`(키가 거절됨).

`/api` 아래는 로그인해야 쓸 수 있고(세션 쿠키 `AF_SESSION`), 로그인하지 않은 요청에는 401로 답합니다. 예외는 서버 확인, 책 이미지, 로그인 자체, 그리고 폰 앱이 세션 없이 보내는 바코드 사진 업로드와 스캔 취소입니다. 뒤의 둘은 로그인한 화면이 만든 스캔의 무작위 id를 아는 것으로 허용합니다. 스캔 조회·확정, 서재, 문장은 자기 것만 보입니다. 다른 사람의 것이 보이는 경우는 둘뿐입니다: 친구의 서재, 그리고 수락된 교환독서의 기록.

### 친구·교환독서·알림

- **아이디**: 다른 사용자는 아이디(handle)로 찾고 부릅니다. 화면에는 `@`를 붙여 보입니다. 개발용 로그인은 그 이름이 아이디이고, 카카오 사용자는 처음 로그인할 때 서버가 `reader4821` 같은 아이디를 정해 줍니다(직접 고르는 것은 회원가입 화면이 생긴 뒤). 내 아이디는 프로필에 보입니다. 사용자 id(카카오 회원번호가 들어 있음)는 본인에게만 주고 다른 사용자에게는 닉네임과 아이디만 줍니다.
- **친구**: 신청하고, 상대가 수락해야 친구입니다. 검색 결과의 `status`는 내 입장에서 본 관계입니다: `NONE`, `REQUESTED`(내가 신청함), `INCOMING`(상대가 신청함), `ADDED`. 거절하거나 삭제하면 관계가 남지 않아 다시 신청할 수 있습니다.
- **교환독서**: 실물 책을 주고받는 것이 아니라, 같은 책을 가진 친구끼리 그 책의 기록을 함께 보는 것입니다. 친구여야 하고 둘 다 그 책이 서재에 있어야 신청할 수 있으며, 같은 두 사람·같은 책으로는 누가 신청하든 대기 중이거나 진행 중인 것이 하나뿐입니다. 상태는 `PENDING` → `ACTIVE`(수락) / `DECLINED`. 받은 사람만 답할 수 있고, 수락할 때 둘 다 책을 가지고 있는지 다시 확인합니다.
- **기록 공유**: 수락 전과 거절 뒤에는 아무것도 공유하지 않습니다. `ACTIVE`인 동안 두 사람에게 두 사람이 수집한 문장과 그 문장에 쓴 메모를 줍니다. 메모는 자기 것만 쓰고 고치고 지울 수 있습니다. 메모는 문장에 붙으므로(`passageId`와 그 문장 안에서 몇 번째 문장인지 `sentence`) 나중에 수집한 문장과 고친 메모는 기록을 다시 읽을 때 반영됩니다.
- **친구가 끝났을 때**: 친구를 삭제하면 진행 중이던 교환독서는 `ENDED`가 되고, 그때까지의 기록은 두 사람에게 남습니다. 남은 기록은 읽기만 됩니다(메모를 쓰면 409 `NOT_ACTIVE`). 끝난 뒤에 수집한 문장과, 끝난 뒤에 쓴 사람이 고친 메모는 들어가지 않습니다. 기록을 따로 복사해 두는 것이 아니라 끝난 시각으로 걸러서 주기 때문에, 고친 메모는 예전 글로 남지 않고 그 기록에서 빠집니다. 답을 기다리던 신청은 `WITHDRAWN`이 되고 아무것도 남기지 않습니다. 다시 친구가 되어 같은 책으로 교환독서를 시작하면 새 교환독서가 전부를 보여 주므로, 목록에는 예전 기록 대신 새것이 나옵니다.
- **알림**: 신청이 오면 받은 사람에게(`FRIEND_REQUEST`, `SWAP_REQUEST`), 답하면 신청한 사람에게(`FRIEND_ACCEPTED`·`FRIEND_DECLINED`·`SWAP_ACCEPTED`·`SWAP_DECLINED`) 생깁니다. 신청 알림의 `state`는 `PENDING`이다가 답한 뒤 `ACCEPTED`나 `DECLINED`가 됩니다. 알림에서 답할 때는 위의 친구·교환독서 주소를 씁니다.
- **거절 응답**: 요청을 받아들일 수 없으면 `{"error": "코드"}`와 함께 4xx로 답합니다. `USER_NOT_FOUND`, `SELF`, `ALREADY_FRIENDS`, `ALREADY_REQUESTED`, `REQUEST_WAITING`(상대가 이미 신청함), `NO_REQUEST`, `NOT_FRIENDS`, `NOT_COMMON`(둘 다 가진 책이 아님), `ALREADY_SWAPPING`, `NOT_YOURS_TO_ANSWER`, `ALREADY_ANSWERED`, `BOOK_MISSING`(수락하려는데 한쪽이 책을 뺌), `NOT_ACTIVE`, `SWAP_NOT_FOUND`(남의 교환독서도 이렇게 답함), `PASSAGE_NOT_FOUND`.

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
- 밑줄·형광펜·메모는 이제 DB에 저장됩니다(내 독서 화면과 교환독서 화면 모두). 그 전에 기기에 남겨 둔 것(폰은 앱 저장소, 데스크톱은 `~/.archivefever/notes`)은 그 책의 독서 화면을 처음 열 때 서버로 옮기고 기기의 파일은 지웁니다. 서버에 그 책의 표시가 이미 있으면 옮기지 않습니다. 쪽수 줄("p.12")에 해 둔 표시는 붙을 문장이 없어 옮겨지지 않습니다.
- 교환 상대에게는 내 메모만 보이고, 밑줄·형광펜은 보이지 않습니다.
- DB는 서버를 실행하는 PC마다 따로입니다. 다른 PC에서 서버를 켜면 빈 서재로 시작합니다.

자동 테스트(`gradlew test`)는 어떤 설정이 있든 메모리 안의 H2만 씁니다.

## 테스트

```powershell
.\gradlew test
```
