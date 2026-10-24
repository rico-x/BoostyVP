// SPDX-License-Identifier: MIT
import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { createClient, markdown, reportLink, scan, verdict } from './virustotal-scan.mjs';

const clean = { malicious: 0, suspicious: 0, undetected: 60, harmless: 0 };
const sha = bytes => createHash('sha256').update(bytes).digest('hex');

/** A fake API with a clock: records requests, answers from small tables. */
function fakeApi({ known = {}, analyses = {}, rateLimited = 0 } = {}) {
  let clock = 0;
  const calls = [];
  let limit = rateLimited;
  const reply = (status, body) => ({ status, ok: status < 300, json: async () => body });
  const fetch = async (url, init = {}) => {
    calls.push({ at: clock, method: init.method ?? 'GET', path: url.replace('https://www.virustotal.com/api/v3', ''), key: init.headers['x-apikey'] });
    if (limit > 0) { limit--; return reply(429, {}); }
    const [, kind, id] = url.split('/api/v3')[1].split('/');
    if (kind === 'files' && init.method === 'POST') return reply(200, { data: { id: `analysis-${calls.length}` } });
    if (kind === 'files') return known[id] ? reply(200, { data: { attributes: { last_analysis_date: 1, last_analysis_stats: known[id] } } }) : reply(404, {});
    const queue = analyses[id] ?? [clean];
    if (queue.length > 1) { queue.shift(); return reply(200, { data: { attributes: { status: 'queued' } } }); }
    return reply(200, { data: { attributes: { status: 'completed', stats: queue[0] } } });
  };
  const sleep = async ms => { clock += ms; };
  return { fetch, sleep, now: () => clock, calls, advance: ms => { clock += ms; } };
}

test('known files are looked up by hash, new ones uploaded and awaited', async () => {
  const known = Buffer.from('known apk');
  const api = fakeApi({ known: { [sha(known)]: clean } });
  const client = createClient({ apiKey: 'test-key', ...api });
  const results = await scan([{ file: 'b.apk', bytes: known }, { file: 'a.apk', bytes: Buffer.from('new apk') }], client,
    { now: api.now, log: () => {} });
  assert.deepEqual(results.map(r => r.file), ['a.apk', 'b.apk']);
  assert.deepEqual(api.calls.map(c => `${c.method} ${c.path.split('/')[1]}`), ['GET files', 'GET files', 'POST files', 'GET analyses']);
  assert.ok(api.calls.every(c => c.key === 'test-key'));
});

test('requests stay within four a minute and wait out a 429', async () => {
  const api = fakeApi({ rateLimited: 1 });
  const client = createClient({ apiKey: 'k', ...api });
  await scan([{ file: 'a.apk', bytes: Buffer.from('a') }, { file: 'b.apk', bytes: Buffer.from('b') }], client, { now: api.now, log: () => {} });
  const gaps = api.calls.slice(1).map((c, i) => c.at - api.calls[i].at);
  assert.ok(gaps.every(g => g >= 15_000), `gaps ${gaps}`);
  assert.ok(gaps[0] >= 60_000, 'Waited a minute after the 429');
});

test('a queued analysis is polled until done; past the deadline nothing passes', async () => {
  const slow = fakeApi({ analyses: { 'analysis-2': [{}, {}, clean] } });
  const results = await scan([{ file: 'a.apk', bytes: Buffer.from('x') }], createClient({ apiKey: 'k', ...slow }),
    { now: slow.now, log: () => {} });
  assert.equal(results[0].stats, clean);
  const stuck = fakeApi({ analyses: { 'analysis-2': Array(100).fill({}) } });
  await assert.rejects(scan([{ file: 'a.apk', bytes: Buffer.from('x') }], createClient({ apiKey: 'k', ...stuck }),
    { now: stuck.now, timeoutMs: 120_000, log: () => {} }), /did not finish in time/);
});

test('any malicious verdict blocks by default; suspicious only shows', () => {
  assert.equal(verdict(clean).blocked, false);
  assert.equal(verdict({ ...clean, suspicious: 2 }).blocked, false);
  assert.equal(verdict({ ...clean, malicious: 1 }).blocked, true);
  assert.equal(verdict({ ...clean, malicious: 1 }, 1).blocked, false, 'The owner may accept a known false positive');
  assert.equal(verdict(clean).engines, 60);
});

test('the release table links every report and never shows the key', () => {
  const hash = 'a'.repeat(64);
  const table = markdown([{ file: 'boostyvp-universal.apk', sha256: hash, stats: { ...clean, suspicious: 1 } }]);
  assert.ok(table.includes(reportLink(hash)));
  assert.ok(table.includes('чисто, 61 антивирусов, подозрений: 1'));
  assert.throws(() => createClient({ apiKey: '' }), /VIRUSTOTAL_API_KEY/);
});
