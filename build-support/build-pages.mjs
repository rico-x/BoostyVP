// SPDX-License-Identifier: MIT
// Builds the static project site for the newest published release, pre-releases
// included. Every download link comes from that release's real assets, so the
// page can never advertise a version or file that does not exist.
import assert from 'node:assert/strict';
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { config, parseVersion, root } from './release-tools.mjs';

export const repo = 'https://github.com/rico-x/BoostyVP';
const output = join(root, 'build/pages');
const screenshotsDir = join(root, 'docs/screenshots');

const ABIS = [
  ['universal', 'Универсальная <small>Рекомендуется</small>', 'Все четыре архитектуры в одном APK'],
  ['armeabi-v7a', 'ARMv7', '32-битные телевизоры и приставки'],
  ['arm64-v8a', 'ARM64', 'Устройства с 64-битной Android'],
  ['x86', 'x86', '32-битная Android x86'],
  ['x86_64', 'x86_64', '64-битная Android x86 и эмуляторы'],
];
const SCREENSHOTS = [
  ['home', 'Подписки по авторам и «Продолжить просмотр»'],
  ['new', 'Лента «Новое» по всем подпискам'],
  ['author', 'Страница автора'],
  ['player', 'Плеер с панелью управления'],
  ['next-up', '«Далее»: следующее видео автора с отсчётом'],
  ['player-settings', 'Настройки плеера'],
  ['interface', 'Настройки внешнего вида'],
];

/** The newest release that is not a draft, by publication time; null when nothing is published. */
export function newestRelease(releases) {
  const published = releases.filter(r => !r.draft && r.published_at);
  published.sort((a, b) => b.published_at.localeCompare(a.published_at));
  const release = published[0];
  if (!release) return null;
  assert.match(release.tag_name, /^v/, 'Release tags start with v');
  const version = parseVersion(release.tag_name.slice(1)).name;
  const assets = Object.fromEntries(release.assets.map(a => [a.name, a.browser_download_url]));
  const expected = [...ABIS.map(([abi]) => `boostyvp-v${version}-${abi}.apk`), 'SHA256SUMS', 'release-manifest.json'];
  for (const name of expected) {
    assert.ok(assets[name], `Release ${release.tag_name} is missing ${name}`);
    assert.equal(assets[name], `${repo}/releases/download/${release.tag_name}/${name}`, `Unexpected URL for ${name}`);
  }
  return { tag: release.tag_name, version, prerelease: release.prerelease, publishedAt: release.published_at, assets };
}

/** SHA-256 per file from a release's SHA256SUMS. */
export function parseChecksums(text) {
  return Object.fromEntries(text.trim().split('\n').filter(Boolean).map(line => {
    const match = /^([a-f0-9]{64}) {2}(boostyvp-v[\w.]+-[\w-]+\.apk)$/.exec(line);
    assert.ok(match, 'Malformed SHA256SUMS line');
    return [match[2], match[1]];
  }));
}

const dateRu = iso => new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Europe/Moscow' })
  .format(new Date(iso));

function downloadsHtml(release, checksums) {
  if (!release) {
    return `<div class="heading"><div><p class="eyebrow">ВЫПУСКИ</p><h2>Выпусков пока нет.</h2></div>` +
      `<a href="${repo}/releases">Страница выпусков ↗</a></div><p class="muted">Первый подписанный выпуск появится здесь ` +
      `сразу после публикации. Ссылки на файлы берутся из самого выпуска.</p>`;
  }
  const cards = ABIS.map(([abi, title, text], i) => {
    const file = `boostyvp-v${release.version}-${abi}.apk`;
    const sha = checksums[file];
    assert.ok(sha, `No checksum for ${file}`);
    return `<div class="download${i === 0 ? ' recommended' : ''}"><a class="download-link" href="${release.assets[file]}">` +
      `<span>${title}</span><p>${text}</p><b>Скачать ↓</b></a>` +
      `<a class="scan" href="https://www.virustotal.com/gui/file/${sha}">Отчёт VirusTotal ↗</a></div>`;
  }).join('');
  const kind = release.prerelease ? 'PRE-RELEASE' : 'ВЫПУСК';
  return `<div class="heading"><div><p class="eyebrow">${kind} ${release.version} · ${dateRu(release.publishedAt)}</p>` +
    `<h2>Выберите свою сборку.</h2></div><a href="${repo}/releases/tag/${release.tag}">Что нового и все файлы ↗</a></div>` +
    `<p class="muted">Не уверены в архитектуре Android на ТВ? Выберите универсальный APK. ` +
    `64-битный процессор не всегда означает 64-битную ОС.</p><div class="downloads">${cards}</div>` +
    `<p class="note">Один пакет и прежний ключ подписи. Для обновления не удаляйте приложение. ` +
    `Каждый файл перед публикацией проверен ClamAV и VirusTotal.<br>` +
    `<a href="${release.assets.SHA256SUMS}">Контрольные суммы</a> · <a href="${release.assets['release-manifest.json']}">Манифест сборки</a> · ` +
    `<a href="${repo}/blob/main/docs/RELEASING.md">Как проверить подпись</a></p>`;
}

