/* חדשות המועדון, תקצירי יוטיוב ומובילי הקבוצה */
import { getText, getJSON, decode } from '../core/net.js';
import { LS, D } from '../core/env.js';
import { ME, CLUB_NEWS, isClubNews } from './constants.js';
import { DB } from './store.js';
import { norm, tokens } from './names.js';
import { S365, Q365 } from './s365.js';

/* ---------- חדשות המועדון: רק מארכיון הכתבות של האתר הרשמי (https://hapoel.co.il/archive) ----------
   כל כתבה = קישור לכתבה באתר המועדון; מהפריט שלה: הכותרת, התאריך והתמונה הראשית. שום מקור אחר */
const NID = /news\.asp\?NewsId=(\d+)/i, DATE_RE = /(\d{1,2})[\/.](\d{1,2})[\/.](\d{4})/g, TIME_RE = /\b([01]?\d|2[0-3]):[0-5]\d\b/g;
const absUrl = (u, base) => { try { const x = new URL(String(u || '').trim(), base).href; return /^https?:\/\//.test(x) ? x.replace(/^http:/, 'https:') : ''; } catch (e) { return ''; } };
function newsImage(el) {
  for (const im of el.querySelectorAll('img')) {
    const src = im.getAttribute('data-src') || im.getAttribute('data-lazy-src') || im.getAttribute('src') || '';
    if (src && !/^data:|\.svg(\?|$)|logo|icon/i.test(src)) return absUrl(src, CLUB_NEWS);
  }
  for (const x of [el, ...el.querySelectorAll('[style*="url("]')]) {
    const m = /url\((['"]?)([^'")]+)\1\)/.exec(x.getAttribute('style') || '');
    if (m) return absUrl(m[2], CLUB_NEWS);
  }
  return '';
}
export function parseClubNews(htmlText) {
  const doc = new DOMParser().parseFromString(htmlText, 'text/html'), out = [], seen = new Set();
  const idOf = a => { const h = a.getAttribute('href') || ''; const m = NID.exec(h); return m ? m[1] : ''; };
  /* פריטי הארכיון (.archived-news-grid .article); אם המבנה השתנה – כל קישור לכתבה בעמוד */
  const items = [...doc.querySelectorAll('.archived-news-grid .article')];
  for (const a of items.length ? items.map(x => x.querySelector('.article-title a[href]') || x.querySelector('a[href*="news.asp"]')).filter(Boolean) : doc.querySelectorAll('a[href]')) {
    const id = idOf(a); if (!id || seen.has(id)) continue; seen.add(id);
    /* הפריט של הכתבה: העטיפה הגדולה ביותר שכל הקישורים לכתבות בתוכה הם לכתבה הזו */
    let box = a;
    for (let p = a.parentElement; p && p !== doc.body; p = p.parentElement) {
      if ([...p.querySelectorAll('a[href]')].some(x => { const i = idOf(x); return i && i !== id; })) break;
      box = p;
    }
    /* הכותרת: רק הטקסט של קישור הכותרת – בלי התאריך והשעה שהאתר מצמיד אליה (הם מוצגים בנפרד, מתחת לכותרת) */
    const links = [...box.querySelectorAll('a[href]')].filter(x => idOf(x) === id).map(x => x.textContent.replace(/\s+/g, ' ').trim()).filter(Boolean);
    const raw = (items.length ? a.textContent.replace(/\s+/g, ' ').trim() : '') || links.sort((x, y) => y.length - x.length)[0] || '';
    const t = raw.replace(DATE_RE, ' ').replace(TIME_RE, ' ').replace(/\s+/g, ' ').replace(/^[\s|·•,\-–—]+|[\s|·•,\-–—]+$/g, '').trim();
    if (!t) continue;
    /* התאריך והשעה: מהפריט, בלי הכותרת עצמה (בכותרת יכולה להיות תוצאה כמו "85:80") */
    const meta = (box.textContent || '').split(raw).join(' '), m = new RegExp(DATE_RE.source).exec(meta), h = /(?:^|[^\d:])([01]?\d|2[0-3]):([0-5]\d)(?![\d:])/.exec(meta);
    out.push({ t, u: 'https://hapoel.co.il/news.asp?NewsId=' + id, d: m ? m[3] + '-' + m[2].padStart(2, '0') + '-' + m[1].padStart(2, '0') : '', h: h ? h[1].padStart(2, '0') + ':' + h[2] : '', i: newsImage(box) });
  }
  return out.filter(x => isClubNews(x.u));
}
export async function loadNews() {
  const out = parseClubNews(await getText(CLUB_NEWS));
  if (out.length) { DB.NEWS = out.slice(0, 30); return out.length; }
  return 0;
}

/* ---------- תקצירים ---------- */
const YTC = { il: 'UCNWMa_pdt1GIWH_zwbTldEQ', euro: 'UCpFqOv5z4WKps64GlQS4ytw', club: 'UCM2ng9CAAebh7NIK_CmcsxA' };
const HLN = { euro: 'יורוקאפ', league: 'ליגת ווינר', wcup: 'גביע ווינר', cup: 'גביע המדינה', super: '', prep: '' };
let HLC = null; const hlCache = () => HLC || (HLC = LS.get('hpj-hl', {}));
/* קודם התקציר מערוץ הפועל (o:0), אחריו הקישור מהנתונים, ואחריו ערוץ המפעל (o:1) */
export const hlOf = g => { const e = hlCache()[g.id]; if (e && e.o === 0 && e.u) return e.u; if (typeof g.hl === 'string' && g.hl) return g.hl; return (e && e.u) || ''; };
export const ytId = g => { const m = /^https:\/\/(?:www\.)?(?:youtube\.com\/watch\?v=|youtu\.be\/)([\w-]{11})/.exec(hlOf(g)); return m ? m[1] : ''; };
export const hlUrl = g => ytId(g) ? 'https://www.youtube.com/watch?v=' + ytId(g) : 'https://www.youtube.com/results?search_query=' + encodeURIComponent(('תקציר ' + (g.h ? ME + ' ' + g.o : g.o + ' ' + ME) + ' ' + (HLN[g.c] || '')).trim());
async function feed(ch) {
  const x = await getText('https://www.youtube.com/feeds/videos.xml?channel_id=' + ch), out = [];
  for (const m of x.matchAll(/<entry>([\s\S]*?)<\/entry>/g)) {
    const e = m[1], v = /<yt:videoId>([\w-]{11})<\/yt:videoId>/.exec(e), t = /<title>([\s\S]*?)<\/title>/.exec(e), p = /<published>([^<]+)<\/published>/.exec(e);
    if (v && t) out.push({ v: v[1], t: decode(t[1]), p: p ? Date.parse(p[1]) : 0 });
  }
  return out;
}
function find(g, feeds, until) {
  const ok = (v, u) => v.p >= g.t && v.p <= u, tk = tokens(g.o || ''), r = g.r || [];
  const sc = t => { const m = /(\d{2,3})\s*[-–]\s*(\d{2,3})/.exec(t); return m && [+m[1], +m[2]].sort().join() === [r[0], r[1]].sort().join(); };
  /* ערוץ הפועל: "… הפועל ירושלים נגד היריבה 84-73" – לפי התוצאה, עד 10 ימים אחרי המשחק */
  const cl = (feeds.club || []).find(v => ok(v, g.t + 10 * D) && /נגד/.test(v.t) && sc(v.t));
  if (cl) return { u: 'https://www.youtube.com/watch?v=' + cl.v, o: 0 };
  const of = g.c === 'euro' ? (feeds.euro || []).find(v => ok(v, until) && /jerusalem/i.test(v.t) && / - /.test(v.t) && /round|game|playoff|final/i.test(v.t) && !/top \d|mvp/i.test(v.t))
    : ['league', 'wcup', 'cup', 'super'].includes(g.c) ? (feeds.il || []).find(v => { const t = norm(v.t); return ok(v, until) && /תקציר/.test(t) && /ירושלים/.test(t) && (!tk.length || tk.some(w => t.includes(w))); }) : null;
  return of ? { u: 'https://www.youtube.com/watch?v=' + of.v, o: 1 } : null;
}
let scannedAll = 0;
/* משחקים שהסתיימו ואין להם עדיין תקציר מערוץ הפועל: בפעם הראשונה – כל העונה, אחר כך – 10 הימים האחרונים */
export async function hlScan() {
  const now = Date.now(), m = hlCache(), club = f => m[f.id] && m[f.id].o === 0;
  const need = DB.F.filter(f => f.r && f.c !== 'prep' && now > f.t && !club(f) && (!scannedAll || now - f.t < 10 * D)); if (!need.length) return 0;
  const chs = new Set(['club']); need.forEach(f => { if (now - f.t < 7 * D && !(typeof f.hl === 'string' && f.hl) && !m[f.id]) chs.add(f.c === 'euro' ? 'euro' : 'il'); });
  const feeds = {}; await Promise.all([...chs].map(k => feed(YTC[k]).then(x => { feeds[k] = x; }).catch(() => { })));
  if (feeds.club) scannedAll = 1;
  let n = 0;
  for (const f of need) {
    const nx = DB.F.filter(x => x.t > f.t).sort((a, b) => a.t - b.t)[0], e = find(f, feeds, Math.min(f.t + 7 * D, nx ? nx.t : Infinity)), p = m[f.id];
    if (e && (!p || p.u !== e.u)) { m[f.id] = e; n++; }
  }
  if (n) LS.set('hpj-hl', Object.fromEntries(Object.entries(m).slice(-80)));
  return n;
}

/* ---------- מובילי הקבוצה לפי מפעל (365scores) ---------- */
export const LCAT = [[1, 'נקודות', 'נק׳'], [3, 'ריבאונדים', 'ריב׳'], [2, 'אסיסטים', 'אס׳']];
export async function loadLeaders() {
  const u = c => S365 + 'stats/?' + Q365 + '&competitors=634' + (c ? '&competitions=' + c : '');
  const base = await getJSON(u(0)), cs = (base.competitions || []).map(c => [c.id, c.name]);
  /* 365scores מחזיר את הטבלאות בשתי צורות: athletesStats.tables או athletesStats כרשימה ישירה – שתיהן נקראות */
  const tables = j => { const a = j && j.stats && j.stats.athletesStats; return Array.isArray(a) ? a : (a && a.tables) || []; };
  const one = async ([id, name]) => {
    const j = tables(base).some(t => t.competitionId === id) ? base : await getJSON(u(id)), T = tables(j).filter(t => t.competitionId === id), cat = {};
    LCAT.forEach(([tid]) => { const t = T.find(x => x.id === tid); cat[tid] = t ? (t.rows || []).slice(0, 3).map(r => ({ id: r.entity.id, n: String(r.entity.name || '').replace(/'/g, '׳'), iv: r.entity.imageVersion || 1, v: (r.stats && r.stats[0] && r.stats[0].value) || '' })) : []; });
    return LCAT.some(([tid]) => cat[tid].length) ? { id, name, cat } : null;
  };
  const now = Date.now(), got = (await Promise.all(cs.map(c => one(c).catch(() => one(c)).catch(() => null)))).filter(Boolean);
  /* 365scores לא מחזיר תמיד את כל המפעלים (גביע ווינר, גביע המדינה) – מפעל שלא הגיע הפעם נשאר מהשמירה הקודמת,
     כך שהוא לא נעלם וחוזר; מפעל שלא הגיע 120 יום (עונה שהסתיימה) יורד */
  const sv = savedLeaders() || {}, prev = (sv.comps || []).filter(c => now - (c.seen || sv.at || 0) < 120 * D), map = new Map(prev.map(c => [c.id, c]));
  got.forEach(c => map.set(c.id, { ...c, seen: now }));
  const out = [...map.values()].sort((a, b) => lRank(a) - lRank(b));
  if (!out.length) return null;
  const d = { at: now, comps: out }; LS.set('hpj-ldr', d); return d;
}
/* סדר קבוע: ליגה, יורוקאפ, גביע ווינר, גביע המדינה, ואחריהם השאר – כדי שהלשוניות לא יקפצו */
const LORD = [/ליג|league/i, /יורו|euro/i, /ווינר|winner/i, /מדינה|state|israel cup/i];
const lRank = c => { const k = LORD.findIndex(r => r.test(c.name || '')); return k < 0 ? LORD.length : k; };
export const savedLeaders = () => LS.get('hpj-ldr', null);
