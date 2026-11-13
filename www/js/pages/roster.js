/* סגל: שחקנים (עם סינון לפי עמדה), צוות האימון, בעלי תפקידים; עמוד שחקן */
import { html, useState, cx } from '../ui/html.js';
import { useApp, Chips, HeaderSlot, Photo, usePhoto, Empty, ExtLink, Seg, Back, LinkRows } from '../ui/kit.js';
import { SplitTable, isDnp } from '../ui/score.js';
import { ME, COMPS, CN, LINKS } from '../data/constants.js';
import { DB, gsOf, posGroups } from '../data/store.js';
import { SEASON, fmt } from '../core/env.js';

function TeamPhoto() {
  const [src, err] = usePhoto(DB.TP, 'team'), [ok, setOk] = useState(false);
  return src ? html`<div class="r-lg glass" style=${{ overflow: 'hidden', height: 190, padding: 0 }}><img src=${src} alt="קבוצת הפועל ירושלים" fetchpriority="high" decoding="async" referrerPolicy="no-referrer" onLoad=${() => setOk(true)} onError=${err} style=${{ width: '100%', height: '100%', objectFit: 'cover', opacity: ok ? 1 : 0, transition: 'opacity .3s' }}/></div>` : null;
}
const MEM = { v: 'pl', ps: 'all' };
export default function Roster() {
  const A = useApp(), [v, setV0] = useState(MEM.v), [ps, setPs0] = useState(MEM.ps);
  const setV = k => { MEM.v = k; setV0(k); }, setPs = k => { MEM.ps = k; setPs0(k); };
  if (!DB.PL.length && DB.ok) return html`<${Empty} title=${'עונת ' + SEASON} sub="הסגל עוד לא נטען לאפליקציה."><${ExtLink} href=${LINKS.club + '/team'}>הסגל באתר הרשמי<//><//>`;
  const list = l => html`<div class="glass lite r-lg stg" style=${{ overflow: 'hidden' }}>${l.map((s, i) => html`<div key=${i} class="li"><span class="grow b">${s[1]}</span><span class="t-s mu">${s[0]}</span></div>`)}</div>`;
  return html`<div class="stack">
    <${HeaderSlot}><${Chips} items=${[['pl', 'שחקנים'], ['st', 'צוות האימון'], ['of', 'בעלי תפקידים']]} val=${v} set=${setV} label="קבוצה"/>
      <div class=${cx('fx-sub', v === 'pl' && 'on')}><div><${Chips} sub=${1} items=${[['all', 'כל העמדות'], ['G', 'גארדים'], ['F', 'פורוורדים'], ['C', 'סנטרים']]} val=${ps} set=${setPs} label="עמדה"/></div></div><//>
    <${TeamPhoto}/>
    ${v === 'pl' ? html`<div key=${ps} class="roster-grid stg">${DB.PL.filter(p => ps === 'all' || posGroups(p).includes(ps)).map(p => html`<button key=${p[0] + p[1]} onClick=${() => A.openPlayer(p)} class="glass lite r-lg press" style=${{ overflow: 'hidden', padding: 0, textAlign: 'right' }}>
        <div class="rel" style=${{ aspectRatio: '4/4.4' }}><${Photo} u=${p[6]} k=${p[9]} fallback=${p[0]} cls="abs-fill"/><span class="pcard-no">${p[0]}</span></div>
        <div style=${{ padding: '9px 12px 11px' }}><div class="b" style=${{ lineHeight: 1.25 }}>${p[1]}</div><div class="t-xs mu">${p[2]} · ${p[3]} מ׳</div></div></button>`)}</div>`
      : list(v === 'st' ? DB.ST : DB.OF)}
    <div class="t-xs mu">מקור: האתר הרשמי של המועדון · הסטטיסטיקה האישית מ-365scores</div>
  </div>`;
}

