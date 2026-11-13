/* התאמת שמות קבוצות בין מקורות (365scores, יוטיוב) לשמות באפליקציה */
import { DB } from './store.js';
import { ME } from './constants.js';
export const norm = x => String(x).replace(/ב[״"]ש/g, 'באר שבע').replace(/ת[״"]א/g, 'תל אביב').replace(/[״"׳']/g, '');
const GENERIC = ['הפועל', 'מכבי', 'בני', 'עירוני', 'אליצור', 'קרית', 'קריית'];
export const tokens = o => norm(o).split(/[\s\/\-–]+/).filter(w => w.length >= 3 && !GENERIC.slice(0, 5).includes(w));
export const tblKey = n => { const t = norm(n).split(/[\s\/\-–]+/).filter(Boolean), pre = t.find(w => w === 'הפועל' || w === 'מכבי') || ''; return { pre, tk: t.filter(w => w.length >= 3 && !GENERIC.includes(w)) }; };
export const keyScore = (k, q) => (k.pre && q.pre && k.pre !== q.pre) ? -1 : q.tk.filter(w => k.tk.includes(w)).length;
/* השם הקרוב ביותר מבין כל הקבוצות שמופיעות באפליקציה */
export function appName(n) {
  if (!n) return '';
  const all = [ME, ...DB.LG.map(r => r.n), ...DB.EC.flatMap(g => g[1].map(t => t[0])), ...((DB.WC && DB.WC.G) || []).flatMap(g => g[1].map(t => t[0])), ...DB.F.map(f => f.o)].filter(Boolean);
  if (all.includes(n)) return n;
  const k = tblKey(n); let best = '', bs = 0;
  for (const c of new Set(all)) { const sc = keyScore(k, tblKey(c)); if (sc > bs) { bs = sc; best = c; } }
  return best || String(n).replace(/'/g, '׳');
}
