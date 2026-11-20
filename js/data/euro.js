/* היורוקאפ: מועדים ותוצאות של הפועל וטבלאות הבתים מה-API הרשמי של היורוליג */
import { getJSON } from '../core/net.js';
import { ECG, ME } from './constants.js';
import { EURO_TEAMS } from './crest-map.js';
import { DB, egCode } from './store.js';
export const EL_SEASON = 'U2026';
export async function loadEuro() {
  const j = await getJSON('https://feeds.incrowdsports.com/provider/euroleague-feeds/v2/competitions/U/seasons/' + EL_SEASON + '/games?teamCode=JER'), by = {};
  (j.data || []).forEach(g => { by[g.code] = g; });
  let n = 0, last = 0;
  DB.F = DB.F.map(f => {
    const g = f.c === 'euro' && by[egCode(f)]; if (!g) return f;
    const home = g.home.code === 'JER', o = { ...f, t: Date.parse(g.date) || f.t }; delete o.tbd;
    if (g.status === 'result') o.r = home ? [g.home.score, g.away.score] : [g.away.score, g.home.score];
    n++; return o;
  });
  (j.data || []).forEach(g => { if (g.status === 'result') last = Math.max(last, (g.round && g.round.round) || 0); });
  /* הטבלה העדכנית ביותר: גם של המחזור שמתנהל עכשיו */
  const stOf = r => getJSON('https://api-live.euroleague.net/v3/competitions/U/seasons/' + EL_SEASON + '/rounds/' + r + '/basicstandings').catch(() => null);
  const gp = x => ((x && x.teams) || []).reduce((a, t) => a + (t.gamesWon || 0) + (t.gamesLost || 0), 0);
  const [a, b] = await Promise.all([last ? stOf(last) : null, stOf(last + 1)]), st = gp(b) > gp(a) ? b : a;
  if (st) {
    const G = {}; (st.teams || []).forEach(t => { (G[t.groupName] = G[t.groupName] || []).push(t); });
    const ec = Object.keys(G).sort().map(k => [ECG[k] || ('בית ' + k), G[k].sort((x, y) => x.position - y.position).map(t => [t.club.code === 'JER' ? ME : EURO_TEAMS[t.club.code] || t.club.name, t.gamesWon, t.gamesLost, parseInt(t.pointsDifference, 10) || 0])]);
    if (ec.length) DB.EC = ec;
  }
  return n;
}
