// SPDX-License-Identifier: MIT
// Original release tooling for BoostyVP. No account or signing secret is read here.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
export const config = JSON.parse(readFileSync(join(root, 'build-support/release-config.json'), 'utf8'));

/**
 * Versions are the release date and the number of the release that day:
 * "2026.10.24.1". Number 0 marks a build that is not a release. The Android
 * versionCode is YYMMDDNN, so every later release installs over an earlier one.
 */
export const RELEASE_TIME_ZONE = 'Europe/Moscow';
const VERSION_PATTERN = /^(20\d{2})\.(0[1-9]|1[0-2])\.(0[1-9]|[12]\d|3[01])\.(0|[1-9]\d?)$/;

export function parseVersion(name) {
  const match = VERSION_PATTERN.exec(name ?? '');
  assert.ok(match, `Version must look like YYYY.MM.DD.N: ${name}`);
  const [year, month, day, number] = match.slice(1).map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  assert.equal(date.getUTCDate(), day, `No such date: ${name}`);
  const code = (year % 100) * 1_000_000 + month * 10_000 + day * 100 + number;
  return { name, code, date: name.slice(0, 10), number };
}

/** The calendar day of a release in the project's time zone, as "YYYY.MM.DD". */
export function releaseDate(now = new Date()) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: RELEASE_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now).map(p => [p.type, p.value]));
  return `${parts.year}.${parts.month}.${parts.day}`;
}

/** The next release today: one more than the highest `v<date>.N` tag, starting at 1. */
export function nextVersion(tags, now = new Date()) {
  const date = releaseDate(now);
  const prefix = `v${date}.`;
  const taken = tags.map(t => t.trim()).filter(t => t.startsWith(prefix))
    .map(t => t.slice(prefix.length)).filter(n => /^[1-9]\d?$/.test(n)).map(Number);
  const number = Math.max(0, ...taken) + 1;
  assert.ok(number <= 99, `Too many releases on ${date}`);
  return parseVersion(`${date}.${number}`);
}

/** BOOSTYVP_VERSION when a release is being built, otherwise today's unreleased ".0" build. */
export const version = () => parseVersion(process.env.BOOSTYVP_VERSION || `${releaseDate()}.0`);

export function parseOutputs(metadata, expected = version()) {
  assert.equal(metadata.applicationId, config.applicationId);
  assert.equal(metadata.elements?.length, config.abis.length + 1, 'All four ABIs and universal are required');
  const outputs = metadata.elements.map(e => {
    assert.equal(e.versionName, expected.name);
    assert.equal(e.versionCode, expected.code);
    assert.ok(Array.isArray(e.filters) && e.filters.length <= 1);
    if (e.filters.length) assert.equal(e.filters[0].filterType, 'ABI');
    const abi = e.filters[0]?.value ?? 'universal';
    assert.ok([...config.abis, 'universal'].includes(abi), 'Unsupported ABI');
    assert.match(e.outputFile ?? '', /^app-[A-Za-z0-9_.-]+\.apk$/);
    assert.equal(basename(e.outputFile), e.outputFile, 'No paths in artifact filenames');
    assert.ok(!e.outputFile.includes('..'));
    return { abi, file: e.outputFile };
  });
  assert.equal(new Set(outputs.map(o => o.abi)).size, outputs.length, 'Duplicate ABI');
  assert.equal(new Set(outputs.map(o => o.file)).size, outputs.length, 'Duplicate file');
  return outputs.sort((a, b) => a.abi.localeCompare(b.abi, 'en'));
}

export function nativeAbis(listing) {
  return [...new Set([...listing.matchAll(/^lib\/([^/]+)\/[^\n]+\.so$/gm)].map(m => m[1]))].sort();
}
export function validateNativeAbis(abi, listing) {
  assert.deepEqual(nativeAbis(listing), (abi === 'universal' ? [...config.abis] : [abi]).sort());
}
export function signingCertificate(text) {
  const matches = [...text.matchAll(/^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$/gm)];
  assert.equal(matches.length, 1, 'Exactly one verified signer is required');
  const digest = matches[0][1].toLowerCase();
  assert.equal(digest, config.signerSha256, 'Signing identity changed');
  return digest;
}

