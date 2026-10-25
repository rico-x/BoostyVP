// SPDX-License-Identifier: MIT
// Validate documentation artifacts locally; no network, APK install or account access.
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { root } from './release-tools.mjs';
import { screenNames } from '../site/copy.mjs';

const problems = [];
const check = (condition, message) => { if (!condition) problems.push(message); };
function markdownIn(directory) {
  if (!existsSync(directory)) return [];
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => entry.isDirectory()
    ? markdownIn(join(directory, entry.name)) : entry.name.endsWith('.md') ? [join(directory, entry.name)] : []);
}
const files = [...readdirSync(root).filter(name => name.endsWith('.md')).map(name => join(root, name)),
  ...['docs', 'release-notes', '.github'].flatMap(name => markdownIn(join(root, name))),
  ...['build-support/README.md', 'build-support/README.en.md', 'feature/pairing/README-pairing.md',
    'feature/pairing/README-pairing.en.md'].map(name => join(root, name))];
function prose(text) {
  let fence = null;
  return text.split('\n').filter(line => {
    const found = /^\s{0,3}(`{3,}|~{3,})/.exec(line);
    if (found) { fence = fence ? null : found[1][0]; return false; }
    return !fence;
  }).join('\n');
}
const anchorsCache = new Map();
function anchors(path) {
  if (anchorsCache.has(path)) return anchorsCache.get(path);
  const text = prose(readFileSync(path, 'utf8'));
  const result = new Set([...text.matchAll(/<(?:a|h[1-6])\b[^>]*(?:id|name)="([^"]+)"/g)].map(m => m[1]));
  const seen = new Map();
  for (const match of text.matchAll(/^#{1,6}\s+(.+?)\s*#*\s*$/gm)) {
    const heading = match[1].replace(/\[([^\]]+)\]\([^)]*\)/g, '$1').replace(/<[^>]*>/g, '').replace(/`/g, '');
    const slug = heading.toLowerCase().replace(/[^\p{L}\p{N}\p{M}_\-\s]/gu, '').replace(/\s/g, '-');
    const count = seen.get(slug) ?? 0;
    result.add(slug + (count ? `-${count}` : '')); seen.set(slug, count + 1);
  }
  anchorsCache.set(path, result); return result;
}
for (const file of files) {
  check(existsSync(file), `Missing document ${relative(root, file)}`);
  if (!existsSync(file)) continue;
  const text = prose(readFileSync(file, 'utf8'));
  for (const match of text.matchAll(/\[[^\]]*\]\((<[^>]+>|[^\s)]+)(?:\s+"[^"]*")?\)/g)) {
    const url = match[1].replace(/^<|>$/g, '');
    if (/^[a-z][\w+.-]*:/i.test(url)) continue;
    const [rawPath, rawAnchor] = url.split('#');
    let target, anchor;
    try {
      const decoded = decodeURIComponent(rawPath.split('?')[0]);
      target = decoded ? resolve(decoded.startsWith('/') ? root : dirname(file), decoded.replace(/^\//, '')) : file;
      anchor = rawAnchor ? decodeURIComponent(rawAnchor) : null;
    } catch { problems.push(`Malformed link ${relative(root, file)} → ${url}`); continue; }
    check(target.startsWith(root + '/') || target === root, `Link leaves repository: ${url}`);
    check(existsSync(target), `${relative(root, file)} → missing ${url}`);
    if (existsSync(target) && target.endsWith('.md') && anchor) {
      check(anchors(target).has(anchor), `${relative(root, file)} → missing anchor ${url}`);
    }
  }
}
const active = files.filter(file => !file.includes('/verification/') && !file.endsWith('ROADMAP.history.md') &&
  !file.includes('/.github/') && !file.includes('/release-notes/') && !file.endsWith('/AGENTS.md') &&
  !file.includes('/screenshots/') && !file.endsWith('.en.md'));
for (const file of active) check(existsSync(file.replace(/\.md$/, '.en.md')), `Missing English pair: ${relative(root, file)}`);
for (const name of ['README.md', 'README.en.md']) {
  const first = readFileSync(join(root, name), 'utf8').split('\n').slice(0, 9).join('\n');
  check(first.includes('[!IMPORTANT]') && first.includes('USER_GUIDE'), `${name}: user guide must be highlighted at the top`);
}
const manifestFile = join(root, 'docs/screenshots/manifest.json');
check(existsSync(manifestFile), 'Missing screenshot manifest');
if (existsSync(manifestFile)) {
  const manifest = JSON.parse(readFileSync(manifestFile, 'utf8'));
  const expected = ['ru', 'en'].flatMap(lang => screenNames.map(name => `${lang}/${name}.webp`));
  const entries = manifest.images ?? [];
  check(entries.length === expected.length, `Expected ${expected.length} screenshots, got ${entries.length}`);
  check(new Set(entries.map(item => item.path)).size === entries.length, 'Duplicate screenshot manifest entries');
  for (const path of expected) {
    const item = entries.find(image => image.path === path);
    const file = join(root, 'docs/screenshots', path);
    check(Boolean(item) && existsSync(file), `Missing screenshot ${path}`);
    if (!item || !existsSync(file)) continue;
    const bytes = readFileSync(file);
    check(bytes.subarray(0, 4).toString() === 'RIFF' && bytes.subarray(8, 12).toString() === 'WEBP', `Invalid WebP ${path}`);
    check(bytes.subarray(12, 16).toString() === 'VP8X' || bytes.subarray(12, 16).toString() === 'VP8 ', `Unsupported WebP encoding ${path}`);
    const extended = bytes.subarray(12, 16).toString() === 'VP8X';
    const width = extended ? bytes.readUIntLE(24, 3) + 1 : bytes.readUInt16LE(26) & 0x3fff;
    const height = extended ? bytes.readUIntLE(27, 3) + 1 : bytes.readUInt16LE(28) & 0x3fff;
    check(width === 1920 && height === 1080 && item.width === width && item.height === height, `Wrong size ${path}`);
    check(item.language === path.split('/')[0], `Wrong locale ${path}`);
    check(item.sha256 === createHash('sha256').update(bytes).digest('hex'), `Changed/unrecorded screenshot ${path}`);
    check(item.bytes === statSync(file).size, `Wrong byte count ${path}`);
    check(['production', 'native-fixture'].includes(item.kind), `Missing provenance ${path}`);
    check(item.kind !== 'native-fixture' || path.endsWith('/next-up.webp'), `Unexpected synthetic screenshot ${path}`);
  }
  const older = readdirSync(join(root, 'docs/screenshots')).filter(name => name.endsWith('.webp'));
  check(older.length === 0, `Unreplaced old screenshots: ${older.join(', ')}`);
}
if (problems.length) {
  console.error(problems.map(item => `- ${item}`).join('\n')); process.exitCode = 1;
} else console.log(`Documentation OK: ${files.length} Markdown files, ${active.length} RU/EN pairs, 24 verified screenshots.`);
