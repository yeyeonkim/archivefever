// Starts the Swing client inside the page with CheerpJ (a JVM that runs in the browser)
// and carries its requests to the phone app around the web view.

const status = document.getElementById('status');
const timer = document.getElementById('timer');
const started = performance.now();
const seconds = () => ((performance.now() - started) / 1000).toFixed(1);

// "?timer" shows a stopwatch for measuring start-up time on a device. It stops when the
// app is on screen; tap it to hide it.
let stopwatch = null;
if (new URLSearchParams(location.search).has('timer')) {
  timer.hidden = false;
  stopwatch = setInterval(() => { timer.textContent = seconds() + 's'; }, 100);
  timer.addEventListener('click', () => { timer.hidden = true; });
}

function fail(message, error) {
  console.error(message, error);
  status.hidden = false;
  status.className = 'error';
  status.textContent = message + (error ? ' (' + (error.message || error) + ')' : '');
}

// --- Requests from the Swing client -------------------------------------------------
//
// The Java class archivefever.host.WebHost hands over one JSON message per request:
//   {"type":"scan","scanId":"..."}   take a barcode photo and upload it to that scan
//   {"type":"openUrl","url":"..."}   open a page outside the app
//   {"type":"ready"}                 the UI is on screen
//   {"type":"camera",...}, {"type":"capture"}, {"type":"capturePage"}
//                                    live camera preview, and photos taken on it
//   {"type":"savePassage","request":3,"isbn13":"..."}
//                                    save the text the reader picked on the page photo;
//                                    answered through window.hostReply (below)
//   {"type":"signOut"}               end the session and go back to the sign-in screen
// Inside the Flutter app a JavaScript channel named "Bridge" receives them. In a plain
// browser (development on a laptop) the page does the same jobs itself, as far as it can.
// Signing out is the page's own job in both.

function fromSwing(json) {
  let message;
  try {
    message = JSON.parse(json);
  } catch (error) {
    console.error('[host] unreadable message from the app', json);
    return;
  }
  if (message.type === 'ready') {
    clearInterval(stopwatch);
    status.hidden = true;
    console.log('[host] app on screen after ' + seconds() + 's');
  }
  if (message.type === 'signOut') {
    signOut();
  } else if (window.Bridge && typeof window.Bridge.postMessage === 'function') {
    window.Bridge.postMessage(json);
  } else if (message.type === 'scan') {
    pickPhotoInBrowser(message.scanId);
  } else if (message.type === 'openUrl') {
    window.open(message.url, '_blank', 'noopener');
  } else if (message.type === 'savePassage') {
    typePassageInBrowser(message.isbn13).then((outcome) => window.hostReply(message.request, outcome));
  }
}

// How a savePassage request ended; the numbers are the order of HostBridge.PassageResult.
const SAVED = 0, NOTHING_PICKED = 1, CANCELLED = 2, FAILED = 3;

// A plain browser has no camera preview to pick lines on: the passage is typed in.
async function typePassageInBrowser(isbn13) {
  const text = window.prompt('수집할 문장을 입력하세요');
  if (text === null) return CANCELLED;
  if (text.trim() === '') return NOTHING_PICKED;
  const page = window.prompt('쪽수 (모르면 비워 두세요)', '');
  if (page === null) return CANCELLED;
  if (page.trim() !== '' && !/^\d{1,4}$/.test(page.trim())) return FAILED;
  const saved = await postPassage(isbn13, { page: page.trim() === '' ? null : Number(page.trim()), text });
  return saved ? SAVED : FAILED;
}

// Saving goes through the page because the page holds the session: the cookie that says
// whose passage this is travels with requests from here, not with the phone app's own.
async function postPassage(isbn13, passage) {
  try {
    const response = await fetch('/api/library/books/' + encodeURIComponent(isbn13) + '/passages', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(passage),
    });
    return response.ok;
  } catch (error) {
    console.error('[host] saving the passage failed', error);
    return false;
  }
}

async function signOut() {
  try {
    await fetch('/api/auth/logout', { method: 'POST' });
  } catch (error) {
    console.error('[host] signing out failed', error);
  }
  // Loading the page afresh shows the sign-in screen and drops the running Swing UI.
  location.replace('/');
}