const sdkTool = tool => join(process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || '/opt/android-sdk', 'build-tools/35.0.0', tool);
const run = (command, args) => execFileSync(command, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
const sha256 = file => createHash('sha256').update(readFileSync(file)).digest('hex');

function inspectApk(file, abi, expected = version()) {
  validateNativeAbis(abi, run('unzip', ['-Z1', file]));
  const badging = run(sdkTool('aapt'), ['dump', 'badging', file]);
  assert.ok(badging.includes(`package: name='${config.applicationId}' versionCode='${expected.code}' versionName='${expected.name}'`));
  assert.ok(badging.includes(`sdkVersion:'${config.minSdk}'`));
  assert.ok(badging.includes(`targetSdkVersion:'${config.targetSdk}'`));
  assert.ok(badging.includes("uses-feature: name='android.software.leanback'"));
}

function makeManifest(directory) {
  const v = version();
  const expectedNames = [...config.abis, 'universal'].map(abi => `boostyvp-v${v.name}-${abi}.apk`).sort();
  assert.deepEqual(readdirSync(directory).sort(), expectedNames, 'Signing output must contain only the five APKs');
  const assets = expectedNames.map(file => {
    const abi = file.slice(`boostyvp-v${v.name}-`.length, -4);
    const path = join(directory, file);
    inspectApk(path, abi, v);
    signingCertificate(run(sdkTool('apksigner'), ['verify', '--print-certs', path]));
    run(sdkTool('zipalign'), ['-c', '-P', '16', '4', path]);
    return { file, abi, bytes: statSync(path).size, sha256: sha256(path) };
  });
  const commit = process.env.GITHUB_SHA || run('git', ['rev-parse', 'HEAD']).trim();
  assert.match(commit, /^[a-f0-9]{40}$/);
  const manifest = { applicationId: config.applicationId, versionName: v.name, versionCode: v.code,
    minSdk: config.minSdk, targetSdk: config.targetSdk, sourceCommit: commit,
    signerCertificateSha256: config.signerSha256, assets };
  writeFileSync(join(directory, 'release-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  writeFileSync(join(directory, 'SHA256SUMS'), assets.map(a => `${a.sha256}  ${a.file}\n`).join(''));
}

function verifyAssets(directory) {
  const v = version();
  const manifest = JSON.parse(readFileSync(join(directory, 'release-manifest.json'), 'utf8'));
  assert.equal(manifest.applicationId, config.applicationId);
  assert.equal(manifest.versionName, v.name);
  assert.equal(manifest.versionCode, v.code);
  assert.equal(manifest.signerCertificateSha256, config.signerSha256);
  if (process.env.GITHUB_SHA) assert.equal(manifest.sourceCommit, process.env.GITHUB_SHA);
  const names = [...config.abis, 'universal'].map(abi => `boostyvp-v${v.name}-${abi}.apk`).sort();
  assert.deepEqual(manifest.assets.map(a => a.file).sort(), names);
  assert.deepEqual(readdirSync(directory).sort(), [...names, 'SHA256SUMS', 'release-manifest.json'].sort());
  for (const asset of manifest.assets) {
    assert.equal(asset.bytes, statSync(join(directory, asset.file)).size);
    assert.equal(asset.sha256, sha256(join(directory, asset.file)));
  }
  assert.equal(readFileSync(join(directory, 'SHA256SUMS'), 'utf8'),
    [...manifest.assets].sort((a, b) => a.file < b.file ? -1 : a.file > b.file ? 1 : 0).map(a => `${a.sha256}  ${a.file}\n`).join(''));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [command, directory] = process.argv.slice(2);
  try {
    if (command === 'version') console.log(version().name);
    else if (command === 'next-version') {
      const tags = run('git', ['tag', '--list', 'v*']).split('\n').filter(Boolean);
      console.log(nextVersion(tags).name);
    }
    else if (command === 'outputs' || command === 'check-apks') {
      const outputs = parseOutputs(JSON.parse(readFileSync(join(directory, 'output-metadata.json'), 'utf8')));
      for (const o of outputs) {
        if (command === 'check-apks') inspectApk(join(directory, o.file), o.abi);
        console.log(`${o.abi}\t${o.file}`);
      }
    } else if (command === 'manifest') makeManifest(directory);
    else if (command === 'verify-assets') verifyAssets(directory);
    else throw new Error('Usage: release-tools.mjs version|next-version|outputs|check-apks|manifest|verify-assets [directory]');
  } catch (error) {
    // Only validation errors reach this CLI, never keystore contents/passwords.
    console.error(`Release validation failed: ${error.message}`);
    process.exitCode = 1;
  }
}
