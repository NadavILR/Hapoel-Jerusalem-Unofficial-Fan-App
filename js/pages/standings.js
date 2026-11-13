/* טבלאות: ליגת ווינר, יורוקאפ (בתים), גביע ווינר (בתים + נוקאאוט), גביע המדינה; בראקטים של כל מפעל */
import { html, useState, Fragment, cx } from '../ui/html.js';
import { useApp, Crest, Chips, HeaderSlot, Empty, LinkRows } from '../ui/kit.js';
import { ME, LINKS, BRT } from '../data/constants.js';
import { DB } from '../data/store.js';
import { brData } from '../data/s365.js';
import { SEASON, fdShort } from '../core/env.js';

const MK = { g: ['mk-g', 'פלייאוף'], p: ['mk-p', 'פליי-אין'], r: ['mk-r', 'בית תחתון'] };
const sgn = v => v == null || isNaN(v) ? '—' : html`<span class="ltr">${(v > 0 ? '+' : v < 0 ? '−' : '') + Math.abs(v)}</span>`;
const streak = v => !v ? '—' : html`<span class=${v > 0 ? 'res-w b' : 'res-l b'}>${(v > 0 ? 'נ' : 'ה') + Math.abs(v)}</span>`;
const pct = (w, l) => w + l ? html`<span class="ltr">${Math.round(w / (w + l) * 100)}%</span>` : '—';
const PM = html`<span class="ltr">+/-</span>`;
/* טבלה: עמודת שם (שם הקבוצה בשורה אחת, לא נשבר) וגלילה לצד בעמודות המספרים */
function Table({ title, heads, rows, mark, legend }) {
  return html`<section class="stack-s">
    ${title ? html`<h2 class="sec-t glass-2 r-full" style=${{ display: 'inline-flex', margin: 0, padding: '4px 14px' }}>${title}</h2>` : null}
    <div class="glass lite r-lg" style=${{ overflow: 'hidden' }}><div class="tscroll"><table class="tbl">
      <thead><tr><th class="mark"></th><th class="pos">#</th><th class="tn">קבוצה</th>${heads.map((h, i) => html`<th key=${i}>${h}</th>`)}</tr></thead>
      <tbody>${rows.map((r, i) => { const m = mark && mark(i, r); return html`<tr key=${r.n} class=${r.n === ME ? 'me' : ''}>
        <td class=${cx('mark', m && MK[m][0])}></td><td class="pos num b">${i + 1}</td>
        <td class="tn"><span class="row" style=${{ gap: 8 }}><${Crest} n=${r.n} s=${22}/><span class="col"><span class="nowrap">${r.n}</span>${r.tag ? html`<span class="t-xxs mu">${r.tag}</span>` : null}</span></span></td>
        ${r.cells.map((v, j) => html`<td key=${j} class=${j === r.cells.length - 1 ? 'xb' : ''}>${v}</td>`)}</tr>`; })}</tbody></table></div></div>
    ${legend ? html`<div class="legend glass-2 r-full" style=${{ display: 'inline-flex', padding: '4px 12px' }}>${legend.map(([c, l]) => html`<span key=${c}><i class=${MK[c][0]}></i>${l || MK[c][1]}</span>`)}</div>` : null}
  </section>`;
}
const groupRows = t => t.map(r => ({ n: r[0], tag: r[4] || '', cells: [r[1] == null ? '—' : r[1] + r[2], r[1] ?? '—', r[2] ?? '—', sgn(r[3]), r[1] == null ? '—' : pct(r[1], r[2])] }));
const Note = ({ children }) => html`<div class="t-xs mu glass-2 r-md" style=${{ padding: '6px 12px', display: 'inline-block' }}>${children}</div>`;

