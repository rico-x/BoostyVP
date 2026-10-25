import { test } from 'node:test';
import assert from 'node:assert/strict';
import { stableVersions, compareVersions, catalogDependencies, auditDependency } from './dependency-audit.mjs';

test('selects only stable releases with numeric ordering, including calendar versions', () => {
  assert.deepEqual(stableVersions('<version>1.9.0</version><version>1.10.0</version><version>2.0.0-rc1</version><version>2026.10.01</version>'), ['1.9.0', '1.10.0', '2026.10.01']);
  assert.equal(compareVersions('1.2', '1.2.0'), 0);
  assert.ok(compareVersions('1.1.0-alpha06', '1.1.0') < 0);
  assert.ok(compareVersions('2.1.0-1.0.29', '2.3.6') < 0);
});
test('resolves catalog references, inline versions and shared family versions', () => {
  assert.deepEqual(catalogDependencies('[versions]\nf = "1.2.0"\n[libraries]\na = { group = "a.b", name = "first", version.ref = "f" }\nb = { group = "a.b", name = "second", version.ref = "f" }\nc = { group = "c.d", name = "other", version = "2.0" }\n').map(v => [v.key, v.current]), [['f', '1.2.0'], ['c.d:other', '2.0']]);
});
test('unavailable metadata is an error, not a claim that a dependency is current', async () => {
  const value = await auditDependency({ group: 'androidx.test', artifact: 'test', current: '1.0.0' }, async () => ({ ok: false, status: 404 }));
  assert.equal(value.latest, undefined); assert.match(value.error, /404/);
});
test('reports primary metadata and whether the pinned release exists', async () => {
  const value = await auditDependency({ group: 'androidx.test', artifact: 'test', current: '1.0.0' }, async () => ({ ok: true, text: async () => '<version>1.0.0</version><version>1.1.0</version>' }));
  assert.equal(value.newer, true); assert.equal(value.currentPublished, true);
  assert.match(value.source, /^https:\/\/dl\.google\.com\//);
});
