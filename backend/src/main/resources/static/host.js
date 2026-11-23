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
// Inside the Flutter app a JavaScript channel named "Bridge" receives them. In a plain
// browser (development on a laptop) the page does the same jobs itself.

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
  if (window.Bridge && typeof window.Bridge.postMessage === 'function') {
    window.Bridge.postMessage(json);
  } else if (message.type === 'scan') {
    pickPhotoInBrowser(message.scanId);
  } else if (message.type === 'openUrl') {
    window.open(message.url, '_blank', 'noopener');
  }
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

async function start() {
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
        // Reading notes go to CheerpJ's persistent (IndexedDB) file system.
        'archivefever.notes.dir=/files/notes',
        ...debugProperties(),
      ],
      natives: {
        // private static native void post(String json) in archivefever.host.WebHost
        async Java_archivefever_host_WebHost_post(lib, json) {
          fromSwing(json);
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