/* ---------- בראקט ---------- */
function Match({ m, bo, ghost }) {
  m = m || {};
  const need = Math.ceil(bo / 2), pl = m.as != null && m.bs != null, dn = pl && m.as !== m.bs && Math.max(m.as, m.bs) >= need, aw = dn && m.as > m.bs, bw = dn && m.bs > m.as, T = m.m ? 'a' : 'div';
  const x = [m.t ? fdShort(m) : null, m.v].filter(Boolean).join(' · ');
  const row = (n, sc, win) => html`<div class=${cx('tm', win && 'win', n === ME && 'red')} style=${{ opacity: dn && !win ? .6 : 1 }}>${n ? html`<${Crest} n=${n} s=${18}/>` : html`<span style=${{ width: 18, height: 18, borderRadius: 9, border: '1px dashed var(--mu)', flexShrink: 0 }}></span>`}<span class=${cx('grow trunc', !n && 'mu')}>${n || (ghost ? '' : 'טרם נקבע')}</span><span class="num ltr">${sc ?? ''}</span></div>`;
  return html`<${T} href=${m.m ? 'https://www.365scores.com/he/basketball/match/' + m.m : undefined} target=${m.m ? '_blank' : undefined} rel="noopener noreferrer" class=${cx('m glass lite', ghost && 'ghost')}>${row(m.a, m.as, aw)}${row(m.b, m.bs, bw)}${x ? html`<div class="t-xxs mu" style=${{ paddingTop: 3 }}>${x}</div>` : null}<//>`;
}
function Bracket({ k }) {
  const T = BRT[k], src = brData(k), raw = T.r.map((_, i) => Array.isArray(src[i]) ? src[i] : []);
  const has = raw.some(r => r.some(Boolean)), ghost = !raw.some(r => r.some(m => m && (m.a || m.b)));
  return html`<section class="stack-s">
    <div class="glass-2 r-md" style=${{ padding: '8px 14px' }}><h2 class="sec-t" style=${{ margin: 0 }}>${T.t}</h2><div class="t-xs mu">${T.f}</div></div>
    ${!has ? html`<${Note}>הבראקט של עונת ${SEASON} עוד לא פורסם. הצמדים יופיעו כאן כשייקבעו.<//>` : ghost ? html`<${Note}>הזוגות עוד לא נקבעו – הם יופיעו כאן כשייקבעו<//>` : null}
    <div class="brk">${T.r.map(([n, bo, c], i) => html`<div key=${n} class="stage"><div class="st-t">${n}${bo > 1 ? ' · הטוב מ-' + bo : ''}</div>
      ${Array.from({ length: Math.max(c, raw[i].length) }, (_, j) => html`<${Match} key=${j} m=${raw[i][j]} bo=${bo} ghost=${ghost}/>`)}</div>`)}</div>
    ${has && !ghost ? html`<div class="t-xs mu">לחיצה על צמד עם קישור פותחת אותו ב-365scores</div>` : null}
  </section>`;
}

const MEM = { k: 'league' };
export default function Standings({ arg }) {
  const [k, setK0] = useState(arg || MEM.k), setK = v => { MEM.k = v; setK0(v); window.scrollTo(0, 0); };
  const none = k === 'league' ? !DB.LG.length : k === 'euro' ? !DB.EC.length : k === 'wcup' ? (!DB.WC.G.length && !DB.WC.KO) : false;
  const links = { league: [[BRT.league.u, 'הבראקט ב-365scores'], [LINKS.basket, 'האתר הרשמי של ליגת ווינר סל']], euro: [[LINKS.e365, 'הטבלאות החיות ב-365scores'], [BRT.euro.u, 'הבראקט ב-365scores']], cup: [[BRT.cup.u, 'הבראקט ב-365scores']], wcup: [[LINKS.wcup365, 'גביע ווינר ב-365scores'], [BRT.wcup.u, 'הבראקט ב-365scores']] }[k];
  return html`<div>
    <${HeaderSlot}><${Chips} items=${[['league', 'ליגת ווינר סל'], ['euro', 'יורוקאפ'], ['cup', 'גביע המדינה'], ['wcup', 'גביע ווינר סל']]} val=${k} set=${setK} label="מפעל"/><//>
    <div key=${k} class="stack stg">
      ${none ? html`<${Empty} title="אין עדיין נתונים" sub=${'הטבלאות של עונת ' + SEASON + ' במפעל הזה עוד לא פורסמו.'}/>`
      : k === 'league' ? html`<${Table} heads=${['מש׳', 'נצ׳', 'הפ׳', PM, 'רצף', 'נק׳']} rows=${DB.LG.map(r => ({ n: r.n, cells: [r.g ?? (r.w + r.l), r.w, r.l, sgn(r.d), streak(r.s), r.p] }))} mark=${i => i < 6 ? 'g' : i < 10 ? 'p' : 'r'} legend=${[['g'], ['p'], ['r']]}/><${Note}>2 נק׳ לניצחון, נקודה להפסד · מקום 1–6 לפלייאוף, 7–10 לפליי-אין<//>`
      : k === 'euro' ? html`${DB.EC.map(([n, t]) => html`<${Table} key=${n} title=${n} heads=${['מש׳', 'נצ׳', 'הפ׳', PM, '% נצ׳']} rows=${groupRows(t)} mark=${i => i < 4 ? 'g' : ''}/>`)}<div class="legend glass-2 r-full" style=${{ display: 'inline-flex', padding: '4px 12px' }}><span><i class="mk-g"></i>עולות לשלב הבא</span></div>`
      : k === 'wcup' ? html`${DB.WC.G.map(([n, t]) => html`<${Table} key=${n} title=${n} heads=${['מש׳', 'נצ׳', 'הפ׳', PM, '% נצ׳']} rows=${groupRows(t)} mark=${(i, r) => /רבע/.test(r.tag) ? 'g' : /פליי/.test(r.tag) ? 'p' : ''}/>`)}<div class="legend glass-2 r-full" style=${{ display: 'inline-flex', padding: '4px 12px' }}><span><i class="mk-g"></i>רבע גמר</span><span><i class="mk-p"></i>פליי-אין</span></div>`
      : null}
      <${Bracket} k=${k}/>
      <${LinkRows} items=${links}/>
    </div></div>`;
}
