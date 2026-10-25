// One-time mechanical Kotlin literal migration. Review the catalog before --apply.
import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
const roots = ['app/src/main', 'core', 'feature'];
const files = [];
function visit(dir) {
  for (const item of readdirSync(dir)) {
    if (['build', 'test', 'androidTest', 'schemas'].includes(item)) continue;
    const file = path.join(dir, item);
    if (statSync(file).isDirectory()) visit(file);
    else if (file.endsWith('.kt') && file.includes('/src/main/')) files.push(file);
  }
}
roots.forEach(visit);
const entries = [], byValue = new Map(), counts = new Map();
const cyrillic = /[А-Яа-яЁё]/;
const patches = [];
function skipComment(s, i) {
  if (s.startsWith('//', i)) { const n = s.indexOf('\n', i); return n < 0 ? s.length : n; }
  if (!s.startsWith('/*', i)) return i;
  let depth = 1; i += 2;
  while (i < s.length && depth) { if (s.startsWith('/*', i)) { depth++; i += 2; } else if (s.startsWith('*/', i)) { depth--; i += 2; } else i++; }
  return i;
}
function charEnd(s, i) { i++; while (i < s.length) { if (s[i] === '\\') i += 2; else if (s[i++] === "'") break; } return i; }
function interpolationEnd(s, start) {
  let depth = 1, i = start;
  while (i < s.length) {
    const next = skipComment(s, i); if (next !== i) { i = next; continue; }
    if (s[i] === '"') { i = quoted(s, i).end; continue; }
    if (s[i] === "'") { i = charEnd(s, i); continue; }
    if (s[i] === '{') depth++;
    else if (s[i] === '}' && --depth === 0) return i;
    i++;
  }
  throw Error('Unclosed interpolation');
}
function quoted(s, start) {
  const triple = s.startsWith('"""', start), delimiter = triple ? '"""' : '"';
  let i = start + delimiter.length, literal = '', parts = [], args = [];
  while (i < s.length) {
    if (s.startsWith(delimiter, i)) { parts.push(literal); return { end: i + delimiter.length, parts, args, triple }; }
    if (!triple && s[i] === '\\') { literal += s.slice(i, i + 2); i += 2; continue; }
    if (s[i] === '$' && s[i + 1] === '{') {
      const end = interpolationEnd(s, i + 2); parts.push(literal); literal = ''; args.push(s.slice(i + 2, end)); i = end + 1; continue;
    }
    if (s[i] === '$' && /[A-Za-z_]/.test(s[i + 1] || '')) {
      const id = s.slice(i + 1).match(/^[A-Za-z_][A-Za-z_0-9]*/)[0]; parts.push(literal); literal = ''; args.push(id); i += id.length + 1; continue;
    }
    literal += s[i++];
  }
  throw Error('Unclosed string');
}
function decode(raw, triple) {
  return triple ? raw : JSON.parse('"' + raw.replace(/\\\$/g, '$').replace(/\\'/g, "'") + '"');
}
function transform(source, stem) {
  let out = '', i = 0;
  while (i < source.length) {
    const comment = skipComment(source, i); if (comment !== i) { out += source.slice(i, comment); i = comment; continue; }
    if (source[i] === "'") { const end = charEnd(source, i); out += source.slice(i, end); i = end; continue; }
    if (source[i] !== '"') { out += source[i++]; continue; }
    const string = quoted(source, i);
    const args = string.args.map(arg => transform(arg, stem));
    if (string.parts.some(part => cyrillic.test(part))) {
      let value = '';
      string.parts.forEach((part, n) => { value += decode(part, string.triple).replaceAll('%', '%%'); if (n < args.length) value += '%' + (n + 1) + '$s'; });
      let entry = byValue.get(value);
      if (!entry) {
        const index = (counts.get(stem) || 0) + 1; counts.set(stem, index);
        entry = { key: stem + '_' + String(index).padStart(3, '0'), ru: value, arguments: args.length };
        entries.push(entry); byValue.set(value, entry);
      }
      out += 'tr(UiText.' + entry.key + (args.length ? ', ' + args.join(', ') : '') + ')';
    } else if (args.every((arg, n) => arg === string.args[n])) {
      out += source.slice(i, string.end);
    } else {
      // Non-translated surrounding text may still interpolate a translated label.
      const delimiter = string.triple ? '"""' : '"';
      out += delimiter;
      string.parts.forEach((part, n) => { out += part; if (n < args.length) out += '${' + args[n] + '}'; });
      out += delimiter;
    }
    i = string.end;
  }
  return out;
}
function dynamicEnumLabels(source) {
  return source.replace(/enum class (\w+)\s*\(([^]*?)\)\s*\{/g, (header, name, params, offset) => {
    // Only the header is replaced here; entry transformations follow below.
    return header;
  });
}
function enumLabels(source) {
  const matches = [...source.matchAll(/enum class \w+\s*\(([^]*?)\)\s*\{/g)].filter(m => /val label:\s*String/.test(m[1]));
  for (const match of matches.reverse()) {
    const labelIndex = match[1].split(',').findIndex(p => /val label:\s*String/.test(p));
    const start = match.index + match[0].length;
    let end = start, depth = 0;
    while (end < source.length) {
      const comment = skipComment(source, end); if (comment !== end) { end = comment; continue; }
      if (source[end] === '"') { end = quoted(source, end).end; continue; }
      if (source[end] === '(') depth++;
      if (source[end] === ')') depth--;
      if (depth === 0 && (source[end] === ';' || source[end] === '}')) break;
      end++;
    }
    let body = source.slice(start, end), replacements = [];
    for (const entry of body.matchAll(/\b\w+\s*\(/g)) {
      const argStart = entry.index + entry[0].length;
      let i = argStart, nest = 1, segment = argStart, args = [];
      while (i < body.length && nest) {
        if (body[i] === '"') { i = quoted(body, i).end; continue; }
        if (body[i] === '(' || body[i] === '{' || body[i] === '[') nest++;
        else if (body[i] === ')' || body[i] === '}' || body[i] === ']') nest--;
        if (nest === 1 && body[i] === ',') { args.push([segment, i]); segment = i + 1; }
        if (nest === 0) args.push([segment, i]);
        i++;
      }
      // Ignore nested calls (tr, toInt, etc.); entry labels have a complete argument list.
      if (args.length > labelIndex && body.slice(args[labelIndex][0], args[labelIndex][1]).trim().match(/^(tr\(UiText\.|\")/)) {
        const [a, b] = args[labelIndex]; replacements.push([a, b]);
      }
    }
    // Nested tr calls do not satisfy the label position; deduplicate defensively.
    for (const [a, b] of [...new Map(replacements.map(r => [r[0], r])).values()].sort((a,b)=>b[0]-a[0])) body = body.slice(0,a)+' { '+body.slice(a,b).trim()+' }'+body.slice(b);
    const head = match[0].replace(/val label:\s*String/, 'private val labelText: () -> String');
    const rest = source[end] === ';' ? source.slice(end + 1) : source.slice(end);
    source = source.slice(0, match.index) + head + body + ';\n    val label: String get() = labelText()\n' + rest;
  }
  return source;
}
for (const file of files.sort()) {
  const source = readFileSync(file, 'utf8');
  let next = enumLabels(transform(source, path.basename(file, '.kt').replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase()));
  if (next !== source && next.includes('tr(UiText.')) next = next.replace(/^(package [^\n]+\n)/, '$1\nimport io.github.ricox.boostyvp.localization.tr\nimport io.github.ricox.boostyvp.localization.UiText\n');
  if (next !== source) patches.push({ file, source, next });
}
if (process.argv.includes('--catalog')) console.log(JSON.stringify(entries));
else if (process.argv.includes('--count')) console.log(patches.length);
else if (process.argv.includes('--apply')) {
  for (const {file, next} of patches) writeFileSync(file, next);
  console.log(JSON.stringify({files: patches.length, keys: entries.length}));
}
else {
  console.log('*** Begin Patch');
  const start = Number(process.argv[process.argv.indexOf('--offset') + 1]) || 0;
  for (const { file, source, next } of patches.slice(start, start + 5)) {
    console.log('*** Update File: ' + path.resolve(file) + '\n@@');
    source.trimEnd().split('\n').forEach(line => console.log('-' + line));
    next.trimEnd().split('\n').forEach(line => console.log('+' + line));
  }
  console.log('*** End Patch');
}
