// SPDX-License-Identifier: MIT
// Static RU/EN pages. Download URLs come only from actual published assets.
import assert from 'node:assert/strict';
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { config, parseVersion, root } from './release-tools.mjs';
import { copy, screenNames } from '../site/copy.mjs';

export const repo = 'https://github.com/rico-x/BoostyVP';
const output = join(root, 'build/pages');
const screenshotsDir = join(root, 'docs/screenshots');
const ABIS = ['universal', 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'];

/** The newest release that is not a draft, by publication time; null when nothing is published. */
export function newestRelease(releases) {
  const published = releases.filter(r => !r.draft && r.published_at);
  published.sort((a, b) => b.published_at.localeCompare(a.published_at));
  const release = published[0];
  if (!release) return null;
  assert.match(release.tag_name, /^v/, 'Release tags start with v');
  const version = parseVersion(release.tag_name.slice(1)).name;
  const assets = Object.fromEntries(release.assets.map(a => [a.name, a.browser_download_url]));
  const expected = [...ABIS.map(abi => `boostyvp-v${version}-${abi}.apk`), 'SHA256SUMS', 'release-manifest.json'];
  for (const name of expected) {
    assert.ok(assets[name], `Release ${release.tag_name} is missing ${name}`);
    assert.equal(assets[name], `${repo}/releases/download/${release.tag_name}/${name}`, `Unexpected URL for ${name}`);
  }
  // SLSA provenance, attached since the release workflow signs it; older releases have none.
  const provenanceName = `boostyvp-v${version}.intoto.jsonl`;
  const provenance = assets[provenanceName] ?? null;
  if (provenance) assert.equal(provenance, `${repo}/releases/download/${release.tag_name}/${provenanceName}`, 'Unexpected provenance URL');
  return { tag: release.tag_name, version, prerelease: release.prerelease, publishedAt: release.published_at, assets, provenance };
}

/** SHA-256 per file from a release's SHA256SUMS. */
export function parseChecksums(text) {
  return Object.fromEntries(text.trim().split('\n').filter(Boolean).map(line => {
    const match = /^([a-f0-9]{64}) {2}(boostyvp-v[\w.]+-[\w-]+\.apk)$/.exec(line);
    assert.ok(match, 'Malformed SHA256SUMS line');
    return [match[2], match[1]];
  }));
}


function downloadsHtml(release, checksums, c) {
  if (!release) return `<div class="heading"><h2>${c.noDownloads}</h2><a href="${repo}/releases">${c.releases} ↗</a></div><p class="muted">${c.noDownloadsText}</p>`;
  const cards = ABIS.map((abi, index) => {
    const file = `boostyvp-v${release.version}-${abi}.apk`;
    const sha = checksums[file];
    assert.ok(sha, `No checksum for ${file}`);
    return `<div class="download${index === 0 ? ' recommended' : ''}"><a class="download-link" href="${release.assets[file]}">` +
      `<span>${c.abiTitles[index]}${index === 0 ? ` <small>${c.recommended}</small>` : ''}</span><p>${c.abiText[index]}</p><b>${c.download} ↓</b></a>` +
      `<a class="scan" href="https://www.virustotal.com/gui/file/${sha}">${c.scan} ↗</a></div>`;
  }).join('');
  const date = new Intl.DateTimeFormat(c.locale, { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(release.publishedAt));
  return `<div class="heading"><div><p class="eyebrow">${release.prerelease ? 'PRE-RELEASE' : c.release} ${release.version} · ${date}</p>` +
    `<h2>${c.choose}</h2></div><a href="${repo}/releases/tag/${release.tag}">${c.releaseNotes} ↗</a></div>` +
    `<p class="muted">${c.abiHint}</p><div class="downloads">${cards}</div><p class="note">${c.downloadNote}<br>` +
    `<a href="${release.assets.SHA256SUMS}">${c.sums}</a> · <a href="${release.assets['release-manifest.json']}">${c.manifest}</a> · ` +
    (release.provenance ? `<a href="${release.provenance}">${c.provenance}</a> · ` : '') +
    `<a href="${repo}/blob/main/docs/${c.verifyFile}">${c.verify}</a></p>`;
}

function screenshotsHtml(available, c) {
  const shots = screenNames.filter(name => available.includes(`${name}.webp`));
  if (!shots.length) return '';
  const figures = shots.map(name => {
    const caption = c.screens[screenNames.indexOf(name)];
    return `<figure><a href="${c.assets}screenshots/${c.lang}/${name}.webp"><img src="${c.assets}screenshots/${c.lang}/${name}.webp" width="1920" height="1080" loading="lazy" alt="${caption}"></a><figcaption>${caption}</figcaption></figure>`;
  }).join('');
  return `<section id="screens" class="wrap section"><p class="eyebrow">${c.screensEyebrow}</p><h2>${c.screensTitle}</h2><div class="gallery">${figures}</div></section>`;
}

export function render(template, release, checksums, screenshots, language = 'ru') {
  assert.ok(['ru', 'en'].includes(language), 'Unsupported site language');
  const c = copy[language];
  const values = Object.fromEntries(Object.entries(c).filter(([, value]) => typeof value === 'string')
    .map(([name, value]) => [name.replace(/[A-Z]/g, letter => `_${letter}`).toUpperCase(), value]));
  Object.assign(values, {
    REPO: repo, OTHER_LANG: language === 'ru' ? 'en' : 'ru',
    GUIDE_URL: `${repo}/blob/main/docs/${c.guideFile}`,
    RELEASE_LABEL: release ? `${release.prerelease ? 'PRE-RELEASE' : c.release} ${release.version}` : c.noRelease,
    HERO_ACTIONS: release ? `<a class="secondary" href="${release.assets[`boostyvp-v${release.version}-universal.apk`]}">${c.universal} ↓</a>` :
      `<a class="secondary" href="${repo}/releases">${c.releases} ↗</a>`,
    VERSION_FOOTER: release ? release.version : c.unpublished,
    NAV: ['features', 'screens', 'downloads', 'docs'].flatMap((id, i) => id === 'screens' && !screenNames.some(name => screenshots.includes(`${name}.webp`)) ? [] : [`<a href="#${id}">${c.nav[i]}</a>`]).join(''),
    FEATURES: c.features.map(([title, text], i) => `<article><span class="number">${String(i + 1).padStart(2, '0')}</span><h3>${title}</h3><p>${text}</p></article>`).join(''),
    DOCS: c.docs.map(([label, path]) => `<a href="${repo}/blob/main/${path}">${label} <span>↗</span></a>`).join(''),
    DOWNLOADS: downloadsHtml(release, checksums, c), SCREENSHOTS: screenshotsHtml(screenshots, c),
  });
  const html = template.replace(/{{([A-Z_]+)}}/g, (_, name) => {
    assert.ok(Object.hasOwn(values, name), `Unknown site token ${name}`);
    return values[name];
  });
  assert.ok(!html.includes('{{'), 'Unresolved site template');
  assert.ok(!/<script\b|<iframe\b|@import/i.test(html), 'Site must be static and self-contained');
  for (const [, href] of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
    const allowed = href.startsWith('https://github.com/') || href.startsWith('#') ||
      ['en/', '../', 'icon.svg', 'styles.css', '../icon.svg', '../styles.css'].includes(href) ||
      /^(?:\.\.\/)?screenshots\/(?:ru|en)\/[a-z-]+\.webp$/.test(href) ||
      /^https:\/\/www\.virustotal\.com\/gui\/file\/[a-f0-9]{64}$/.test(href);
    assert.ok(allowed, `Unexpected resource: ${href}`);
  }
  return html;
}

export const linksToCheck = html => [...new Set([...html.matchAll(/href="(https:\/\/github\.com\/[^"]+\/releases\/(?:download|tag)\/[^"]+)"/g)].map(m => m[1]))];

function build(releaseDir) {
  const releasesFile = releaseDir && join(releaseDir, 'releases.json');
  const release = releasesFile && existsSync(releasesFile) ? newestRelease(JSON.parse(readFileSync(releasesFile, 'utf8'))) : null;
  const checksums = release ? parseChecksums(readFileSync(join(releaseDir, 'SHA256SUMS'), 'utf8')) : {};
  const template = readFileSync(join(root, 'site/index.html'), 'utf8');
  rmSync(output, { recursive: true, force: true });
  for (const lang of ['ru', 'en']) {
    const images = join(screenshotsDir, lang);
    const screenshots = existsSync(images) ? readdirSync(images).filter(f => f.endsWith('.webp')) : [];
    const page = join(output, copy[lang].path);
    mkdirSync(page, { recursive: true });
    writeFileSync(join(page, 'index.html'), render(template, release, checksums, screenshots, lang));
    mkdirSync(join(output, 'screenshots', lang), { recursive: true });
    for (const file of screenshots) copyFileSync(join(images, file), join(output, 'screenshots', lang, file));
  }
  for (const file of ['styles.css', 'icon.svg']) copyFileSync(join(root, 'site', file), join(output, file));
  writeFileSync(join(output, '.nojekyll'), '');
  writeFileSync(join(output, 'version.json'), JSON.stringify({ version: release?.version ?? null,
    prerelease: release?.prerelease ?? null, published: release?.publishedAt ?? null,
    applicationId: config.applicationId, repository: repo, languages: ['ru', 'en'] }) + '\n');
  console.log(release ? `RU/EN site built for ${release.tag}` : 'RU/EN site built without a release');
}

async function checkLinks() {
  const links = new Set(['index.html', 'en/index.html'].flatMap(file => linksToCheck(readFileSync(join(output, file), 'utf8'))));
  for (const url of links) {
    const response = await fetch(url, { method: 'HEAD', redirect: 'follow' });
    assert.equal(response.status, 200, `${url} answers HTTP ${response.status}`);
  }
  console.log(`All ${links.size} release links answer 200`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [command = 'build', argument] = process.argv.slice(2);
  try {
    if (command === 'newest-tag') console.log(newestRelease(JSON.parse(readFileSync(argument, 'utf8')))?.tag ?? '');
    else if (command === 'build') build(argument);
    else if (command === 'check-links') await checkLinks();
    else throw new Error('Usage: build-pages.mjs newest-tag <releases.json> | build [release dir] | check-links');
  } catch (error) { console.error(`Pages build failed: ${error.message}`); process.exitCode = 1; }
}
