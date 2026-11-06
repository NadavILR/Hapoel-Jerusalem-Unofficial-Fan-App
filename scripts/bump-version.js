#!/usr/bin/env node
/*
 * Raises the app version by one step and updates every place that shows it.
 *   1.0.0 -> 1.0.1 -> ... -> 1.0.9 -> 1.1.0 -> ... -> 1.9.9 -> 2.0.0
 * Usage: node scripts/bump-version.js          (next version)
 *        node scripts/bump-version.js 1.0.0    (set an exact version)
 */
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..');
const cur = fs.readFileSync(path.join(root, 'VERSION'), 'utf8').trim();
let next = process.argv[2];
if (!next) {
  let [a, b, c] = cur.split('.').map(Number);
  if (++c > 9) { c = 0; if (++b > 9) { b = 0; a++; } }
  next = [a, b, c].join('.');
}
if (!/^\d+\.\d\.\d$/.test(next)) throw new Error('bad version ' + next);
fs.writeFileSync(path.join(root, 'VERSION'), next + '\n');
fs.writeFileSync(path.join(root, 'www/version.js'), `window.APP_VERSION='${next}';\n`);
const sw = path.join(root, 'www/sw.js');
fs.writeFileSync(sw, fs.readFileSync(sw, 'utf8').replace(/const CACHE = '[^']*';/, `const CACHE = 'hpj-v${next}';`));
console.log(cur + ' -> ' + next);
