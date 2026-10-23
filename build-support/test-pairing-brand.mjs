// Offline browser-handoff regression. All account values below are synthetic.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { webcrypto } from 'node:crypto';
import vm from 'node:vm';

const script = readFileSync(new URL('../core/auth/src/main/resources/pairing/browser-handoff.js', import.meta.url), 'utf8');
const key = new Uint8Array(32).fill(7);
const encodedKey = Buffer.from(key).toString('base64url');
const target = `http://192.168.1.20:8765/p/${encodedKey}`;
const fixture = { accessToken: 'synthetic-access', refreshToken: 'synthetic-refresh' };

function browser(url, { pairingPage = false } = {}) {
  const location = new URL(url);
  const notices = [];
  const confirmation = { className: '', focus() {}, textContent: '' };
  let resolveNavigation;
  const navigation = new Promise(resolve => { resolveNavigation = resolve; });
  location.replace = value => resolveNavigation(new URL(value));
  const document = {
    cookie: '',
    createElement: () => ({ style: {}, setAttribute() {}, remove() {}, set textContent(value) { notices.push(value); } }),
    body: { appendChild() {} },
    getElementById: () => pairingPage ? confirmation : null,
  };
  const context = {
    location, document, crypto: webcrypto, TextEncoder, Uint8Array, URL,
    localStorage: { getItem: name => ({ auth: JSON.stringify(fixture), _clientId: 'synthetic-client' })[name] ?? null },
    atob: value => Buffer.from(value, 'base64').toString('binary'),
    btoa: value => Buffer.from(value, 'binary').toString('base64'),
  };
  context.window = context;
  vm.runInNewContext(script, context, { timeout: 1000 });
  return { navigation, notices, confirmation };
}

const valid = browser(`https://boosty.to/#boostyvp=${encodeURIComponent(target)}`);
let timeout;
const returned = await Promise.race([
  valid.navigation,
  new Promise((_, reject) => { timeout = setTimeout(() => reject(new Error('No encrypted handoff')), 5000); }),
]).finally(() => clearTimeout(timeout));
assert.equal(returned.origin, 'http://192.168.1.20:8765');
assert.equal(returned.pathname, '/link');
assert.ok(returned.hash.startsWith('#sealed='));
const sealed = Buffer.from(decodeURIComponent(returned.hash.slice('#sealed='.length)), 'base64');
const imported = await webcrypto.subtle.importKey('raw', key, 'AES-GCM', false, ['decrypt']);
const plain = await webcrypto.subtle.decrypt({ name: 'AES-GCM', iv: sealed.subarray(0, 12) }, imported, sealed.subarray(12));
assert.deepEqual(JSON.parse(Buffer.from(plain).toString('utf8')), {
  auth: JSON.stringify(fixture), clientId: 'synthetic-client',
});

const remote = browser(`https://boosty.to/#boostyvp=${encodeURIComponent(`http://example.com/p/${encodedKey}`)}`);
assert.ok(remote.notices.some(value => value.includes('Адрес ТВ потерян')));
const wrongFragment = browser(`https://boosty.to/#unrelated=${encodeURIComponent(target)}`);
assert.ok(wrongFragment.notices.some(value => value.includes('Адрес ТВ потерян')));
const local = browser('http://192.168.1.20:8765/link', { pairingPage: true });
assert.equal(local.confirmation.className, 'status ok');
assert.ok(local.confirmation.textContent.includes('Закладка работает'));
console.log('PASS: new-name encrypted handoff, remote-target rejection, fragment rejection, local bookmark check');
