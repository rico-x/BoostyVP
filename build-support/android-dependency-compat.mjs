// SPDX-License-Identifier: MIT
// Primary AAR manifests/metadata, not guessed Android compatibility from version numbers.
import { mkdtempSync, writeFileSync, rmSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { stableVersions, compareVersions } from './dependency-audit.mjs';

const targets = [
  ['coreKtx', 'androidx.core', 'core-ktx'],
  ['lifecycle', 'androidx.lifecycle', 'lifecycle-runtime-android'],
  ['lifecycleCompose', 'androidx.lifecycle', 'lifecycle-runtime-compose-android'],
  ['lifecycleViewModelCompose', 'androidx.lifecycle', 'lifecycle-viewmodel-compose-android'],
  ['lifecycleSavedState', 'androidx.lifecycle', 'lifecycle-viewmodel-savedstate-android'],
  ['activityCompose', 'androidx.activity', 'activity-compose'],
  ['composeUi', 'androidx.compose.ui', 'ui-android'],
  ['navigationCompose', 'androidx.navigation', 'navigation-compose-android'],
  ['tvMaterial', 'androidx.tv', 'tv-material'],
  ['paging', 'androidx.paging', 'paging-runtime'],
  ['room', 'androidx.room', 'room-runtime-android'],
  ['datastore', 'androidx.datastore', 'datastore-preferences-android'],
  ['hiltNavigationCompose', 'androidx.hilt', 'hilt-navigation-compose'],
  ['securityCrypto', 'androidx.security', 'security-crypto'],
  ['hilt', 'com.google.dagger', 'hilt-android'],
  ['androidx.tvprovider:tvprovider', 'androidx.tvprovider', 'tvprovider'],
  ['coil3', 'io.coil-kt.coil3', 'coil-compose-android'],
  ['androidxTestExt', 'androidx.test.ext', 'junit'],
  ['testRunner', 'androidx.test', 'runner'],
  ['uiAutomator', 'androidx.test.uiautomator', 'uiautomator'],
  ['okhttpAndroid', 'com.squareup.okhttp3', 'okhttp-android'],
];
export function androidConstraints(manifest, metadata = '') {
  if (!manifest?.includes('<manifest')) throw Error('No plain AAR manifest');
  return {
    minSdk: Number(manifest.match(/android:minSdkVersion="(\d+)"/)?.[1] || 1),
    minCompileSdk: Number(metadata.match(/^minCompileSdk=(\d+)/m)?.[1] || 1),
    minAgp: metadata.match(/^(?:minAndroidGradlePluginVersion|minAgpVersion)=(.+)/m)?.[1]?.trim(),
  };
}
export function isCompatible(value) {
  return value.minSdk <= 23 && value.minCompileSdk <= 36 &&
    (!value.minAgp || compareVersions(value.minAgp, '8.13.2') <= 0) &&
    (!value.kotlinStdlib || compareVersions(value.kotlinStdlib, '2.3.21') <= 0);
}
export function kotlinRequirement(pom) {
  return [...pom.matchAll(/<artifactId>kotlin-stdlib[^<]*<\/artifactId>\s*<version>([\d.]+)<\/version>/g)]
    .map(value => value[1]).sort(compareVersions).at(-1);
}
async function check([key, group, artifact]) {
  const root = group.startsWith('androidx.') ? 'https://dl.google.com/dl/android/maven2' : 'https://repo.maven.apache.org/maven2';
  const base = `${root}/${group.replaceAll('.', '/')}/${artifact}`;
  const response = await fetch(`${base}/maven-metadata.xml`, { signal: AbortSignal.timeout(30_000) });
  if (!response.ok) return { key, error: `Metadata HTTP ${response.status}` };
  const versions = stableVersions(await response.text()).reverse();
  const directory = mkdtempSync(join(tmpdir(), 'boostyvp-aar-'));
  const archive = join(directory, 'library.aar');
  const checked = [];
  try {
    for (const version of versions.slice(0, 24)) {
      const source = `${base}/${version}/${artifact}-${version}.aar`;
      const aar = await fetch(source, { signal: AbortSignal.timeout(30_000) });
      if (!aar.ok) { checked.push({ version, error: `AAR HTTP ${aar.status}` }); continue; }
      const bytes = Buffer.from(await aar.arrayBuffer());
      if (bytes.length > 32 * 1024 * 1024) throw Error('AAR larger than audit limit');
      writeFileSync(archive, bytes);
      const manifest = spawnSync('unzip', ['-p', archive, 'AndroidManifest.xml'], { encoding: 'utf8' }).stdout;
      const metadata = spawnSync('unzip', ['-p', archive, 'META-INF/com/android/build/gradle/aar-metadata.properties'], { encoding: 'utf8' }).stdout;
      if (!manifest?.includes('<manifest')) { checked.push({ version, error: 'No plain AAR manifest' }); continue; }
      const { minSdk, minCompileSdk, minAgp } = androidConstraints(manifest, metadata);
      const pom = await fetch(`${base}/${version}/${artifact}-${version}.pom`, { signal: AbortSignal.timeout(30_000) });
      if (!pom.ok) { checked.push({ version, error: `POM HTTP ${pom.status}` }); continue; }
      const kotlinStdlib = kotlinRequirement(await pom.text());
      const value = { version, minSdk, minCompileSdk, ...(minAgp && { minAgp }),
        ...(kotlinStdlib && { kotlinStdlib }), source };
      checked.push(value);
      if (isCompatible(value)) {
        return { key, latest: versions[0], compatible: version, checked };
      }
    }
    return { key, latest: versions[0], error: 'No compatible AAR in checked range', checked };
  } finally {
    rmSync(archive, { force: true }); rmdirSync(directory);
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
const results = [];
const selected = targets.filter(value => process.argv.length <= 2 || process.argv.slice(2).includes(value[0]));
if (!selected.length) throw Error('No known dependency families selected');
for (let index = 0; index < selected.length; index += 3) {
  results.push(...await Promise.all(selected.slice(index, index + 3).map(target =>
    check(target).catch(error => ({ key: target[0], error: error.message })))));
}
process.stdout.write(JSON.stringify({ projectMinSdk: 23, projectCompileSdk: 36, projectKotlin: '2.3.21',
  note: 'Direct AAR/POM constraints only. Transitive dependencies, Kotlin metadata and build plugins still require a real Gradle build.',
  results }, null, 2) + '\n');
if (results.some(value => value.error)) process.exitCode = 1;
}
