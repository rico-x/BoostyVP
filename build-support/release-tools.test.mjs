// SPDX-License-Identifier: MIT
import test from 'node:test';
import assert from 'node:assert/strict';
import { config, nextVersion, parseVersion, parseOutputs, releaseDate, signingCertificate, validateNativeAbis } from './release-tools.mjs';
const v = parseVersion('2026.10.24.1');
const fixture = () => ({ applicationId: config.applicationId, elements: [...config.abis, 'universal'].map(abi => ({
  versionName: v.name, versionCode: v.code, filters: abi === 'universal' ? [] : [{ filterType: 'ABI', value: abi }],
  outputFile: `app-${abi}-release-unsigned.apk`,
})) });
test('a version is the release date and its number that day', () => {
  assert.deepEqual(parseVersion('2026.10.24.1'), { name: '2026.10.24.1', code: 26102401, date: '2026.10.24', number: 1 });
  assert.equal(parseVersion('2026.10.24.0').code, 26102400, 'Unreleased builds are number 0');
  assert.equal(parseVersion('2099.12.31.99').code, 99123199);
  for (const bad of ['0.0.3', '2026.10.24', '2026.1.24.1', '2026.10.24.01', '2026.02.30.1', '2026.10.24.100',
    '2026.10.24.1;echo', '1999.10.24.1']) assert.throws(() => parseVersion(bad), bad);
});
test('every later release installs over an earlier one and over the old 0.x builds', () => {
  const codes = ['2026.10.24.1', '2026.10.24.2', '2026.10.25.1', '2026.11.01.1', '2027.01.01.1'].map(n => parseVersion(n).code);
  assert.deepEqual([...codes].sort((a, b) => a - b), codes);
  assert.ok(codes[0] > 7, 'Above the last 0.0.3 versionCode');
  assert.ok(parseVersion('2099.12.31.99').code <= 2_100_000_000, 'Within the Android limit');
});
test('the date follows Moscow time and the number counts that day\'s tags from 1', () => {
  const lateUtc = new Date('2026-10-24T21:30:00Z');  // already 25 October in Moscow
  assert.equal(releaseDate(lateUtc), '2026.10.25');
  const now = new Date('2026-10-24T09:00:00Z');
  assert.equal(nextVersion([], now).name, '2026.10.24.1');
  assert.equal(nextVersion(['v2026.10.23.4', 'v0.0.3', 'build-5.1'], now).name, '2026.10.24.1');
  assert.equal(nextVersion(['v2026.10.24.1', 'v2026.10.24.3', 'v2026.10.24.2'], now).name, '2026.10.24.4');
  assert.equal(nextVersion(['v2026.10.24.0', 'v2026.10.24.x'], now).name, '2026.10.24.1', 'Only real release numbers count');
});
test('all four real architectures and universal are required', () => {
  assert.equal(parseOutputs(fixture(), v).length, 5);
  const missing = fixture(); missing.elements.pop(); assert.throws(() => parseOutputs(missing, v));
  const duplicate = fixture(); duplicate.elements[0] = duplicate.elements[1]; assert.throws(() => parseOutputs(duplicate, v));
});
test('wrong application identity, versions, filter types and filenames are rejected', () => {
  for (const mutate of [m => m.applicationId = 'other', m => m.elements[0].versionCode = 6,
    m => m.elements[0].versionName = '2026.10.24.2', m => m.elements[0].filters[0].filterType = 'DENSITY',
    m => m.elements[0].outputFile = '../private.apk', m => m.elements[0].outputFile = 'app-../private.apk']) {
    const metadata = fixture(); mutate(metadata); assert.throws(() => parseOutputs(metadata, v));
  }
});
test('native libraries really match the advertised architecture', () => {
  validateNativeAbis('arm64-v8a', 'lib/arm64-v8a/native.so\n');
  validateNativeAbis('universal', config.abis.map(a => `lib/${a}/native.so`).join('\n'));
  assert.throws(() => validateNativeAbis('arm64-v8a', 'lib/armeabi-v7a/native.so'));
  assert.throws(() => validateNativeAbis('x86', 'classes.dex'));
});
test('only the persistent production certificate is accepted', () => {
  const line = `Signer #1 certificate SHA-256 digest: ${config.signerSha256}`;
  assert.equal(signingCertificate(line), config.signerSha256);
  assert.throws(() => signingCertificate(line.replace(config.signerSha256, '0'.repeat(64))));
  assert.throws(() => signingCertificate(line + '\n' + line));
});
