import { test } from 'node:test';
import assert from 'node:assert/strict';
import { androidConstraints, isCompatible, kotlinRequirement } from './android-dependency-compat.mjs';

test('reads the real AGP metadata property, not only the legacy shorthand', () => {
  const value = androidConstraints('<manifest><uses-sdk android:minSdkVersion="23"/></manifest>',
    'minCompileSdk=36\nminAndroidGradlePluginVersion=9.1.0\n');
  assert.deepEqual(value, { minSdk: 23, minCompileSdk: 36, minAgp: '9.1.0' });
  assert.equal(isCompatible(value), false);
});
test('rejects higher Android/SDK requirements and compares the full AGP version', () => {
  const base = { minSdk: 23, minCompileSdk: 36, minAgp: '8.13.2' };
  assert.equal(isCompatible(base), true);
  assert.equal(isCompatible({ ...base, minSdk: 24 }), false);
  assert.equal(isCompatible({ ...base, minCompileSdk: 37 }), false);
  assert.equal(isCompatible({ ...base, minAgp: '8.14.0' }), false);
});
test('absent optional metadata uses Android defaults; binary manifests cannot pass', () => {
  assert.deepEqual(androidConstraints('<manifest/>'), { minSdk: 1, minCompileSdk: 1, minAgp: undefined });
  assert.throws(() => androidConstraints('binary bytes'), /plain AAR manifest/);
});
test('detects a newer Kotlin stdlib requirement even when AAR SDK metadata is compatible', () => {
  const kotlinStdlib = kotlinRequirement('<artifactId>kotlin-stdlib</artifactId>\n<version>2.4.0</version>');
  assert.equal(kotlinStdlib, '2.4.0');
  assert.equal(isCompatible({ minSdk: 23, minCompileSdk: 36, kotlinStdlib }), false);
  assert.equal(isCompatible({ minSdk: 23, minCompileSdk: 36, kotlinStdlib: '2.3.10' }), true);
  assert.equal(kotlinRequirement('<project/>'), undefined);
});
