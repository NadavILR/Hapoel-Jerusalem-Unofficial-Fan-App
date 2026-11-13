/* 365scores: תוויות מפעל/מחזור, טבלת הליגה, בראקטים, נתוני משחק וסטטיסטיקות */
import { getJSON } from '../core/net.js';
import { H, D, SEASON_Y, LS } from '../core/env.js';
import { BRT, TEAM365 } from './constants.js';
import { DB, setLabels, id365, gsCache, saveGsCache } from './store.js';
import { appName, tblKey, keyScore } from './names.js';

export const S365 = 'https://webws.365scores.com/web/', Q365 = 'appTypeId=5&langId=2&timezoneName=Asia/Jerusalem&userCountryId=6';
export const api = (path, q = '') => getJSON(S365 + path + '?' + Q365 + q);
const teamGames = k => api('games/' + k + '/', '&competitors=' + TEAM365);

/* "ליגת ווינר, מחזור 5" / "ליגת ווינר – חצי גמר, משחק 3" */
const label = g => { const c = String(g.competitionDisplayName || '').trim().replace(/ - /g, ' – '), n = +g.roundNum || 0, rn = String(g.roundName || '').trim() || 'מחזור'; return c ? c + (n > 0 ? ', ' + rn + ' ' + n : '') : ''; };
export async function loadLabels() {
  const a = await Promise.all(['fixtures', 'results'].map(k => teamGames(k).catch(() => null))), out = [];
  a.forEach(j => ((j && j.games) || []).forEach(g => { const t = Date.parse(g.startTime), l = label(g); if (t && l) out.push([t, l]); }));
  if (out.length) setLabels(out);
  return out.length;
}

/* טבלת ליגת ווינר: מוחלת רק כשכל הקבוצות זוהו ומספר המשחקים לא ירד */
export async function loadLeague() {
  if (!DB.LG.length) return 0;
  const d = await api('standings/', '&competitions=47'), rows = ((d.standings || [])[0] || {}).rows || [];
  if (rows.length !== DB.LG.length) return 0;
  const keys = DB.LG.map(r => tblKey(r.n)), used = new Set(), out = [];
  for (const r of rows.slice().sort((a, b) => a.position - b.position)) {
    const k = tblKey(r.competitor && r.competitor.name || ''); let best = -1, bs = 0;
    keys.forEach((q, i) => { if (used.has(i)) return; const sc = keyScore(k, q); if (sc > bs) { bs = sc; best = i; } });
    if (best < 0) return 0; used.add(best);
    out.push({ n: DB.LG[best].n, g: r.gamePlayed, w: r.gamesWon, l: r.gamesLost, p: Math.round(r.points), d: Math.round(r.ratio), s: r.strike || null });
  }
  const sum = a => a.reduce((x, r) => x + (r.g ?? (r.w + r.l)), 0);
  if (sum(out) < sum(DB.LG)) return 0;
  DB.LG = out; return 1;
}

/* הבראקטים של העונה הנוכחית */
const BR365 = { euro: 329, league: 47, cup: 51, wcup: 609 };
export const brData = k => k === 'wcup' ? (DB.WC && DB.WC.KO ? [DB.WC.KO.qf || [], DB.WC.KO.sf || [], DB.WC.KO.f || []] : []) : (DB.BR[k] || []);
export async function loadBrackets() {
  let n = 0;
  await Promise.all(Object.entries(BR365).map(async ([k, comp]) => {
    try {
      const d = await api('brackets/', '&competitions=' + comp), b = [].concat(d.brackets || []).find(x => x && x.isCurrentSeason === true); if (!b) return;
      /* 365scores מציגה את העונה הקודמת כ"נוכחית" עד שהמפעל מתחיל */
      const s0 = Date.UTC(SEASON_Y, 7, 1), ym = /(\d{4})\s*\/\s*\d{2,4}/.exec(b.name || ''), ts = (b.stages || []).flatMap(x => x.groups || []).flatMap(g => g.games || []).map(x => Date.parse(x.startTime)).filter(Boolean);
      if ((ym && +ym[1] !== SEASON_Y) || ts.some(t => t < s0)) return;
      const old = brData(k), cnt = a => a.reduce((x, st) => x + st.filter(m => m && m.a).length, 0);
      const out = BRT[k].r.map(([name, bo], i) => {
        const st = (b.stages || []).find(x => x.name === name && (x.groups || []).some(g => g.participants)); if (!st) return [];
        return st.groups.filter(g => g.participants).sort((x, y) => x.num - y.num).map(g => {
          const ps = g.participants.slice().sort((x, y) => x.num - y.num), a = appName(ps[0] && ps[0].name), bb = appName(ps[1] && ps[1].name), m = { a, b: bb };
          const gs = (g.games || []).slice().sort((x, y) => x.num - y.num), done = gs.filter(x => x.game && x.game.statusGroup === 4), sc = Array.isArray(g.score) ? g.score.map(v => Math.round(v)) : null;
          if (sc && sc.length === 2 && done.length && sc[0] >= 0 && sc[1] >= 0) { m.as = sc[0]; m.bs = sc[1]; }
          const nx = gs.find(x => !(x.game && x.game.statusGroup === 4));
          if (nx && nx.startTime && bo === 1) { const t = Date.parse(nx.startTime); if (t) m.t = t; if (nx.venue && nx.venue.name && /[֐-׿]/.test(nx.venue.name)) m.v = nx.venue.name; }
          const o = (old[i] || []).find(x => x && x.a === a && x.b === bb); if (o && o.m) m.m = o.m;
          return m;
        });
      });
      if (!cnt(out) || cnt(out) < cnt(old)) return;
      if (k === 'wcup') DB.WC = { ...DB.WC, KO: { qf: out[0], sf: out[1], f: out[2] } }; else DB.BR = { ...DB.BR, [k]: out };
      n++;
    } catch (e) { }
  }));
  return n;
}

