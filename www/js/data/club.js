/* המיקום של כל משחק לפי לוח המשחקים באתר הרשמי של הפועל (hapoel.co.il/games.asp): השורה שמעל "משחק בית / חוץ".
   באנדרואיד בלבד (האתר חוסם גישה מדפדפן). ההתאמה לפי תאריך המשחק בשעון ישראל – משחק אחד ביום */
import { getText } from '../core/net.js';
import { SEASON_Y, fmt } from '../core/env.js';
import { DB } from './store.js';
const MONTHS = { 'בינואר': 1, 'בפברואר': 2, 'במרץ': 3, 'באפריל': 4, 'במאי': 5, 'ביוני': 6, 'ביולי': 7, 'באוגוסט': 8, 'בספטמבר': 9, 'באוקטובר': 10, 'בנובמבר': 11, 'בדצמבר': 12 };
export async function loadVenues() {
  const doc = new DOMParser().parseFromString(await getText('https://hapoel.co.il/games.asp?cYear=' + (SEASON_Y + 1) + '&cMode=2'), 'text/html'), by = {};
  for (const g of doc.querySelectorAll('.games-container .game')) {
    const dt = g.querySelector('.date-time'), m = /(\d{1,2})\s+([^\s,]+)/.exec(dt ? dt.textContent : ''); if (!m || !MONTHS[m[2]]) continue;
    const v = ((g.querySelector('.game-type .text') || {}).textContent || '').replace(/\s+/g, ' ').trim().replace(/"/g, '״');
    if (v) by[+m[1] + '.' + MONTHS[m[2]]] = v;
  }
  let n = 0;
  DB.F = DB.F.map(f => { const d = fmt.dm(f.t).split('.'), v = by[(+d[0]) + '.' + (+d[1])]; if (!v || v === f.v) return f; n++; return { ...f, v }; });
  return n;
}
