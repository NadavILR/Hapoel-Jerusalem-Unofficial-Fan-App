/* משחק בלייב: ליגת ווינר וגביע ווינר – Live Stats של איגוד הכדורסל (באנדרואיד); יורוקאפ – ה-API הרשמי של היורוליג;
   כל השאר (וגיבוי) – 365scores. וגם: מהלך המשחק של משחקים שהסתיימו, ומצב בדיקה */
import { getJSON } from '../core/net.js';
import { NATIVE, LS, H, D, ilToUtc } from '../core/env.js';
import { ME, TEAM365 } from './constants.js';
import { DB, egCode, url365, id365 } from './store.js';
import { EL_SEASON } from './euro.js';
import { api, live365, gameDetail, gameStats, gs365 } from './s365.js';

export const PBQ = q => q <= 4 ? 'Q' + q : 'OT' + (q - 4);
const titleCase = x => String(x || '').toLowerCase().replace(/(^|[\s'-])\S/g, c => c.toUpperCase());
const flipName = x => titleCase(String(x || '').split(',').reverse().join(' ').trim());
const jerName = num => { const p = DB.PL.find(r => String(r[0]) === String(num).trim()); return p ? p[1] : ''; };
const n0 = v => parseInt(v, 10) || 0, nv = v => +v || 0;

/* ================= יורוקאפ ================= */
const ELAPI = 'https://live.euroleague.net/api/';
const EUA = { '2FGM': ['קליעה ל-2', 1], '2FGA': ['החטאה ל-2'], '3FGM': ['שלשה', 1], '3FGA': ['החטאה לשלוש'], FTM: ['קליעה מהעונשין', 1], FTA: ['החטאה מהעונשין'], D: ['ריבאונד הגנה'], O: ['ריבאונד התקפה'], AS: ['אסיסט'], ST: ['חטיפה'], TO: ['איבוד'], FV: ['חסימה'], CM: ['עבירה'], OF: ['עבירת התקפה'], C: ['עבירה טכנית למאמן'], CMT1: ['עבירה טכנית'], CMU_FL: ['עבירה בוטה'], TOUT: ['פסק זמן'], TOUT_TV: ['פסק זמן טלוויזיוני'], CCH: ['אתגר מאמן'] };
async function pbpEuro(code, f) {
  const d = await getJSON(ELAPI + 'PlayByPlay?gamecode=' + code + '&seasoncode=' + EL_SEASON), jA = String(d.CodeTeamA || '').trim() === 'JER', out = [];
  ['FirstQuarter', 'SecondQuarter', 'ThirdQuarter', 'ForthQuarter', 'ExtraTime'].forEach((k, qi) => (d[k] || []).forEach(a => {
    const t = String(a.PLAYTYPE || '').trim(), q = qi + 1; let text = '', big = 0, side = null;
    if (t === 'BP') text = 'תחילת ' + PBQ(q); else if (t === 'EP') text = 'סיום ' + PBQ(q); else if (t === 'EG') text = 'סיום המשחק';
    else if (EUA[t]) {
      const team = String(a.CODETEAM || '').trim(), isJ = team === 'JER'; side = team ? (isJ ? 'j' : 'o') : null;
      const who = a.PLAYER ? ((isJ && jerName(a.DORSAL)) || flipName(a.PLAYER)) : (isJ ? ME : (f && f.o) || a.TEAM || '');
      text = (who ? who + ' – ' : '') + EUA[t][0]; big = EUA[t][1] || 0;
    }
    if (!text) return;
    const sc = a.POINTS_A != null && a.POINTS_B != null ? (jA ? [a.POINTS_A, a.POINTS_B] : [a.POINTS_B, a.POINTS_A]) : null;
    out.push({ q, clock: a.MARKERTIME || '', side, text, big, sc });
  }));
  return out.reverse();
}
const elMin = m => { const x = /^(\d+):(\d\d)/.exec(String(m || '')); return x ? (+x[1]) + ':' + x[2] : '0:00'; };
function gsEl(bx, jh, q) {
  const S = bx && bx.Stats; if (!Array.isArray(S) || S.length < 2) return null; const J = S[jh ? 0 : 1], O = S[jh ? 1 : 0];
  const rows = (t, isJ) => ((t && t.PlayersStats) || []).map(p => {
    const d = String(p.Dorsal || '').trim(), pl = isJ ? DB.PL.find(r => String(r[0]) === d) : null;
    return [isJ && pl ? pl[1] : flipName(p.Player), d === '' ? '' : (isNaN(+d) ? d : +d), p.IsStarter ? 1 : 0, elMin(p.Minutes), nv(p.Points), nv(p.TotalRebounds), nv(p.Assistances), nv(p.Steals), nv(p.BlocksFavour),
      nv(p.FieldGoalsMade2) + nv(p.FieldGoalsMade3), nv(p.FieldGoalsAttempted2) + nv(p.FieldGoalsAttempted3), nv(p.FieldGoalsMade3), nv(p.FieldGoalsAttempted3), nv(p.FreeThrowsMade), nv(p.FreeThrowsAttempted), nv(p.OffensiveRebounds), nv(p.DefensiveRebounds), nv(p.Turnovers), nv(p.FoulsCommited), isJ && pl ? pl[7] || 0 : 0];
  }).sort((a, b) => (b[2] - a[2]) || ((a[3] === '0:00') - (b[3] === '0:00')) || ((+a[1] || 0) - (+b[1] || 0)));
  const T = t => (t && t.totr) || {}, a = T(J), b = T(O), two = k => [nv(a[k]), nv(b[k])], sh = (m, at) => [[m(a), at(a)], [m(b), at(b)]];
  return {
    q, fg: sh(x => nv(x.FieldGoalsMade2) + nv(x.FieldGoalsMade3), x => nv(x.FieldGoalsAttempted2) + nv(x.FieldGoalsAttempted3)), p3: sh(x => nv(x.FieldGoalsMade3), x => nv(x.FieldGoalsAttempted3)), ft: sh(x => nv(x.FreeThrowsMade), x => nv(x.FreeThrowsAttempted)),
    t: { ast: two('Assistances'), reb: two('TotalRebounds'), or: two('OffensiveRebounds'), dr: two('DefensiveRebounds'), stl: two('Steals'), blk: two('BlocksFavour'), to: two('Turnovers'), pf: two('FoulsCommited') }, pl: [rows(J, 1), rows(O, 0)], live: 1,
  };
}
/* מובילי המשחק מתוך טבלת השחקנים */
export const topOf = (gs, jh) => {
  if (!gs || !gs.pl) return [];
  const best = (rows, k) => { let b = null; rows.forEach(r => { if (r[k] > 0 && (!b || r[k] > b.v)) b = { p: r[0], v: r[k] }; }); return b; }, J = gs.pl[0], O = gs.pl[1];
  return [['נקודות', 4], ['ריבאונדים', 5], ['אסיסטים', 6]].map(([n, k]) => ({ n, h: best(jh ? J : O, k), a: best(jh ? O : J, k) })).filter(c => c.h || c.a);
};
let elFull = 0;
async function elLive(f, prev) {
  const c = egCode(f); if (!c) return null;
  const qs = '?gamecode=' + c + '&seasoncode=' + EL_SEASON, h = await getJSON(ELAPI + 'Header' + qs); if (!h || !h.CodeTeamA) return null;
  const jh = String(h.CodeTeamA).trim() === 'JER', hs = n0(h.ScoreA), as = n0(h.ScoreB), live = !!h.Live, rem = String(h.RemainingPartialTime || '').trim();
  const gt = String(h.GameTime || '').split(':'), played = n0(gt[0]) + n0(gt[1]) / 60;
  /* בהפסקות השדה Quarter ריק – אז לפי הזמן שעבר (10 דקות לרבע, 5 להארכה) */
  const qn = n0(h.Quarter) || (played <= 0 ? 1 : played <= 40 ? Math.min(4, Math.ceil(played / 10 - 1e-9)) : 4 + Math.ceil((played - 40) / 5 - 1e-9));
  const L = { app: f.id, id: 0, jh, hs, as, r: jh ? [hs, as] : [as, hs], status: 'pre', st: '', clock: '', q: [], top: [], url: url365(f), at: Date.now(), src: 'el' };
  if (!live && hs + as === 0) return L;
  const end = !live && played >= 40 && hs !== as, cum = [1, 2, 3, 4].map(i => [n0(h['ScoreQuarter' + i + 'A']), n0(h['ScoreQuarter' + i + 'B'])]), nq = end ? 4 : Math.min(4, Math.max(1, qn || 1)), per = [];
  for (let i = 0; i < nq; i++) { const cur = i === nq - 1 && !end && qn <= 4 ? [hs, as] : cum[i], pv = i ? cum[i - 1] : [0, 0]; per.push([Math.max(0, cur[0] - pv[0]), Math.max(0, cur[1] - pv[1])]); }
  if (qn > 4 || n0(h.ScoreExtraTimeA) + n0(h.ScoreExtraTimeB) > 0 || (end && (hs !== cum[3][0] || as !== cum[3][1]))) per.push([Math.max(0, hs - cum[3][0]), Math.max(0, as - cum[3][1])]);
  L.q = per.map((x, i) => ({ n: i < 4 ? 'Q' + (i + 1) : 'OT' + (i - 3), h: x[0], a: x[1] }));
  if (end) { L.status = 'end'; L.st = 'סיום'; }
  else { L.status = 'live'; const k = Math.max(1, qn); if (!rem || rem === '00:00') L.st = k === 2 ? 'מחצית' : 'סיום ' + PBQ(k); else { L.st = k <= 4 ? 'רבע ' + k : 'הארכה' + (k > 5 ? ' ' + (k - 4) : ''); L.clock = rem; } }
  const same = prev && prev.app === f.id && prev.src === 'el', moved = !same || prev.r[0] !== L.r[0] || prev.r[1] !== L.r[1], qJ = per.map(x => jh ? x : [x[1], x[0]]);
  if (moved || end || Date.now() - elFull >= 1e4 || !prev.gs) {
    elFull = Date.now(); const [bx, pb] = await Promise.all([getJSON(ELAPI + 'Boxscore' + qs).catch(() => null), pbpEuro(c, f).catch(() => null)]);
    let gs = null; try { gs = gsEl(bx, jh, qJ); } catch (e) { }
    L.gs = gs || (same ? prev.gs : null); L.top = L.gs ? topOf(L.gs, jh) : []; L.pbp = pb && pb.length ? pb : (same ? prev.pbp : null);
  } else { L.gs = prev.gs ? { ...prev.gs, q: qJ } : null; L.top = prev.top || []; L.pbp = prev.pbp; }
  return L;
}

/* ================= ליגת ווינר / גביע ווינר: basket.co.il/livestats ================= */
const LSB = 'https://basket.co.il/livestats/data/';
const bucket = () => Math.floor(Date.now() / 1e4);
const isJ = t => /ירושלים/.test((t && t.nameLocal) || '') || /JERUSALEM/i.test((t && t.name) || '');
const lsTime = x => { const m = /(\d{4})-(\d\d)-(\d\d)T(\d\d):(\d\d)/.exec((x && x.time) || ''); return m ? ilToUtc(+m[1], +m[2], +m[3], +m[4], +m[5]) : NaN; };
const LSID = {}; let LSK = null; const lsk = () => LSK || (LSK = LS.get('hpj-lsid', {}));
const keep = (f, id) => { LSID[f.id] = id; const k = lsk(); if (k[f.id] !== id) { k[f.id] = id; LS.set('hpj-lsid', k); } return id; };
let MISS = null; const miss = () => MISS || (MISS = LS.get('hpj-lsmiss', {}));
/* משחק שכבר ירד מהרשימה: לפי קובצי המשחקים (המספרים עולים עם הזמן) – עד 24 מספרים אחרי המשחק הקודם באותו מפעל */
async function lsScan(f) {
  const ms = miss()[f.id]; if (ms && Date.now() - ms < (Date.now() - f.t < 3 * D ? 6e5 : D)) return null;
  const kn = DB.F.filter(x => x.t < f.t && (DB.PBP[x.id] || lsk()[x.id])).sort((x, y) => y.t - x.t), pv = kn.find(x => x.c === f.c) || kn[0], from = (pv ? +(DB.PBP[pv.id] || lsk()[pv.id]) : 390) + 1;
  for (let a = from; a < from + 24; a += 6) {
    const got = await Promise.all([0, 1, 2, 3, 4, 5].map(i => getJSON(LSB + 'game-' + (a + i) + '.json').then(d => ({ id: a + i, d })).catch(() => null)));
    const hit = got.find(x => x && x.d && x.d.gameInfo && (isJ(x.d.gameInfo.homeTeam) || isJ(x.d.gameInfo.awayTeam)) && Math.abs(lsTime(x.d.gameInfo) - f.t) < 48 * H);
    if (hit) return hit.id;
  }
  miss()[f.id] = Date.now(); LS.set('hpj-lsmiss', miss()); return null;
}
async function lsId(f) {
  if (LSID[f.id]) return LSID[f.id]; if (lsk()[f.id]) return LSID[f.id] = lsk()[f.id];
  const list = await getJSON(LSB + 'games.json?w=' + bucket()), mine = (list.games || []).filter(x => isJ(x.homeTeam) || isJ(x.awayTeam));
  const gm = mine.find(x => Math.abs(lsTime(x) - f.t) < 4 * H) || mine.find(x => !x.gameFinished && (+x.currentQuarter || 0) >= 1 && Math.abs(lsTime(x) - f.t) < 48 * H);
  if (gm) return keep(f, gm.id);
  if (f.r || Date.now() > f.t + 4 * H) { const id = await lsScan(f).catch(() => null); if (id) return keep(f, id); }
  return null;
}
async function lsGame(f) { const id = await lsId(f); return id ? getJSON(LSB + 'game-' + id + '.json?w=' + bucket()) : null; }
function applyLs(L, d) {
  const gi = d.gameInfo || {}, bg = (d.boxscore && d.boxscore.gameInfo) || {}, acts = (d.actions && d.actions.actions) || [], same = isJ(gi.homeTeam) === !!L.jh;
  const lh = +bg.homeScore || 0, la = +bg.awayScore || 0; L.hs = same ? lh : la; L.as = same ? la : lh; L.r = L.jh ? [L.hs, L.as] : [L.as, L.hs];
  const real = acts.filter(a => a.type !== 'clock'), last = real[real.length - 1], brk = !!(last && last.type === 'quarter' && last.parameters && last.parameters.type === 'end-of-quarter'), q = +gi.currentQuarter || 1;
  const hq = bg.homeQuarterScores || [], aq = bg.awayQuarterScores || [];
  L.q = hq.map((h, i) => ({ n: i < 4 ? 'Q' + (i + 1) : 'OT' + (i - 3), h: same ? +h || 0 : +aq[i] || 0, a: same ? +aq[i] || 0 : +h || 0 })).filter((x, i) => i < Math.max(q, 4) && (i < q || x.h || x.a));
  if (gi.gameFinished) { L.status = 'end'; L.st = 'סיום'; L.clock = ''; return L; }
  if (!real.length) { L.status = 'pre'; return L; }
  L.status = 'live';
  if (brk) { L.st = q === 2 ? 'מחצית' : 'סיום ' + PBQ(q); L.clock = ''; }
  else {
    L.st = q <= 4 ? 'רבע ' + q : 'הארכה' + (q > 5 ? ' ' + (q - 4) : '');
    const la2 = acts[acts.length - 1], t = gi.currentQuarterTime || {};
    L.clock = la2 && (+la2.quarter || q) === q && la2.quarterTime ? la2.quarterTime : String(t.m || 0).padStart(2, '0') + ':' + String(t.s || 0).padStart(2, '0');
  }
  return L;
}
const lsNames = t => { const m = {}; ((t && t.players) || []).forEach(p => { const v = ((p.firstNameLocal || '') + ' ' + (p.lastNameLocal || '')).trim() || titleCase((p.firstName || '') + ' ' + (p.lastName || '')); m[String(p.id)] = v; m['#' + p.jerseyNumber] = v; }); return m; };
export function gsLs(d) {
  const gi = d.gameInfo || {}, bx = d.boxscore || {}, bg = bx.gameInfo || {}, jh = isJ(gi.homeTeam), J = jh ? bx.homeTeam : bx.awayTeam, O = jh ? bx.awayTeam : bx.homeTeam, JI = jh ? gi.homeTeam : gi.awayTeam, OI = jh ? gi.awayTeam : gi.homeTeam;
  const rows = (t, ti) => { const nm = lsNames(ti); return ((t && t.players) || []).map(p => { const f2 = nv(p.fg_2m), f2a = f2 + nv(p.fg_2mis), f3 = nv(p.fg_3m), f3a = f3 + nv(p.fg_3mis), ft = nv(p.ft_m), fta = ft + nv(p.ft_mis);
    return [(nm[String(p.playerId)] || nm['#' + p.jerseyNumber] || '').replace(/'/g, '׳'), p.jerseyNumber != null ? p.jerseyNumber : '', p.starter ? 1 : 0, String(p.minutes || '0:00').replace(/^0(\d)/, '$1'), nv(p.points), nv(p.reb_d) + nv(p.reb_o), nv(p.ast), nv(p.stl), nv(p.blk), f2 + f3, f2a + f3a, f3, f3a, ft, fta, nv(p.reb_o), nv(p.reb_d), nv(p.to), nv(p.f), 0]; }); };
  const TJ = (J && J.teamActionsTotal) || {}, TO = (O && O.teamActionsTotal) || {}, pair = fn => [fn(TJ), fn(TO)];
  const hq = bg.homeQuarterScores || [], aq = bg.awayQuarterScores || [], cq = +gi.currentQuarter || 0;
  const q = hq.map((h, i) => jh ? [nv(h), nv(aq[i])] : [nv(aq[i]), nv(h)]).filter((x, i) => i < Math.max(cq, 1) && (i < 4 || x[0] || x[1]));
  return {
    q, fg: pair(t => [nv(t.fg_2m) + nv(t.fg_3m), nv(t.fg_2m) + nv(t.fg_2mis) + nv(t.fg_3m) + nv(t.fg_3mis)]), p3: pair(t => [nv(t.fg_3m), nv(t.fg_3m) + nv(t.fg_3mis)]), ft: pair(t => [nv(t.ft_m), nv(t.ft_m) + nv(t.ft_mis)]),
    t: { ast: pair(t => nv(t.ast)), reb: pair(t => nv(t.reb_d) + nv(t.reb_o)), or: pair(t => nv(t.reb_o)), dr: pair(t => nv(t.reb_d)), stl: pair(t => nv(t.stl)), blk: pair(t => nv(t.blk)), to: pair(t => nv(t.to)), pf: pair(t => nv(t.f)) }, pl: [rows(J, JI), rows(O, OI)], live: 1,
  };
}
function pbpLs(d, f) {
  const gi = d.gameInfo || {}, jh = isJ(gi.homeTeam), names = {};
  [gi.homeTeam, gi.awayTeam].forEach((t, i) => ((t && t.players) || []).forEach(p => { names[(i + 1) + ':' + p.jerseyNumber] = ((p.firstNameLocal || '') + ' ' + (p.lastNameLocal || '')).trim() || titleCase((p.firstName || '') + ' ' + (p.lastName || '')); }));
  const acts = (d.actions && d.actions.actions) || [], sc = [0, 0], out = [];
  for (const a of acts) {
    const P = a.parameters || {}, tm = P.team, side = tm === 1 ? (jh ? 'j' : 'o') : tm === 2 ? (jh ? 'o' : 'j') : null, who = tm && P.player != null ? (names[tm + ':' + P.player] || '') : '', q = a.quarter || 1;
    let text = '', big = 0; const add = n => { if (side === 'j') sc[0] += n; else if (side === 'o') sc[1] += n; };
    switch (a.type) {
      case 'game': text = P.type === 'start-of-game' ? 'שריקת פתיחה' : P.type === 'end-of-game' ? 'סיום המשחק' : ''; break;
      case 'quarter': text = P.type === 'start-of-quarter' ? 'תחילת ' + PBQ(q) : P.type === 'end-of-quarter' ? 'סיום ' + PBQ(q) : ''; break;
      case 'shot': { const made = P.made === 'made', pts = +P.points || 2; text = pts === 3 ? (made ? 'שלשה' : 'החטאה לשלוש') : (made ? 'קליעה ל-2' : 'החטאה ל-2'); if (made) { add(pts); big = 1; } break; }
      case 'freeThrow': { const made = P.made === 'made'; text = made ? 'קליעה מהעונשין' : 'החטאה מהעונשין'; if (made) { add(1); big = 1; } break; }
      case 'rebound': text = P.type === 'offensive' ? 'ריבאונד התקפה' : 'ריבאונד הגנה'; break;
      case 'assist': text = 'אסיסט'; break; case 'steal': text = 'חטיפה'; break; case 'block': text = 'חסימה'; break; case 'turnover': text = 'איבוד'; break;
      case 'foul': text = P.kind === 'offensive' ? 'עבירת התקפה' : P.type === 'technical' ? 'עבירה טכנית' : P.type === 'unsportsmanlike' ? 'עבירה בוטה' : 'עבירה'; break;
      case 'timeout': text = 'פסק זמן'; break; case 'challenge': text = 'אתגר מאמן'; break;
    }
    if (!text) continue;
    if (['shot', 'freeThrow', 'rebound', 'assist', 'steal', 'block', 'turnover', 'foul'].includes(a.type)) text = (who || '') + (who ? ' – ' : '') + text;
    else if (side && a.type === 'timeout') text = (side === 'j' ? ME : f.o) + ' – ' + text;
    out.push({ q, clock: a.quarterTime || '', side, text, big, sc: big ? [sc[0], sc[1]] : null });
  }
  return out.reverse();
}
export const lsFinal = d => { const gi = d.gameInfo || {}, bg = (d.boxscore && d.boxscore.gameInfo) || {}, h = +bg.homeScore || 0, a = +bg.awayScore || 0; return isJ(gi.homeTeam) ? [h, a] : [a, h]; };

/* ================= משחקים שהסתיימו: סטטיסטיקה ומהלך המשחק ================= */
const PAST = {}, PBPC = {}, official = c => c === 'league' || c === 'wcup';
export function pastLs(f) {
  if (!official(f.c)) return Promise.resolve(null); if (PAST[f.id]) return PAST[f.id];
  const id = DB.PBP[f.id] || lsk()[f.id];
  const p = (id ? getJSON(LSB + 'game-' + id + '.json') : NATIVE ? lsGame(f) : Promise.resolve(null)).then(d => d && d.gameInfo && d.boxscore ? d : null).catch(() => null);
  PAST[f.id] = p; p.then(x => { if (!x) delete PAST[f.id]; }); return p;
}
export function pastPbp(f) {
  if (PBPC[f.id]) return PBPC[f.id];
  const c = f.c === 'euro' ? egCode(f) : null, id = DB.PBP[f.id];
  const p = (c ? pbpEuro(c, f) : official(f.c) ? pastLs(f).then(d => d ? pbpLs(d, f) : null) : id ? getJSON(LSB + 'game-' + id + '.json').then(d => pbpLs(d, f)) : Promise.resolve(null)).then(x => x && x.length ? x : null).catch(() => null);
  PBPC[f.id] = p; p.then(x => { if (!x) delete PBPC[f.id]; }); return p;
}
async function livePbp(f) {
  try {
    if (f.c === 'euro') { const c = egCode(f); return c ? await pbpEuro(c, f) : null; }
    if (NATIVE && ['league', 'wcup', 'cup', 'super'].includes(f.c)) { const d = await lsGame(f); return d ? pbpLs(d, f) : null; }
  } catch (e) { }
  return null;
}

/* ================= בדיקה אחת של המשחק הבא ================= */
let full = 0;
export async function pollLive(n, prev) {
  const same = prev && prev.app === n.id;
  const lsd = NATIVE && ['league', 'wcup'].includes(n.c) ? await lsGame(n).catch(() => null) : null;
  if (lsd) {
    const L = { app: n.id, src: 'ls', id: 0, jh: !!n.h, hs: 0, as: 0, r: [0, 0], status: 'pre', st: '', clock: '', q: [], top: [], url: url365(n), at: Date.now() };
    applyLs(L, lsd);
    if (L.status !== 'pre') { try { L.gs = gsLs(lsd); L.top = topOf(L.gs, L.jh); } catch (e) { } const pb = pbpLs(lsd, n); L.pbp = pb && pb.length ? pb : (same ? prev.pbp : null); }
    return L;
  }
  if (n.c === 'euro') { const L = await elLive(n, prev).catch(() => null); if (L) return L; }
  const cur = await api('games/current/', '&competitors=' + TEAM365), want = id365(n), gl = cur.games || [];
  const g = gl.find(x => String(x.id) === want) || gl.find(x => Math.abs(Date.parse(x.startTime) - n.t) < 3 * H);
  if (!g) return null;
  const det = g.statusGroup >= 3 ? ((await gameDetail(g.id).catch(() => null)) || g) : g, L = live365(det, n, url365(n));
  const on = L.status !== 'pre', moved = !same || prev.r[0] !== L.r[0] || prev.r[1] !== L.r[1];
  if (on && (moved || L.status === 'end' || Date.now() - full >= 1e4)) {
    full = Date.now(); try { L.gs = gs365(det, await gameStats(g.id)); L.gs.live = 1; } catch (e) { }
    const pb = await livePbp(n); L.pbp = pb && pb.length ? pb : (same ? prev.pbp : null);
  } else if (same) { L.gs = prev.gs; L.pbp = prev.pbp; }
  return L;
}
/* המשחק בלייב נשמר במכשיר (עד 3 שעות), כדי שהכרטיס יופיע מיד כשפותחים את האפליקציה באמצע משחק */
export const savedLive = () => { const x = LS.get('hpj-live-L', null); return x && x.L && x.L.app && x.L.status !== 'end' && Date.now() - x.ts < 3 * H ? x.L : null; };
let saveAt = 0, saveSig = '';
export function saveLive(L, n) {
  if (L.status === 'end' || (L.status === 'pre' && Date.now() < n.t)) { if (saveAt || LS.get('hpj-live-L', null)) LS.set('hpj-live-L', null); saveAt = 0; return; }
  const sig = L.status + '|' + L.r.join('-') + '|' + L.st, now = Date.now(); if (sig === saveSig && now - saveAt < 1e4) return;
  saveSig = sig; saveAt = now; LS.set('hpj-live-L', { ts: now, L: { ...L, pbp: L.pbp ? L.pbp.slice(0, 40) : null } });
}

/* ================= מצב בדיקה: המשחק הבא כאילו הוא בלייב ================= */
const shot = p => p === 3 ? 'שלשה' : p === 2 ? 'קליעה ל-2' : 'קליעה מהעונשין';
const mmss = s => String(s / 60 | 0).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
function demoQs(q, J, O, pick) {
  const ev = [], sc = [0, 0];
  for (let k = 0; k < 3; k++) {
    const left = q[k].slice(), n = Math.max(1, Math.ceil((left[0] + left[1]) / 2.2)); let t = 600;
    ev.push({ q: k + 1, clock: '10:00', side: null, text: 'תחילת ' + PBQ(k + 1), big: 0, sc: null });
    while (left[0] + left[1] > 0) {
      const i = left[0] && (!left[1] || Math.random() < .5) ? 0 : 1, pts = left[i] >= 3 && Math.random() < .3 ? 3 : left[i] >= 2 ? 2 : 1;
      left[i] -= pts; sc[i] += pts; t = Math.max(3, t - Math.round(600 / n * (.6 + Math.random() * .8)));
      ev.push({ q: k + 1, clock: mmss(t), side: i ? 'o' : 'j', text: (pick(i ? O : J) || (i ? 'יריבה' : ME)) + ' – ' + shot(pts), big: 1, sc: sc.slice() });
    }
    ev.push({ q: k + 1, clock: '00:00', side: null, text: 'סיום ' + PBQ(k + 1), big: 0, sc: null });
  }
  return ev.reverse();
}
const demoSync = (L, sc) => { const r = [sc[0], sc[1]], hs = L.jh ? r[0] : r[1], as = L.jh ? r[1] : r[0];
  return { ...L, r, hs, as, at: Date.now(), q: L.qa.map((x, i) => ({ n: i < 4 ? 'Q' + (i + 1) : 'OT' + (i - 3), h: L.jh ? x[0] : x[1], a: L.jh ? x[1] : x[0] })), gs: L.gs ? { ...L.gs, q: L.qa } : null }; };
export function mkDemo(n, gsOf) {
  const src = DB.F.filter(f => f.r && gsOf(f.id) && gsOf(f.id).q && gsOf(f.id).q.length).sort((a, b) => b.t - a.t)[0], g0 = src ? gsOf(src.id) : null, jh = !!n.h;
  const q = (g0 ? g0.q.slice(0, 3).map(x => x.slice()) : [[22, 18], [19, 21], [24, 20]]).concat([[7, 5]]), sc = [0, 1].map(i => q.reduce((a, x) => a + x[i], 0));
  const pn = i => ((g0 && g0.pl && g0.pl[i]) || []).map(r => r[0]).filter(Boolean), J = pn(0), O = pn(1), pick = a => a.length ? a[Math.random() * a.length | 0] : '';
  const L = { app: n.id, id: 0, jh, status: 'live', st: 'רבע 4', clock: '07:40', q: [], top: [], url: url365(n), demo: 1, J, O, qa: q, gs: g0 ? { ...g0, q } : null,
    pbp: [{ q: 4, clock: '10:00', side: null, text: 'תחילת Q4', big: 0, sc: null }, ...demoQs(q, J, O, pick)] };
  const s0 = [sc[0] - q[3][0], sc[1] - q[3][1]];
  L.pbp.unshift({ q: 4, clock: '08:55', side: 'j', text: (pick(J) || ME) + ' – קליעה ל-2', big: 1, sc: [s0[0] + 2, s0[1]] });
  const out = demoSync(L, sc); out.top = topOf(out.gs, jh); return out;
}
export function demoTick(L) {
  const pick = a => a.length ? a[Math.random() * a.length | 0] : '';
  const t = L.clock.split(':').map(Number); let sec = t[0] * 60 + t[1] - 12, st = L.st; const qa = L.qa.map(x => x.slice()), pbp = L.pbp.slice(); let qn = qa.length;
  if (sec <= 0) { sec = 300; qa.push([0, 0]); qn = qa.length; st = 'הארכה' + (qn - 4 > 1 ? ' ' + (qn - 4) : ''); pbp.unshift({ q: qn, clock: '05:00', side: null, text: 'תחילת הארכה', big: 0, sc: null }); }
  const clock = mmss(sec);
  if (Math.random() < .7) {
    const i = Math.random() < .55 ? 0 : 1, pts = [2, 2, 3, 1][Math.random() * 4 | 0]; qa[qa.length - 1][i] += pts;
    const sc = [0, 1].map(k => qa.reduce((a, x) => a + x[k], 0));
    pbp.unshift({ q: qn, clock, side: i ? 'o' : 'j', text: (pick(i ? L.O : L.J) || (i ? 'יריבה' : ME)) + ' – ' + shot(pts), big: 1, sc });
  }
  return demoSync({ ...L, st, clock, qa, pbp }, [0, 1].map(k => qa.reduce((a, x) => a + x[k], 0)));
}
/* שמות שחקני היריבה האמיתית במקום שמות המשחק שעליו מבוססת ההדמיה */
export function demoNames(L, sq) {
  if (!sq.length) return L;
  const mp = {}, rows = L.gs && L.gs.pl ? L.gs.pl[1].map((r, i) => { const a = sq[i % sq.length]; mp[r[0]] = a.n; const x = r.slice(); x[0] = a.n; x[1] = a.j; x[19] = a.id; return x; }) : null, O = sq.map(a => a.n);
  L.O.forEach((n, i) => { if (!mp[n]) mp[n] = O[i % O.length]; });
  const pbp = L.pbp.map(e => { if (e.side !== 'o') return e; const k = Object.keys(mp).find(n => e.text.startsWith(n + ' – ')); return k ? { ...e, text: mp[k] + e.text.slice(k.length) } : e; });
  return { ...L, O, pbp, gs: L.gs && rows ? { ...L.gs, pl: [L.gs.pl[0], rows] } : L.gs };
}
