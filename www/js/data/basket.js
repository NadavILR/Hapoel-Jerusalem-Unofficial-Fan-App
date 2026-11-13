/* basket.co.il (באנדרואיד בלבד – האתר חוסם גישה מדפדפן): מועדים ותוצאות של הפועל, וטבלת ליגת ווינר */
import { getText, cellsOf, rowsOf } from '../core/net.js';
import { ilToUtc } from '../core/env.js';
import { bName } from './constants.js';
import { DB, bgId } from './store.js';
export async function loadIsrael() {
  const page = await getText('https://basket.co.il/team.asp?TeamId=2114'), by = {};
  for (const r of rowsOf(page)) {
    const id = (/GameId=(\d+)/.exec(r) || [])[1]; if (!id || by[id]) continue;
    const c = cellsOf(r), txt = c.join(' | '), d = /(\d\d)\/(\d\d)\/(\d{4})/.exec(txt); if (!d) continue;
    const tm = /(\d\d):(\d\d)/.exec(txt), sc = /(\d{2,3})-(\d{2,3})\s*$/.exec(c[c.length - 1] || '');
    by[id] = { d: [+d[3], +d[2], +d[1]], tm: tm ? [+tm[1], +tm[2]] : null, sc: sc ? [+sc[1], +sc[2]] : null };
  }
  let n = 0;
  DB.F = DB.F.map(f => {
    const g = by[bgId(f)]; if (!g) return f; const o = { ...f };
    if (g.tm) { o.t = ilToUtc(g.d[0], g.d[1], g.d[2], g.tm[0], g.tm[1]); delete o.tbd; } else { o.t = ilToUtc(g.d[0], g.d[1], g.d[2], 12, 0); o.tbd = 1; }
    /* התוצאה באתר מופיעה כ"אורחת-מארחת" */
    if (g.sc) o.r = f.h ? [g.sc[1], g.sc[0]] : [g.sc[0], g.sc[1]];
    n++; return o;
  });
  const tbl = await getText('https://basket.co.il/table.asp'), rows = [];
  const t = (tbl.match(/<table[^>]*>[\s\S]*?<\/table>/g) || []).find(x => x.includes('שם הקבוצה'));
  if (t) for (const r of rowsOf(t)) {
    const c = cellsOf(r);
    if (/^\d+$/.test(c[0]) && c.length >= 6 && /^\d+$/.test(c[3])) rows.push({ n: bName(c[1]), g: +c[3], w: +c[4], l: +c[5], p: +c[2], d: /^[+-]?\d+$/.test(c[6] || '') ? parseInt(c[6], 10) : null, s: /^[+-]\d+$/.test(c[21] || '') ? parseInt(c[21], 10) : null });
  }
  if (rows.length >= 8) DB.LG = rows;
  return n;
}
