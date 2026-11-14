/* לוח המשחקים לפי האתר הרשמי של הפועל (hapoel.co.il/games.asp): התאריך, השעה והמיקום של כל משחק.
   באנדרואיד בלבד (האתר חוסם גישה מדפדפן). משחקי ליגת ווינר וגביע ווינר מותאמים לפי מספר המשחק (זהה ל-basket.co.il),
   ושאר המשחקים (יורוקאפ, הכנה) לפי התאריך – משחק אחד ביום. "(לא סופי)" בלי שעה – המועד לא סופי */
import { getText } from '../core/net.js';
import { SEASON_Y, ilToUtc, fmt } from '../core/env.js';
import { DB, bgId } from './store.js';
const MONTHS = { 'בינואר': 1, 'בפברואר': 2, 'במרץ': 3, 'באפריל': 4, 'במאי': 5, 'ביוני': 6, 'ביולי': 7, 'באוגוסט': 8, 'בספטמבר': 9, 'באוקטובר': 10, 'בנובמבר': 11, 'בדצמבר': 12 };
const D = 864e5;
export async function loadClub() {
  const doc = new DOMParser().parseFromString(await getText('https://hapoel.co.il/games.asp?cYear=' + (SEASON_Y + 1) + '&cMode=2'), 'text/html'), byId = {}, byDay = {};
  for (const g of doc.querySelectorAll('.games-container .game')) {
    const a = g.querySelector('.date-time a') || g.querySelector('.date-time'), txt = a ? a.textContent : '';
    const m = /(\d{1,2})\s+([^\s,]+)/.exec(txt), mo = m && MONTHS[m[2]]; if (!mo) continue;
    const tm = /(\d{1,2}):(\d\d)/.exec(txt), y = mo >= 8 ? SEASON_Y : SEASON_Y + 1;
    const v = ((g.querySelector('.game-type .text') || {}).textContent || '').replace(/\s+/g, ' ').trim().replace(/"/g, '״');
    const x = { t: tm ? ilToUtc(y, mo, +m[1], +tm[1], +tm[2]) : ilToUtc(y, mo, +m[1], 12, 0), tbd: !tm, v };
    const id = (/GameId=(\d+)/.exec(a && a.getAttribute ? a.getAttribute('href') || '' : '') || [])[1];
    if (id) byId[id] = x;
    byDay[(+m[1]) + '.' + mo] = x;
  }
  let n = 0;
  DB.F = DB.F.map(f => {
    const d = fmt.dm(f.t).split('.'), id = bgId(f), x = (id && byId[id]) || (!id && byDay[(+d[0]) + '.' + (+d[1])]);
    if (!x || Math.abs(x.t - f.t) > 90 * D) return f;
    const o = { ...f };
    /* תוצאה כבר ידועה: לא מזיזים את המועד של משחק שהסתיים */
    if (!f.r) { if (!x.tbd) { o.t = x.t; delete o.tbd; } else if (fmt.dm(x.t) !== fmt.dm(f.t) || f.tbd) { o.t = x.t; o.tbd = 1; } }
    if (x.v) o.v = x.v;
    if (o.t === f.t && o.v === f.v && !!o.tbd === !!f.tbd) return f;
    n++; return o;
  });
  return n;
}
