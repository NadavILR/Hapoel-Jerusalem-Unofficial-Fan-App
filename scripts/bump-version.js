#!/usr/bin/env node
/*
 * Raises the app version by one step and updates every place that shows it.
 *   Up to 1.1.8 the format was X.Y.Z; from then on it is 1.XX: 1.1.8 -> 1.19 -> 1.20 -> 1.21 -> ...
 * Usage: node scripts/bump-version.js          (next version)
 *        node scripts/bump-version.js 1.0.0    (set an exact version)
 */
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..');
const cur = fs.readFileSync(path.join(root, 'VERSION'), 'utf8').trim();
let next = process.argv[2];
if (!next) {
  const p = cur.split('.').map(Number);
  const minor = p.length === 3 ? p[1] * 10 + p[2] : p[1]; // 1.1.8 counts as 1.18
  next = p[0] + '.' + (minor + 1);
}
if (!/^\d+\.(\d\.\d|\d{2,})$/.test(next)) throw new Error('bad version ' + next);
fs.writeFileSync(path.join(root, 'VERSION'), next + '\n');
fs.writeFileSync(path.join(root, 'www/version.js'), `window.APP_VERSION='${next}';\n`);
const sw = path.join(root, 'www/sw.js');
fs.writeFileSync(sw, fs.readFileSync(sw, 'utf8').replace(/const CACHE = '[^']*';/, `const CACHE = 'hpj-v${next}';`));
console.log(cur + ' -> ' + next);
