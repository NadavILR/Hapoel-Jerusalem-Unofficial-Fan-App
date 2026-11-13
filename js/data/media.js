/* חדשות המועדון, תקצירי יוטיוב ומובילי הקבוצה */
import { getText, getJSON, decode } from '../core/net.js';
import { LS, D } from '../core/env.js';
import { ME } from './constants.js';
import { DB } from './store.js';
import { norm, tokens } from './names.js';
import { S365, Q365 } from './s365.js';

/* ---------- חדשות: ארכיון האתר הרשמי ---------- */
export const CLUB = 'https://hapoel.co.il/';
export async function loadNews() {
  const doc = new DOMParser().parseFromString(await getText(CLUB + 'archive'), 'text/html'), out = [], seen = new Set();
  for (const a of doc.querySelectorAll('.archived-news-grid .article')) {
    const l = a.querySelector('.article-title a') || a.querySelector('a[href*="news.asp"]'), img = a.querySelector('img.news_img'), dt = a.querySelector('.inner-content');
    const href = l && l.getAttribute('href'); if (!href || seen.has(href)) continue; seen.add(href);
    const m = /(\d\d)\/(\d\d)\/(\d{4})/.exec(dt ? dt.textContent : '');
    out.push({ t: l.textContent.trim(), u: new URL(href, CLUB).href, d: m ? m[3] + '-' + m[2] + '-' + m[1] : '', i: img ? img.getAttribute('src') || '' : '' });
  }
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
  const tables = j => (j.stats && j.stats.athletesStats && j.stats.athletesStats.tables) || [];
  const one = async ([id, name]) => {
    const j = tables(base).some(t => t.competitionId === id) ? base : await getJSON(u(id)), T = tables(j).filter(t => t.competitionId === id), cat = {};
    LCAT.forEach(([tid]) => { const t = T.find(x => x.id === tid); cat[tid] = t ? (t.rows || []).slice(0, 3).map(r => ({ id: r.entity.id, n: String(r.entity.name || '').replace(/'/g, '׳'), iv: r.entity.imageVersion || 1, v: (r.stats && r.stats[0] && r.stats[0].value) || '' })) : []; });
    return LCAT.some(([tid]) => cat[tid].length) ? { id, name, cat } : null;
  };
  const out = (await Promise.all(cs.map(c => one(c).catch(() => null)))).filter(Boolean);
  if (!out.length) return null;
  const d = { at: Date.now(), comps: out }; LS.set('hpj-ldr', d); return d;
}
export const savedLeaders = () => LS.get('hpj-ldr', null);
