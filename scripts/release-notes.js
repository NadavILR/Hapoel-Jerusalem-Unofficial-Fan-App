#!/usr/bin/env node
/*
 * Release notes from CHANGELOG.md.
 *   node scripts/release-notes.js 1.31         -> the notes of one version (Markdown), with the install line
 *   node scripts/release-notes.js --list       -> "tag<TAB>version" for every version in the changelog
 * The release tag: the version as it is from 1.132 on (v1.132); before that X.Y.Z (1.31 -> v1.31.0, 1.1.8 -> v1.1.8).
 * See .github/workflows/android.yml.
 */
const fs = require('fs'), path = require('path');
const md = fs.readFileSync(path.join(__dirname, '..', 'CHANGELOG.md'), 'utf8');
const sections = {};
let cur = null;
for (const line of md.split('\n')) {
  const h = /^## (\d+\.\d+(?:\.\d+)?)\s*$/.exec(line);
  if (h) { cur = h[1]; sections[cur] = []; continue; }
  if (/^## /.test(line)) { cur = null; continue; }
  if (cur) sections[cur].push(line);
}
const tagOf = v => {
  const p = v.split('.').map(Number);
  const plain = p.length !== 2 || p[0] > 1 || (p[0] === 1 && p[1] >= 132);
  return 'v' + (plain ? v : v + '.0');
};
const arg = process.argv[2];
if (arg === '--list') {
  for (const v of Object.keys(sections)) console.log(tagOf(v) + '\t' + v);
} else {
  const v = arg || fs.readFileSync(path.join(__dirname, '..', 'VERSION'), 'utf8').trim();
  const body = (sections[v] || []).join('\n').trim();
  if (!body) { console.error('No notes for ' + v + ' in CHANGELOG.md'); process.exit(1); }
  console.log('## מה חדש בגרסה ' + v + '\n\n' + body + '\n\n---\n' +
    'להתקנה: מורידים מהטלפון את `hapoel-jerusalem-v' + v + '.apk` ופותחים אותו (מתקינים מעל הגרסה הקיימת). ' +
    'מי שהאפליקציה כבר מותקנת אצלו יקבל את העדכון גם מתוך האפליקציה.');
}
