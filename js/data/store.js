/* מאגר הנתונים של העונה: נטען מ-data.json, מתעדכן מהמקורות החיים, ומודיע למסכים על כל שינוי */
import { LS, H, SEASON } from '../core/env.js';
import { ME, COMPS, CN, OFFICIAL } from './constants.js';
import { CREST_PATHS } from './crest-map.js';

export const DB = {
  ok: false, ASOF: '—', updated: '', F: [], LG: [], PL: [], ST: [], OF: [], TP: '', M365: {}, PBP: {}, GP: {}, GS: {},
  FACTS: {}, LAST: {}, AVG: {}, EC: [], WC: { G: [], KO: null }, BR: { euro: [], league: [], cup: [] }, NEWS: [],
  CR: { ...CREST_PATHS }, labels: LS.get('hpj-365lbl', { l: [] }).l || [],
};
/* מנויים: כל שינוי בנתונים מעלה את המונה, ורכיבי React שמאזינים מתרנדרים מחדש */
const subs = new Set(); let ver = 0;
export const changed = () => { ver++; subs.forEach(f => f(ver)); };
export const subscribe = f => { subs.add(f); return () => subs.delete(f); };
export const version = () => ver;

/* ---------- בדיקת data.json: רק העונה הנוכחית, וכל שדה מהסוג הנכון ---------- */
const A = (x, d) => Array.isArray(x) ? x : d, O = (x, d) => x && typeof x === 'object' && !Array.isArray(x) ? x : d;
const num = x => typeof x === 'number' && isFinite(x), pair = x => Array.isArray(x) && x.length === 2 && num(x[0]) && num(x[1]);
const https = x => typeof x === 'string' && /^https:\/\//.test(x);
export function applyData(j) {
  if (!j || j.season !== SEASON || !Array.isArray(j.F)) return false;
  const w = O(j.WC, {}), br = O(j.BR, {}), BN = {};
  for (const k of ['euro', 'league', 'cup']) BN[k] = A(br[k], []).map(r => A(r, []).map(m => O(m, null)));
  const GN = {};
  for (const [k, v] of Object.entries(O(j.GS, {}))) {
    const e = O(v, {}), r = { q: A(e.q, []).filter(pair) };
    for (const t of ['fg', 'p3', 'ft']) { const a = A(e[t], []); r[t] = a.length === 2 && pair(a[0]) && pair(a[1]) ? a : null; }
    const tt = O(e.t, {}); r.t = {}; for (const x of ['ast', 'reb', 'or', 'dr', 'stl', 'blk', 'to', 'pf']) if (pair(tt[x])) r.t[x] = tt[x];
    const pl = A(e.pl, []);
    r.pl = pl.length === 2 ? pl.map(sd => A(sd, []).filter(w => Array.isArray(w) && (w.length === 19 || w.length === 20) && typeof w[0] === 'string' && w.slice(4, 19).every(num))
      .map(w => [w[0], num(w[1]) ? w[1] : null, w[2] ? 1 : 0, String(w[3]), ...w.slice(4, 19), num(w[19]) ? w[19] : null])) : null;
    if (r.q.length || r.fg || r.p3 || r.ft || Object.keys(r.t).length || (r.pl && (r.pl[0].length || r.pl[1].length))) GN[k] = r;
  }
  const s = x => typeof x === 'string' ? x : num(x) ? String(x) : '';
  Object.assign(DB, {
    ok: true, ASOF: String(j.ASOF || '—'), updated: String(j.updated || ''),
    F: A(j.F, []).filter(f => f && num(f.t) && f.id != null && typeof f.o === 'string'),
    LG: A(j.LG, []).filter(r => r && typeof r.n === 'string'),
    PL: A(j.PL, []).filter(p => Array.isArray(p) && p.length >= 9 && typeof p[1] === 'string')
      .map(p => [num(p[0]) ? p[0] : '', p[1], s(p[2]), s(p[3]), s(p[4]), s(p[5]), https(p[6]) ? p[6] : '', num(p[7]) && p[7] > 0 ? p[7] : 0, typeof p[8] === 'string' && /^[\w-]+$/.test(p[8]) ? p[8] : '', num(p[9]) && p[9] > 0 ? p[9] : 0]),
    ST: A(j.ST, []).filter(x => Array.isArray(x) && typeof x[0] === 'string' && typeof x[1] === 'string'),
    OF: A(j.OF, []).filter(x => Array.isArray(x) && typeof x[0] === 'string' && typeof x[1] === 'string'),
    TP: https(j.TP) ? j.TP : '', M365: O(j.M365, {}), PBP: O(j.PBP, {}), GP: O(j.GP, {}), GS: GN,
    FACTS: O(j.FACTS, {}), LAST: O(j.LAST, {}), AVG: O(j.AVG, {}), EC: A(j.EC, []),
    WC: { G: A(w.G, []), KO: O(w.KO, null) }, BR: BN,
    NEWS: A(j.NEWS, []).map(x => typeof x === 'string' ? { t: x } : O(x, null)).filter(x => x && typeof x.t === 'string' && x.t)
      .map(x => ({ t: x.t, d: typeof x.d === 'string' ? x.d : '', u: typeof x.u === 'string' && /^https?:\/\//.test(x.u) ? x.u : '', i: https(x.i) ? x.i : '' })),
  });
  Object.assign(DB.CR, O(j.CR, {}));
  keepResults();
  return true;
}

/* ---------- תוצאות סיום מהלייב: נשמרות עד שמגיעות גם בקובץ הנתונים ---------- */
export function keepResults() {
  const rs = LS.get('hpj-res', {}); if (!Object.keys(rs).length) return;
  DB.F = DB.F.map(f => !f.r && Array.isArray(rs[f.id]) ? { ...f, r: rs[f.id] } : f);
}
export function markResult(id, r) {
  const rs = LS.get('hpj-res', {}); rs[id] = r; LS.set('hpj-res', Object.fromEntries(Object.entries(rs).slice(-20)));
  let ch = false; DB.F = DB.F.map(f => { if (f.id === id && !f.r) { ch = true; return { ...f, r }; } return f; });
  if (ch) changed();
}

/* ---------- עזרים למשחקים ---------- */
export const byTime = (a, b) => a.t - b.t;
export const upcoming = () => DB.F.filter(f => !f.r).sort(byTime);
export const nextGame = () => upcoming()[0] || null;
export const won = f => f.r && f.r[0] > f.r[1];
export const egCode = f => { const m = /\/U\d{4}\/(\d+)$/.exec(f.ou || ''); return m ? +m[1] : null; };
export const bgId = f => { const m = /GameId=(\d+)/.exec(f.ou || ''); return m ? m[1] : null; };
export const id365 = f => f && (/#id=(\d+)/.exec(DB.M365[f.id] || '') || [])[1];
export const url365 = f => f && DB.M365[f.id] ? 'https://www.365scores.com/he/basketball/match/' + DB.M365[f.id] : 'https://www.365scores.com/basketball/team/hapoel-jerusalem-634';
export const officialUrl = f => f && typeof f.ou === 'string' && /^https:\/\/[^\s"'<>]+$/.test(f.ou) ? f.ou : (f && OFFICIAL[f.c]) || '';
export const homeName = f => f.h ? ME : f.o, awayName = f => f.h ? f.o : ME;
/* המפעל והמחזור/השלב: קודם לפי 365scores, אחרת לפי הנתונים */
export const compLabel = f => {
  const m = f && f.t && DB.labels.find(x => Math.abs(x[0] - f.t) < 3 * H);
  return m ? m[1] : (f.cl ? String(f.cl).replace(/ - /g, ' – ') : (CN[f.c] || COMPS[f.c] || '') + (f.rd && f.c !== 'prep' ? ', ' + f.rd : ''));
};
export const setLabels = l => { DB.labels = l; LS.set('hpj-365lbl', { at: Date.now(), l }); changed(); };

/* ---------- סטטיסטיקות משחק: הקובץ, ואחריו מה שהאפליקציה הורידה בעצמה ---------- */
let GSX = null;
export const gsCache = () => GSX || (GSX = LS.get('hpj-gs365', {}));
export const saveGsCache = () => LS.set('hpj-gs365', Object.fromEntries(Object.entries(gsCache()).slice(-40)));
let LGS = null;
export const liveGsCache = () => LGS || (LGS = LS.get('hpj-livegs', {}));
export const gsOf = id => DB.GS[id] && DB.GS[id].pl ? DB.GS[id] : gsCache()[id] || liveGsCache()[id] || DB.GS[id] || null;

/* ---------- סמלי קבוצות ---------- */
export const CBASE = 'https://imagecache.365scores.com/image/upload/f_png,c_limit,q_auto:eco,dpr_2,d_Competitors:default1.png/';
export const crestSrcs = n => { const v = DB.CR[n]; if (!v) return []; const a = v.startsWith('http') ? v : CBASE + v, o = [a]; if (a.startsWith(CBASE)) o.push(a.replace('f_png,c_limit', 'f_png,w_64,h_64,c_limit')); return o; };
export const crestUrl = n => n === ME ? 'icons/club-logo.png' : crestSrcs(n)[0] || '';
/* שמות שחקני הפועל לפי מספר החולצה (לכרטיס הלייב הנייטיב) */
export const jerseyMap = () => { const o = {}; DB.PL.forEach(r => { if (r && r[0] !== '' && r[1]) o[String(r[0])] = String(r[1]); }); return o; };
/* קבוצות לפי עמדה */
export const posGroups = p => { const t = String(p[2]), r = []; if (/גארד|רכז/.test(t)) r.push('G'); if (/פורוורד/.test(t)) r.push('F'); if (/סנטר/.test(t)) r.push('C'); return r; };
/* גיבוי לסמלים: עותקים מקומיים (crests.json) כשהקישור של 365scores חסום */
let CRP = null;
export const crestBackup = () => CRP || (CRP = fetch('crests.json').then(r => r.ok ? r.json() : {}).catch(() => ({})));
export const crestDataOk = v => typeof v === 'string' && /^data:image\/png;base64,[A-Za-z0-9+\/=]+$/.test(v);
