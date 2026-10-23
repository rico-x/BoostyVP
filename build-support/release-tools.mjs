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

export function parseVersion(text) {
  const values = Object.fromEntries(text.trim().split(/\r?\n/).filter(l => l && !l.startsWith('#')).map(l => l.split('=')));
  assert.match(values.versionName ?? '', /^0\.(0|[1-9]\d*)\.(0|[1-9]\d*)$|^[1-9]\d*\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/);
  assert.match(values.versionCode ?? '', /^[1-9]\d*$/);
  const code = Number(values.versionCode);
  assert.ok(Number.isSafeInteger(code) && code <= 2_100_000_000);
  return { name: values.versionName, code };
}
export const version = () => parseVersion(readFileSync(join(root, 'version.properties'), 'utf8'));

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
    else if (command === 'outputs' || command === 'check-apks') {
      const outputs = parseOutputs(JSON.parse(readFileSync(join(directory, 'output-metadata.json'), 'utf8')));
      for (const o of outputs) {
        if (command === 'check-apks') inspectApk(join(directory, o.file), o.abi);
        console.log(`${o.abi}\t${o.file}`);
      }
    } else if (command === 'manifest') makeManifest(directory);
    else if (command === 'verify-assets') verifyAssets(directory);
    else throw new Error('Usage: release-tools.mjs version|outputs|check-apks|manifest|verify-assets [directory]');
  } catch (error) {
    // Only validation errors reach this CLI, never keystore contents/passwords.
    console.error(`Release validation failed: ${error.message}`);
    process.exitCode = 1;
  }
}
