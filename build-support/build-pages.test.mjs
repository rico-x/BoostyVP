// SPDX-License-Identifier: MIT
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { linksToCheck, newestRelease, parseChecksums, render, repo } from './build-pages.mjs';
import { root } from './release-tools.mjs';

const ABIS = ['universal', 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'];
const release = (version, publishedAt, extra = {}) => ({
  tag_name: `v${version}`, draft: false, prerelease: true, published_at: publishedAt,
  assets: [...ABIS.map(abi => `boostyvp-v${version}-${abi}.apk`), 'SHA256SUMS', 'release-manifest.json']
    .map(name => ({ name, browser_download_url: `${repo}/releases/download/v${version}/${name}` })),
  ...extra,
});
const sums = version => ABIS.map((abi, i) => `${String(i).repeat(64)}  boostyvp-v${version}-${abi}.apk`).join('\n') + '\n';
const template = readFileSync(join(root, 'site/index.html'), 'utf8');

test('the newest published release wins, pre-releases included, drafts ignored', () => {
  const chosen = newestRelease([
    release('2026.10.23.1', '2026-10-23T10:00:00Z', { prerelease: false }),
    release('2026.10.24.2', '2026-10-24T12:00:00Z'),
    release('2026.10.24.3', null, { draft: true }),
    release('2026.10.24.1', '2026-10-24T09:00:00Z'),
  ]);
  assert.equal(chosen.version, '2026.10.24.2');
  assert.equal(chosen.prerelease, true);
  assert.equal(newestRelease([release('2026.10.24.3', null, { draft: true })]), null);
});

test('a release without every file is never advertised', () => {
  const partial = release('2026.10.24.1', '2026-10-24T09:00:00Z');
  partial.assets = partial.assets.filter(a => !a.name.endsWith('x86.apk'));
  assert.throws(() => newestRelease([partial]), /missing boostyvp-v2026.10.24.1-x86.apk/);
  const odd = release('0.0.3', '2026-10-24T09:00:00Z');
  assert.throws(() => newestRelease([odd]), /YYYY.MM.DD.N/);
});

test('every download link is the release\'s own asset, with its VirusTotal report', () => {
  const chosen = newestRelease([release('2026.10.24.2', '2026-10-24T12:00:00Z')]);
  const html = render(template, chosen, parseChecksums(sums('2026.10.24.2')), ['home.webp']);
  const links = linksToCheck(html);
  assert.equal(links.filter(l => l.endsWith('.apk')).length, 5);
  assert.ok(links.every(l => l.includes('/v2026.10.24.2/') || l.endsWith('/tag/v2026.10.24.2')));
  assert.ok(html.includes('PRE-RELEASE 2026.10.24.2'));
  assert.equal([...html.matchAll(/www\.virustotal\.com\/gui\/file\/[a-f0-9]{64}/g)].length, 5);
  assert.ok(html.includes('src="screenshots/home.webp"'));
  assert.ok(!html.includes('0.0.3'));
});

test('without a release the page has no download links at all', () => {
  const html = render(template, null, {}, []);
  assert.deepEqual(linksToCheck(html), []);
  assert.ok(html.includes('Выпусков пока нет'));
  assert.ok(!html.includes('id="screens"'), 'No empty gallery');
});

test('checksums must be well formed', () => {
  assert.throws(() => parseChecksums('zzz  boostyvp-v2026.10.24.1-x86.apk\n'), /Malformed/);
  assert.throws(() => parseChecksums(`${'a'.repeat(64)}  ../private.apk\n`), /Malformed/);
});
