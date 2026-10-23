// SPDX-License-Identifier: MIT
import assert from 'node:assert/strict';
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { root, version } from './release-tools.mjs';
const name = version().name;
const repo = 'https://github.com/rico-x/BoostyVP';
const output = join(root, 'build/pages');
mkdirSync(output, { recursive: true });
let html = readFileSync(join(root, 'site/index.html'), 'utf8')
  .replaceAll('{{VERSION}}', name).replaceAll('{{REPO}}', repo);
assert.ok(!html.includes('{{'), 'Unresolved site template');
assert.ok(!/<script\b|<iframe\b|@import/i.test(html), 'Site must be static and self-contained');
for (const [, href] of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
  assert.ok(href.startsWith('https://github.com/') || href.startsWith('#') || ['icon.svg', 'styles.css'].includes(href), 'Unexpected external resource');
}
assert.equal([...html.matchAll(/releases\/download\/[^"]+\.apk/g)].length, 6);
writeFileSync(join(output, 'index.html'), html);
for (const file of ['styles.css', 'icon.svg']) copyFileSync(join(root, 'site', file), join(output, file));
writeFileSync(join(output, '.nojekyll'), '');
writeFileSync(join(output, 'version.json'), JSON.stringify({ version: name, repository: repo }) + '\n');
console.log('Static Pages site validated for ' + name);
