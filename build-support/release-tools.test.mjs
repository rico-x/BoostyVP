// SPDX-License-Identifier: MIT
import test from 'node:test';
import assert from 'node:assert/strict';
import { config, parseVersion, parseOutputs, signingCertificate, validateNativeAbis } from './release-tools.mjs';
const v = { name: '0.0.3', code: 7 };
const fixture = () => ({ applicationId: config.applicationId, elements: [...config.abis, 'universal'].map(abi => ({
  versionName: v.name, versionCode: v.code, filters: abi === 'universal' ? [] : [{ filterType: 'ABI', value: abi }],
  outputFile: `app-${abi}-release-unsigned.apk`,
})) });
test('version is explicit and its Android counter remains above the local version', () => {
  assert.deepEqual(parseVersion('versionName=0.0.3\nversionCode=7\n'), v);
  for (const bad of ['0.0.3;echo bad', '0.0', '00.0.3']) assert.throws(() => parseVersion(`versionName=${bad}\nversionCode=7`));
  for (const bad of ['0', '-1', '7oops', '999999999999']) assert.throws(() => parseVersion(`versionName=0.0.3\nversionCode=${bad}`));
});
test('all four real architectures and universal are required', () => {
  assert.equal(parseOutputs(fixture(), v).length, 5);
  const missing = fixture(); missing.elements.pop(); assert.throws(() => parseOutputs(missing, v));
  const duplicate = fixture(); duplicate.elements[0] = duplicate.elements[1]; assert.throws(() => parseOutputs(duplicate, v));
});
test('wrong application identity, versions, filter types and filenames are rejected', () => {
  for (const mutate of [m => m.applicationId = 'other', m => m.elements[0].versionCode = 6,
    m => m.elements[0].versionName = '0.2.1', m => m.elements[0].filters[0].filterType = 'DENSITY',
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