/* ---------- עמוד שחקן ---------- */
const ppc = (m, a) => a ? Math.round(m / a * 100) + '%' : '-';
const f1 = x => { const t = x.toFixed(1); return t.slice(-2) === '.0' ? t.slice(0, -2) : t; };
const secOf = t => { const m = String(t).match(/^(\d+):(\d+)$/); return m ? (+m[1]) * 60 + (+m[2]) : 0; };
const ageOf = b => { const m = String(b).match(/^(\d+)\/(\d+)\/(\d{4})$/); if (!m) return ''; const n = new Date(); let a = n.getFullYear() - (+m[3]); if (n.getMonth() < (+m[2] - 1) || (n.getMonth() === (+m[2] - 1) && n.getDate() < (+m[1]))) a--; return a; };
/* סטטיסטיקה לפי מפעל מתוך טבלאות המשחקים */
function pStats(aid) {
  const by = {}, log = []; if (!aid) return { by, log };
  for (const f of DB.F) {
    if (!f.r) continue; const e = gsOf(f.id), w = e && e.pl && e.pl[0] && e.pl[0].find(r => r[19] === aid); if (!w) continue;
    log.push([f, w]); const a = by[f.c] || (by[f.c] = new Array(17).fill(0)); a[0]++; a[1] += secOf(w[3]); for (let i = 4; i < 19; i++) a[i - 2] += w[i];
  }
  log.sort((x, y) => y[0].t - x[0].t); return { by, log };
}
const avgRow = a => [a[0], f1(a[1] / 60 / a[0]), f1(a[2] / a[0]), f1(a[3] / a[0]), f1(a[4] / a[0]), f1(a[5] / a[0]), f1(a[6] / a[0]), ppc(a[7], a[8]), ppc(a[9], a[10]), ppc(a[11], a[12]), f1(a[13] / a[0]), f1(a[14] / a[0]), f1(a[15] / a[0]), f1(a[16] / a[0])];
const totRow = a => [a[0], Math.round(a[1] / 60), a[2], a[3], a[4], a[5], a[6], a[7] + '/' + a[8], a[9] + '/' + a[10], a[11] + '/' + a[12], a[13], a[14], a[15], a[16]];
const PH = ['דק׳', 'נק׳', 'ריב׳', 'אס׳', 'חט׳', 'חס׳', 'מהשדה', 'שלוש', 'עונשין', 'ר״ה', 'ר״ג', 'איב׳', 'עב׳'];
const prw = w => [w[3], w[4], w[5], w[6], w[7], w[8], w[9] + '/' + w[10], w[11] + '/' + w[12], w[13] + '/' + w[14], w[15], w[16], w[17], w[18]];
export function Player({ p }) {
  const A = useApp(), [md, setMd] = useState('a'), { by, log } = pStats(p[7]), ks = Object.keys(COMPS).filter(k => by[k]);
  const tt = ks.reduce((t, k) => t.map((x, i) => x + by[k][i]), new Array(17).fill(0)), fm = md === 'a' ? avgRow : totRow, ag = ageOf(p[5]);
  const HD = ['מש׳', 'דק׳', 'נק׳', 'ריב׳', 'אס׳', 'חט׳', 'חס׳', md === 'a' ? 'FG%' : 'FG', md === 'a' ? '3P%' : '3P', md === 'a' ? 'FT%' : 'FT', 'ר״ה', 'ר״ג', 'איב׳', 'עב׳'];
  const facts = Array.isArray(DB.FACTS[p[1]]) ? DB.FACTS[p[1]].filter(x => Array.isArray(x) && x.length > 1) : [];
  return html`<div><${Back} onClick=${A.back} label="לסגל"/>
    <div class="stack stg">
      <div class="glass-red r-lg row" style=${{ overflow: 'hidden', padding: 0, alignItems: 'stretch' }}>
        <div class="rel shrink0" style=${{ width: 136, aspectRatio: '4/5' }}><${Photo} u=${p[6]} k=${p[9]} fallback=${p[0]} cls="abs-fill"/></div>
        <div class="col pad" style=${{ justifyContent: 'center' }}><div class="num" style=${{ fontSize: 46, fontWeight: 800, lineHeight: 1, opacity: .92 }}>${p[0]}</div><div class="t-l xb" style=${{ marginTop: 4 }}>${p[1]}</div><div class="t-s" style=${{ opacity: .85 }}>${p[2]}</div></div></div>
      <div class="grid2">${[['גובה', p[3] ? p[3] + ' מ׳' : '—'], ['אזרחות', p[4] || '—'], ['תאריך לידה', (p[5] || '—') + (ag !== '' ? ' · גיל ' + ag : '')], ['עמדה', p[2] || '—']].map(([l, v]) => html`<div key=${l} class="glass r-md pad-s"><div class="t-xs mu">${l}</div><div class="t-s b">${v}</div></div>`)}</div>
      ${facts.length ? html`<div class="glass r-lg" style=${{ overflow: 'hidden' }}>${facts.map(([l, v], i) => html`<div key=${i} class="li"><span class="grow t-s">${l}</span><b class="t-s num">${v}</b></div>`)}</div>` : null}
      <section class="glass r-lg pad-s"><div class="sec-t" style=${{ padding: '2px 4px 8px' }}>סטטיסטיקה אישית · עונת ${SEASON}</div>
        ${ks.length ? html`<${Seg} items=${[['a', 'ממוצע למשחק'], ['t', 'סה״כ']]} val=${md} set=${setMd} label="תצוגה"/>
          <div key=${md} class="fade-in" style=${{ marginTop: 8 }}><${SplitTable} head="מפעל" heads=${HD} rows=${[...ks.map(k => ({ key: k, name: html`<span class="md">${CN[k] || COMPS[k]}</span>`, cells: fm(by[k]), hi: 2 })), ...(ks.length > 1 ? [{ key: 'all', cls: 'b', name: 'כל המפעלים', cells: fm(tt) }] : [])]}/></div>
          <div class="t-xs mu" style=${{ padding: '8px 4px 0' }}>מש׳ = משחקים שבהם נכלל בסגל · ר״ה = ריבאונד התקפה · ר״ג = ריבאונד הגנה · בלי משחקי הכנה · הנתונים מ-365scores</div>`
        : html`<div class="t-s mu" style=${{ padding: '0 4px 4px' }}>${p[7] ? 'השחקן עוד לא נכלל בסגל של משחק עם נתונים העונה.' : 'השחקן לא נמצא ב-365scores, ולכן אין לו סטטיסטיקה אישית.'}</div>`}</section>
      ${log.length ? html`<section class="glass r-lg pad-s"><div class="sec-t" style=${{ padding: '2px 4px 8px' }}>משחק אחר משחק</div>
        <${SplitTable} head="משחק" heads=${['תוצאה', ...PH]} rows=${log.map(([f, w]) => ({ key: f.id, cls: '', hi: 2,
          name: html`<span class="col" style=${{ lineHeight: 1.2 }}><span class="t-xs b trunc">${f.h ? '' : '@ '}${f.o}</span><span class="t-xxs mu">${fmt.dm(f.t)} · ${CN[f.c] || COMPS[f.c]}</span></span>`,
          cells: [html`<span class=${f.r[0] > f.r[1] ? 'res-w b' : 'res-l b'}>${f.r[0]}–${f.r[1]}</span>`, ...(isDnp(w) ? [html`<span class="mu t-xs nowrap">לא שיחק</span>`] : prw(w))] }))}/></section>` : null}
      ${p[8] || p[9] ? html`<${LinkRows} items=${[p[8] && ['https://www.365scores.com/en-us/basketball/player/' + p[8], 'הדף המלא ב-365scores'], p[9] && ['https://hapoel.co.il/player.asp?PlayerId=' + p[9], 'הדף באתר הרשמי של המועדון']]}/>` : null}
    </div></div>`;
}
