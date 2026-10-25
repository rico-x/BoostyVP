(function () {
  'use strict';
  var pending = null;
  var incoming = location.hash;
  // Remove the handoff from the address and history before showing any UI.
  if (incoming) history.replaceState(null, '', location.pathname + location.search);
  var form = document.getElementById('f');
  var status = document.getElementById('status');
  var setup = document.getElementById('setup');
  var review = document.getElementById('review');
  var send = document.getElementById('send-session');
  var bookmark = document.getElementById('bookmark-url');
  var boosty = document.getElementById('open-boosty');
  boosty.href = 'https://boosty.to/#boostyvp=' + encodeURIComponent(form.action);
  document.getElementById('tv-address').textContent = location.host;
  document.getElementById('connection-address').textContent = location.origin + '/link';

  var deviceGuide = document.getElementById('device-guide');
  var titles = {
    computer: 'Computer instructions',
    android: 'Android · Chrome instructions',
    ios: 'iPhone / iPad instructions'
  };
  function showGuide(device) {
    if (!Object.prototype.hasOwnProperty.call(titles, device)) device = 'computer';
    deviceGuide.value = device;
    document.getElementById('guide-title').textContent = titles[device];
    ['computer', 'android', 'ios'].forEach(function (name) {
      document.getElementById('guide-' + name).hidden = name !== device;
      document.getElementById('run-' + name).hidden = name !== device;
    });
    boosty.textContent = device === 'computer' ? 'Open Boosty on your computer' : 'Open Boosty';
    var copy = document.getElementById('copy-bookmark');
    copy.textContent = device === 'computer' ? 'Alternative: copy address' : 'Copy bookmark address';
    copy.className = device === 'computer' ? 'secondary' : '';
  }
  var agent = navigator.userAgent || '';
  var ios = /iPhone|iPad|iPod/.test(agent) ||
    (/Macintosh|MacIntel/.test(agent + ' ' + (navigator.platform || '')) && navigator.maxTouchPoints > 1);
  showGuide(ios ? 'ios' : /Android/.test(agent) ? 'android' : 'computer');
  deviceGuide.addEventListener('change', function () { showGuide(deviceGuide.value); });

  function message(text, ok) {
    status.className = ok === true ? 'status ok' : ok === false ? 'status err' : 'status';
    status.textContent = text;
    status.hidden = false;
  }
  function done() {
    pending = null;
    document.getElementById('payload').value = '';
    document.getElementById('auth').value = '';
    document.getElementById('clientId').value = '';
    setup.hidden = true;
    review.hidden = true;
    message('Done! Your session has been sent to the TV. You can close this tab.', true);
  }
  function post(url, body, contentType) {
    return fetch(url, {
      method: 'POST',
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
      headers: {'Content-Type': contentType},
      body: body
    }).then(function (response) {
      if (!response.ok) throw new Error('rejected');
      done();
    }).catch(function () {
      message('The TV did not accept your session. Keep the connection screen open on the TV, reopen /link and sign in again on the same local network.', false);
    });
  }

  function copyBookmark() {
    function fallback() {
      document.getElementById('bookmark-code').open = true;
      bookmark.focus();
      bookmark.select();
      bookmark.setSelectionRange(0, bookmark.value.length);
      try {
        if (document.execCommand('copy')) {
          message('Bookmark address copied. Paste it into the address field of your saved BoostyVP bookmark.', true);
          return;
        }
      } catch (_) {}
      message(deviceGuide.value === 'computer' ?
        'Text selected. Press Ctrl+C (⌘C on Mac) and paste it into the bookmark address.' :
        'Text selected. Press and hold, choose Copy, and paste it into the bookmark address.', true);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(bookmark.value).then(function () {
        message('Bookmark address copied. Paste it into the address field of your saved BoostyVP bookmark.', true);
      }, fallback);
    } else fallback();
  }
  document.getElementById('copy-bookmark').addEventListener('click', copyBookmark);
  document.getElementById('install-bookmark').addEventListener('click', function (event) {
    // Leave the anchor's native URL/title drag payload intact. A click explains
    // how to save it; it must not run the script and pretend it was installed.
    event.preventDefault();
    message('To save the bookmark, drag the BoostyVP button to your browser bookmarks bar. Then run the saved bookmark to test it.');
  });

  send.addEventListener('click', function () {
    if (!pending || send.disabled) return;
    send.disabled = true;
    post('/pair', pending, 'text/plain; charset=UTF-8').then(function () { send.disabled = false; });
  });
  form.addEventListener('submit', function (event) {
    event.preventDefault();
    var button = document.getElementById('send-manual');
    if (button.disabled) return;
    button.disabled = true;
    var body = new URLSearchParams(new FormData(form)).toString();
    post(form.action, body, 'application/x-www-form-urlencoded').then(function () { button.disabled = false; });
  });

  if (incoming.indexOf('#sealed=') === 0) {
    try {
      var value = decodeURIComponent(incoming.substring(8));
      if (value.length > 240000 || !/^[A-Za-z0-9+/]+={0,2}$/.test(value) || atob(value).length < 29) {
        throw new Error('invalid envelope');
      }
      pending = value;
      setup.hidden = true;
      review.hidden = false;
      send.focus();
    } catch (_) {
      message('Could not read the session. Open Boosty from the connection page and run the bookmark again.', false);
    }
  }
})();
