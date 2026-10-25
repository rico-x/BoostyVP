// SPDX-License-Identifier: MIT
// Read-only: compare the version catalog with primary Google/Maven Central metadata.
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export function compareVersions(a, b) {
  const left = a.split('-')[0].split('.').map(Number), right = b.split('-')[0].split('.').map(Number);
  for (let i = 0; i < Math.max(left.length, right.length); i++) {
    const delta = (left[i] || 0) - (right[i] || 0);
    if (delta) return delta;
  }
  return a.includes('-') && !b.includes('-') ? -1 : !a.includes('-') && b.includes('-') ? 1 : 0;
}
export function stableVersions(xml) {
  return [...xml.matchAll(/<version>([^<]+)<\/version>/g)].map(m => m[1])
    .filter(v => /^\d+(?:\.\d+){1,3}$/.test(v)).sort(compareVersions);
}
export function catalogDependencies(text) {
  const versions = {}, libraries = [];
  let section = '';
  for (const line of text.split('\n')) {
    const heading = line.match(/^\[([^\]]+)\]/);
    if (heading) { section = heading[1]; continue; }
    if (section === 'versions') {
      const value = line.match(/^([\w-]+)\s*=\s*"([^"]+)"/);
      if (value) versions[value[1]] = value[2];
    }
    if (section !== 'libraries') continue;
    const group = line.match(/group\s*=\s*"([\w.-]+)"/), name = line.match(/name\s*=\s*"([\w.-]+)"/);
    const ref = line.match(/version\.ref\s*=\s*"([\w-]+)"/), direct = line.match(/\bversion\s*=\s*"([^"]+)"/);
    if (group && name && (ref || direct)) libraries.push({ group: group[1], artifact: name[1],
      key: ref?.[1] ?? `${group[1]}:${name[1]}`, current: ref ? versions[ref[1]] : direct[1] });
  }
  // Plugin artifacts not otherwise represented by a library.
  libraries.push(...[
    ['agp', 'com.android.tools.build', 'gradle'],
    ['kotlin', 'org.jetbrains.kotlin', 'kotlin-gradle-plugin'],
    ['ksp', 'com.google.devtools.ksp', 'symbol-processing-gradle-plugin'],
  ].filter(([key]) => versions[key]).map(([key, group, artifact]) => ({ key, group, artifact, current: versions[key] })));
  return [...new Map(libraries.map(value => [value.key, value])).values()];
}
export async function auditDependency(value, fetchMetadata = fetch) {
  const path = `${value.group.replaceAll('.', '/')}/${value.artifact}/maven-metadata.xml`;
  const roots = value.group.startsWith('androidx.') || value.group.startsWith('com.android.')
    ? ['https://dl.google.com/dl/android/maven2']
    : ['https://repo.maven.apache.org/maven2', 'https://dl.google.com/dl/android/maven2'];
  const errors = [];
  for (const root of roots) {
    const source = `${root}/${path}`;
    try {
      const response = await fetchMetadata(source, { signal: AbortSignal.timeout(30_000) });
      if (!response.ok) { errors.push(`HTTP ${response.status}`); continue; }
      const metadata = await response.text();
      const available = stableVersions(metadata);
      if (!available.length) { errors.push('No stable releases in metadata'); continue; }
      const latest = available.at(-1);
      return { ...value, latest, newer: compareVersions(value.current, latest) < 0,
        currentPublished: [...metadata.matchAll(/<version>([^<]+)<\/version>/g)].some(m => m[1] === value.current), source };
    } catch (error) { errors.push(error.name || 'Network error'); }
  }
  return { ...value, error: errors.join('; ') };
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const values = catalogDependencies(readFileSync('gradle/libs.versions.toml', 'utf8'));
  const results = [];
  // Bound concurrency: do not flood public Maven repositories.
  for (let index = 0; index < values.length; index += 4) {
    results.push(...await Promise.all(values.slice(index, index + 4).map(value => auditDependency(value))));
  }
  process.stdout.write(JSON.stringify({ note: 'Latest is not necessarily compatible with this project/minSdk.', results }, null, 2) + '\n');
  if (results.some(value => value.error)) process.exitCode = 1;
}