/* ---------- נתוני משחק ---------- */
const sv = (a, t) => { const x = (a || []).find(y => y.type === t); return x ? x.value : ''; };
const mk2 = v => { const m = /(\d+)\s*\/\s*(\d+)/.exec(v || ''); return m ? [+m[1], +m[2]] : [0, 0]; };
const int = v => parseInt(v, 10) || 0;
const nn = x => x >= 0 ? Math.round(x) : 0;
const QRE = /רבע|הארכה|ר'|OT|Q/;
const isQ = x => x.homeCompetitorScore >= 0 && x.awayCompetitorScore >= 0 && QRE.test(x.name || x.shortName || '');
/* סטטיסטיקות מלאות בפורמט של האפליקציה ([הפועל, יריבה]) */
export function gs365(d, ts) {
  const jh = d.homeCompetitor.id === TEAM365, J = jh ? d.homeCompetitor : d.awayCompetitor, O = jh ? d.awayCompetitor : d.homeCompetitor, mem = {}, nm = x => String(x || '').replace(/'/g, '׳');
  (d.members || []).forEach(m => { mem[m.id] = m; });
  const rows = c => ((c.lineups && c.lineups.members) || []).filter(m => m.status === 1 || m.status === 2).map(m => {
    const x = mem[m.id] || {}, g = t => sv(m.stats, t), fg = mk2(g(5)), p3 = mk2(g(9)), ft = mk2(g(13));
    return [nm(x.name), x.jerseyNumber != null ? x.jerseyNumber : '', m.status === 1 ? 1 : 0, (g(1) || '0:00').replace(/^0(\d)/, '$1'), int(g(2)), int(g(17)), int(g(18)), int(g(19)), int(g(21)), fg[0], fg[1], p3[0], p3[1], ft[0], ft[1], int(g(15)), int(g(16)), int(g(20)), int(g(22)), x.athleteId || 0];
  });
  const pl = [rows(J), rows(O)], S = (i, k) => pl[i].reduce((a, r) => a + r[k], 0);
  const T = (id, cid) => { const x = ((ts && ts.statistics) || []).find(y => y.id === id && y.competitorId === cid); return x ? x.value : null; };
  const pr = (id, k, k2) => [0, 1].map(i => { const v = T(id, i ? O.id : J.id); return v == null ? (k2 != null ? [S(i, k), S(i, k2)] : S(i, k)) : (k2 != null ? mk2(v) : int(v)); });
  const q = (d.stages || []).filter(isQ).map(x => jh ? [nn(x.homeCompetitorScore), nn(x.awayCompetitorScore)] : [nn(x.awayCompetitorScore), nn(x.homeCompetitorScore)]);
  return { q, fg: pr(5, 9, 10), p3: pr(9, 11, 12), ft: pr(13, 13, 14), t: { ast: pr(18, 6), reb: pr(17, 5), or: pr(15, 15), dr: pr(16, 16), stl: pr(19, 7), blk: pr(21, 8), to: pr(20, 17), pf: pr(22, 18) }, pl };
}
/* מצב המשחק (pre / live / end) לפי 365scores */
export function live365(d, f, url) {
  const jh = d.homeCompetitor && d.homeCompetitor.id === TEAM365, hs = nn(d.homeCompetitor.score), as = nn(d.awayCompetitor.score), pt = d.preciseGameTime;
  const clock = d.gameTimeDisplay || (pt && pt.minutes >= 0 && d.statusGroup === 3 ? String(pt.minutes).padStart(2, '0') + ':' + String(pt.seconds || 0).padStart(2, '0') : '');
  return {
    app: f.id, id: d.id, src: '365', jh, hs, as, r: jh ? [hs, as] : [as, hs], status: d.statusGroup === 3 ? 'live' : d.statusGroup === 4 ? 'end' : 'pre', st: d.statusText || d.shortStatusText || '', clock,
    q: (d.stages || []).filter(isQ).map(x => ({ n: x.shortName || x.name, h: nn(x.homeCompetitorScore), a: nn(x.awayCompetitorScore) })),
    top: ((d.topPerformers && d.topPerformers.categories) || []).map(c => ({ n: c.name, h: c.homePlayer && { p: c.homePlayer.name, v: ((c.homePlayer.stats || [])[0] || {}).value }, a: c.awayPlayer && { p: c.awayPlayer.name, v: ((c.awayPlayer.stats || [])[0] || {}).value } })),
    url, at: Date.now(),
  };
}
/* מזהה המשחק ב-365scores: מהנתונים, ואם אין – לפי שעת המשחק */
export async function find365(f, kinds = ['games/current/', 'games/results/']) {
  const id = id365(f); if (id) return id;
  for (const k of kinds) { const l = await api(k, '&competitors=' + TEAM365).catch(() => null), g = ((l && l.games) || []).find(x => Math.abs(Date.parse(x.startTime) - f.t) < 3 * H); if (g) return String(g.id); }
  return null;
}
export const gameDetail = id => api('game/', '&gameId=' + id + '&withExpanded=true').then(j => j.game);
export const gameStats = id => api('game/stats/', '&games=' + id).catch(() => null);
async function gsFetch(f) {
  const id = await find365(f); if (!id) return null;
  const det = await gameDetail(id); if (!det || det.statusGroup !== 4) return null;
  const gs = gs365(det, await gameStats(id)), jh = det.homeCompetitor && det.homeCompetitor.id === TEAM365, hs = nn(det.homeCompetitor.score), as = nn(det.awayCompetitor.score);
  return gs.pl[0].length ? { gs, r: jh ? [hs, as] : [as, hs] } : null;
}
/* משחקים מ-30 הימים האחרונים שהסתיימו בלי סטטיסטיקה – עד 5 בכל בדיקה. מחזיר [מספר שנוספו, תוצאות] */
let busy = false;
export async function gsScan() {
  if (busy) return [0, []]; busy = true;
  try {
    const now = Date.now(), m = gsCache(), res = []; let n = 0;
    const need = DB.F.filter(f => f.c !== 'prep' && !f.tbd && now > f.t + 2.5 * H && now - f.t < 30 * D && !(DB.GS[f.id] && DB.GS[f.id].pl) && !m[f.id]).slice(0, 5);
    for (const f of need) { try { const x = await gsFetch(f); if (x) { m[f.id] = x.gs; n++; if (!f.r) res.push([f.id, x.r]); } } catch (e) { } }
    if (n) saveGsCache();
    return [n, res];
  } finally { busy = false; }
}
/* הסגל של קבוצה (למצב הבדיקה) */
export async function squadOf(f) {
  let c = (/-(\d+)-(\d+)-\d+#id=/.exec(DB.M365[f.id] || '') || []).slice(1).find(x => x !== String(TEAM365));
  if (!c) { const id = id365(f); if (!id) return []; const g = await gameDetail(id).catch(() => ({})) || {}; c = [g.homeCompetitor, g.awayCompetitor].map(x => x && String(x.id)).find(x => x && x !== String(TEAM365)); }
  if (!c) return [];
  const sq = ((await api('squads/', '&competitors=' + c)).squads || [])[0];
  return ((sq && sq.athletes) || []).filter(a => a.name && !/מאמן|coach/i.test((a.position && a.position.name) || '')).map(a => ({ n: String(a.name).replace(/'/g, '׳'), j: a.jerseyNum != null ? a.jerseyNum : '', id: a.id || 0 }));
}