function heroHtml(release) {
  if (!release) {
    return { label: 'СКОРО ПЕРВЫЙ ВЫПУСК', actions: `<a class="button" href="${repo}/releases">Страница выпусков <span>↗</span></a>` };
  }
  const universal = release.assets[`boostyvp-v${release.version}-universal.apk`];
  return {
    label: `${release.prerelease ? 'PRE-RELEASE' : 'ВЫПУСК'} ${release.version}`,
    actions: `<a class="button" href="${universal}">Скачать универсальный APK <span>↓</span></a>` +
      `<a class="secondary" href="${repo}/releases/tag/${release.tag}">Что нового ↗</a>`,
  };
}

function screenshotsHtml(available) {
  const shots = SCREENSHOTS.filter(([name]) => available.includes(`${name}.webp`));
  if (!shots.length) return '';
  const figures = shots.map(([name, caption]) =>
    `<figure><img src="screenshots/${name}.webp" width="1920" height="1080" loading="lazy" alt="${caption}"><figcaption>${caption}</figcaption></figure>`).join('');
  return `<section id="screens" class="wrap section"><p class="eyebrow">КАК ЭТО ВЫГЛЯДИТ</p><h2>Снято на телевизоре.</h2>` +
    `<div class="gallery">${figures}</div></section>`;
}

export function render(template, release, checksums, screenshots) {
  const hero = heroHtml(release);
  const html = template
    .replaceAll('{{RELEASE_LABEL}}', hero.label)
    .replaceAll('{{HERO_ACTIONS}}', hero.actions)
    .replaceAll('{{DOWNLOADS}}', downloadsHtml(release, checksums))
    .replaceAll('{{SCREENSHOTS}}', screenshotsHtml(screenshots))
    .replaceAll('{{VERSION_FOOTER}}', release ? release.version : 'выпусков пока нет')
    .replaceAll('{{REPO}}', repo);
  assert.ok(!html.includes('{{'), 'Unresolved site template');
  assert.ok(!/<script\b|<iframe\b|@import/i.test(html), 'Site must be static and self-contained');
  for (const [, href] of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
    const allowed = href.startsWith('https://github.com/') || href.startsWith('#') ||
      ['icon.svg', 'styles.css'].includes(href) || /^screenshots\/[a-z-]+\.webp$/.test(href) ||
      /^https:\/\/www\.virustotal\.com\/gui\/file\/[a-f0-9]{64}$/.test(href);
    assert.ok(allowed, `Unexpected external resource: ${href}`);
  }
  return html;
}

/** Links that must answer before the site goes live: the release files and its page. */
export const linksToCheck = html => [...new Set([...html.matchAll(/href="(https:\/\/github\.com\/[^"]+\/releases\/(?:download|tag)\/[^"]+)"/g)].map(m => m[1]))];

function build(releaseDir) {
  const releasesFile = releaseDir && join(releaseDir, 'releases.json');
  const release = releasesFile && existsSync(releasesFile) ? newestRelease(JSON.parse(readFileSync(releasesFile, 'utf8'))) : null;
  const checksums = release ? parseChecksums(readFileSync(join(releaseDir, 'SHA256SUMS'), 'utf8')) : {};
  const screenshots = existsSync(screenshotsDir) ? readdirSync(screenshotsDir).filter(f => f.endsWith('.webp')) : [];
  const html = render(readFileSync(join(root, 'site/index.html'), 'utf8'), release, checksums, screenshots);
  rmSync(output, { recursive: true, force: true });
  mkdirSync(join(output, 'screenshots'), { recursive: true });
  writeFileSync(join(output, 'index.html'), html);
  for (const file of ['styles.css', 'icon.svg']) copyFileSync(join(root, 'site', file), join(output, file));
  for (const file of screenshots) copyFileSync(join(screenshotsDir, file), join(output, 'screenshots', file));
  writeFileSync(join(output, '.nojekyll'), '');
  writeFileSync(join(output, 'version.json'), JSON.stringify({
    version: release?.version ?? null, prerelease: release?.prerelease ?? null,
    published: release?.publishedAt ?? null, applicationId: config.applicationId, repository: repo,
  }) + '\n');
  console.log(release ? `Site built for ${release.tag}${release.prerelease ? ' (pre-release)' : ''}` : 'Site built without a release');
}

async function checkLinks() {
  const links = linksToCheck(readFileSync(join(output, 'index.html'), 'utf8'));
  for (const url of links) {
    const response = await fetch(url, { method: 'HEAD', redirect: 'follow' });
    assert.equal(response.status, 200, `${url} answers HTTP ${response.status}`);
  }
  console.log(`All ${links.length} release links answer 200`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [command = 'build', argument] = process.argv.slice(2);
  try {
    if (command === 'newest-tag') console.log(newestRelease(JSON.parse(readFileSync(argument, 'utf8')))?.tag ?? '');
    else if (command === 'build') build(argument);
    else if (command === 'check-links') await checkLinks();
    else throw new Error('Usage: build-pages.mjs newest-tag <releases.json> | build [release dir] | check-links');
  } catch (error) {
    console.error(`Pages build failed: ${error.message}`);
    process.exitCode = 1;
  }
}