function pickPhotoInBrowser(scanId) {
  const scan = '/api/scans/' + encodeURIComponent(scanId);
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/*';
  input.capture = 'environment';
  input.addEventListener('change', async () => {
    const photo = input.files[0];
    if (!photo) return;
    const form = new FormData();
    form.append('file', photo);
    try {
      await fetch(scan + '/image', { method: 'POST', body: form });
    } catch (error) {
      console.error('[host] upload failed', error);
    }
  });
  // Closing the chooser without a photo: stop the app waiting for one.
  input.addEventListener('cancel', () => {
    fetch(scan + '/cancel', { method: 'POST' }).catch(() => {});
  });
  input.click();
}

// --- Requests from the phone app ------------------------------------------------------

// The phone's Back button. The Swing UI has no "back", but Escape closes whatever popup
// it has open, so the app calls this instead of leaving the page.
window.hostBack = function () {
  const target = document.activeElement || document.body;
  for (const type of ['keydown', 'keyup']) {
    target.dispatchEvent(new KeyboardEvent(type, {
      key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, cancelable: true,
    }));
  }
};

// The phone app has the text and page number the reader confirmed for a savePassage
// request, and asks the page to save them. The page tells the app how it went with
// {"type":"passageSaved","request":3,"saved":true}; the app then answers the Swing UI
// through hostReply, as for any other outcome.
window.hostSavePassage = async function (request, isbn13, passage) {
  const saved = await postPassage(isbn13, passage);
  if (window.Bridge && typeof window.Bridge.postMessage === 'function') {
    window.Bridge.postMessage(JSON.stringify({ type: 'passageSaved', request, saved }));
  }
  return saved;
};

// The answer to a request that expects one (savePassage): its request number and how it
// ended. A Java thread waits for answers in WebHost.awaitReply; they queue here until it
// collects them.
const replies = [];
let wakeJava = null;
window.hostReply = function (request, outcome) {
  replies.push(request * 16 + outcome);
  if (wakeJava) {
    wakeJava();
    wakeJava = null;
  }
};

async function nextReply() {
  while (replies.length === 0) {
    await new Promise((resolve) => { wakeJava = resolve; });
  }
  return replies.shift();
}

// --- Start-up -------------------------------------------------------------------------

// For diagnosing display problems: "?web.debug=true" in the address becomes the Java
// system property archivefever.web.debug=true, and likewise for any other web.* name.
function debugProperties() {
  const properties = [];
  for (const [name, value] of new URLSearchParams(location.search)) {
    if (/^web\.[a-z]+$/.test(name) && /^[a-z0-9]*$/.test(value)) properties.push('archivefever.' + name + '=' + value);
  }
  return properties;
}

// --- Signing in -----------------------------------------------------------------------
//
// The Swing UI is only started for someone who is signed in. Until then this page shows
// the sign-in screen itself: going to Kakao and back reloads the page, and the Java
// runtime takes most of a minute to start on a phone, so it should start once, afterwards.

const signin = document.getElementById('signin');
const kakaoButton = document.getElementById('kakao');
const nameForm = document.getElementById('byname');
const nameInput = document.getElementById('name');
const signinMessage = document.getElementById('signin-message');

function fitSigninScreen() {
  const scale = Math.min(1, innerWidth / 412, innerHeight / 917);
  document.getElementById('signin-stage').style.setProperty('--scale', scale);
}

function say(message) {
  signinMessage.textContent = message;
  signinMessage.hidden = !message;
}

// The signed-in user as {id, nickname, provider, handle}, or null when nobody is signed in.
async function currentUser() {
  const response = await fetch('/api/me', { cache: 'no-store' });
  if (response.status === 401) return null;
  if (!response.ok) throw new Error('HTTP ' + response.status);
  return response.json();
}

// Shows the sign-in screen and resolves with the user once someone has signed in by name.
// Signing in with Kakao leaves the page; it comes back already signed in.
async function signIn() {
  const options = await (await fetch('/api/auth/options', { cache: 'no-store' })).json();
  kakaoButton.hidden = !options.kakao;
  nameForm.hidden = !options.development;
  nameForm.classList.toggle('alone', !options.kakao);
  // The server sends the browser back here with "?login=..." when a Kakao sign-in did not work out.
  const outcome = new URLSearchParams(location.search).get('login');
  if (outcome) {
    say(outcome === 'unavailable' ? '이 서버에는 카카오 로그인이 설정되어 있지 않아요.' : '카카오 로그인을 마치지 못했어요. 다시 시도해 주세요.');
    history.replaceState(null, '', location.pathname);
  }
  fitSigninScreen();
  addEventListener('resize', fitSigninScreen);
  status.hidden = true;
  signin.hidden = false;

  return new Promise((resolve) => {
    nameForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const button = nameForm.querySelector('button');
      button.disabled = true;
      say('');
      try {
        const response = await fetch('/api/auth/dev', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: nameInput.value }),
        });
        if (response.ok) {
          // Lets go of the field so that the keyboard goes away with the form. Inside the
          // phone app that alone leaves the keyboard up, so the app is told as well.
          nameInput.blur();
          if (window.Bridge && typeof window.Bridge.postMessage === 'function') {
            window.Bridge.postMessage(JSON.stringify({ type: 'signedIn' }));
          }
          resolve(await response.json());
          return;
        }
        say(response.status === 400 ? '이름은 영문 소문자, 숫자, 밑줄로 2~20자여야 해요.' : '로그인하지 못했어요. (' + response.status + ')');
      } catch (error) {
        say('서버에 연결하지 못했어요.');
      }
      button.disabled = false;
    });
  });
}

async function start() {
  let user;
  try {
    user = await currentUser() ?? await signIn();
  } catch (error) {
    fail('서버에 연결하지 못했습니다.', error);
    return;
  }
  removeEventListener('resize', fitSigninScreen);
  signin.hidden = true;
  status.hidden = false;

  if (typeof cheerpjInit !== 'function') {
    fail('Java 실행 환경을 불러오지 못했습니다. 인터넷 연결을 확인해 주세요.');
    return;
  }

  let manifest;
  try {
    const response = await fetch('/swing/manifest.json', { cache: 'no-store' });
    if (!response.ok) throw new Error('HTTP ' + response.status);
    manifest = await response.json();
  } catch (error) {
    fail('앱 파일을 찾지 못했습니다. 서버에서 swingWeb 빌드가 되었는지 확인해 주세요.', error);
    return;
  }

  try {
    await cheerpjInit({
      version: 17,
      // Draw at CSS-pixel size and let the browser enlarge it. At a phone's full density
      // (3x, nine times the pixels) screens took several seconds to finish drawing on a
      // Galaxy Z Flip4.
      disableScaling: true,
      javaProperties: [
        // The REST API lives on the same server as this page.
        'archivefever.api=' + location.origin,
        // Who is signed in; the Swing UI then opens on the shelf instead of its login screen.
        'archivefever.user=' + user.nickname,
        // The handle friends find them by, shown in the profile.
        'archivefever.handle=' + (user.handle || ''),
        // Reading notes go to CheerpJ's persistent (IndexedDB) file system, a folder per
        // user so that two people using one phone do not see each other's marks.
        'archivefever.notes.dir=/files/notes/' + user.id,
        ...debugProperties(),
      ],
      natives: {
        // private static native void post(String json) in archivefever.host.WebHost
        async Java_archivefever_host_WebHost_post(lib, json) {
          fromSwing(json);
        },
        // private static native int awaitReply(): only the Java thread calling it waits.
        async Java_archivefever_host_WebHost_awaitReply(lib) {
          return nextReply();
        },
      },
    });
    console.log('[host] runtime ready after ' + seconds() + 's');
    cheerpjCreateDisplay(-1, -1, document.getElementById('display'));
    // "/app/" is CheerpJ's name for the root of this web server.
    const exitCode = await cheerpjRunJar('/app/swing/' + manifest.jar);
    console.log('[host] main returned ' + exitCode + ' after ' + seconds() + 's');
  } catch (error) {
    fail('앱을 시작하지 못했습니다.', error);
  }
}

start();
