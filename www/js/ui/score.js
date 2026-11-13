/* תוצאה, לייב, רבעים וסטטיסטיקות משחק */
import { html, useState, useEffect, useRef, useMemo, Fragment, cx } from './html.js';
import { Icon } from './icons.js';
import { Crest, Seg } from './kit.js';
import { ME } from '../data/constants.js';
import { PBQ } from '../data/live.js';

/* כשקבוצה קולעת: +1/+2/+3 קופץ מעל התוצאה שלה */
export function Pts({ v, k }) {
  const p = useRef({ v, k }), [d, setD] = useState(null);
  useEffect(() => { const o = p.current; p.current = { v, k }; if (o.k !== k) return; const n = v - o.v; if (n > 0) { setD({ n, id: Date.now() }); const t = setTimeout(() => setD(null), 1800); return () => clearTimeout(t); } }, [v, k]);
  return html`<span class="rel" style=${{ display: 'inline-block' }}>${v}${d ? html`<span key=${d.id} class="pts-pop ltr">+${d.n}</span>` : null}</span>`;
}
const stLabel = L => L.status === 'live' ? (L.st || 'לייב') : L.status === 'end' ? 'סיום' : 'לפני שריקת הפתיחה';
/* התוצאה בלייב (בכרטיס האדום ובעמוד המשחק) */
export function LiveScore({ L, g, big }) {
  const hm = L.jh ? ME : g.o, aw = L.jh ? g.o : ME, cs = big ? 64 : 52;
  const side = n => html`<div class="col" style=${{ alignItems: 'center', gap: 6, textAlign: 'center' }}><${Crest} n=${n} s=${cs}/><div class=${cx('t-s', n === ME ? 'xb' : 'md')} style=${{ lineHeight: 1.25 }}>${n}</div></div>`;
  return html`<div>
    <div class="row between t-s" style=${{ marginBottom: 10 }}>${L.status === 'live' ? html`<span class="live-pill">לייב</span>` : html`<span class="b">${stLabel(L)}</span>`}
      <span class="b num">${L.status === 'live' ? L.st : ''}${L.status === 'live' && L.clock ? html` · <span class="ltr">${L.clock}</span>` : null}</span></div>
    <div style=${{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto minmax(0,1fr)', alignItems: 'center', gap: 8 }}>
      ${side(hm)}
      <div class="row center sc-big" style=${{ gap: 10, fontSize: big ? 50 : 44 }}><${Pts} v=${L.hs} k=${L.app}/><span style=${{ opacity: .55, fontSize: '.6em' }}>–</span><${Pts} v=${L.as} k=${L.app}/></div>
      ${side(aw)}
    </div>
    ${L.q && L.q.length ? html`<div class="on-red r-md" style=${{ marginTop: 12, padding: '8px 10px' }}><div style=${{ display: 'grid', gridTemplateColumns: `28px repeat(${L.q.length}, minmax(0,1fr))`, gap: 4, textAlign: 'center', alignItems: 'center', fontSize: 12.5 }}>
      <span></span>${L.q.map(x => html`<span key=${x.n} aria-label=${x.n.replace(/^Q(\d)$/, 'רבע $1').replace(/^OT(\d)$/, 'הארכה $1')}>${x.n}</span>`)}
      <span class="row center"><${Crest} n=${hm} s=${18}/></span>${L.q.map(x => html`<b key=${x.n} class="num">${x.h}</b>`)}
      <span class="row center"><${Crest} n=${aw} s=${18}/></span>${L.q.map(x => html`<b key=${x.n} class="num">${x.a}</b>`)}
    </div></div>` : null}
  </div>`;
}
/* פס תוצאה קומפקטי (בתוך סרגל הניווט / מתחת לכותרת) */
export function MiniScore({ g, L, sc }) {
  const hm = g.h ? ME : g.o, aw = g.h ? g.o : ME, live = L && L.status === 'live', hs = L ? L.hs : sc[0], as = L ? L.as : sc[1], fin = !L && sc && sc[0] !== sc[1];
  const num = v => html`<span class="num" style=${{ fontSize: 24, fontWeight: fin && v < Math.max(hs, as) ? 500 : 800, lineHeight: 1 }}>${L ? html`<${Pts} v=${v} k=${L.app}/>` : v}</span>`;
  return html`<div class="row" style=${{ gap: 12, width: '100%' }}>
    <div class="row grow" style=${{ gap: 8 }}><${Crest} n=${hm} s=${30}/>${num(hs)}</div>
    <div class="col" style=${{ alignItems: 'center', minWidth: 64 }}>${live ? html`<span class="red xb t-xs row" style=${{ gap: 4 }}><span class="dot" style=${{ width: 6, height: 6 }}></span>${L.st || 'לייב'}</span><span class="b t-s num ltr">${L.clock || ''}</span>` : html`<span class="mu b t-xs">${L ? '' : 'סיום'}</span>`}</div>
    <div class="row grow" style=${{ gap: 8, justifyContent: 'flex-end' }}>${num(as)}<${Crest} n=${aw} s=${30}/></div>
  </div>`;
}

/* ---------- סטטיסטיקות משחק ---------- */
export function Quarters({ g, gs }) {
  const hm = g.h ? ME : g.o, aw = g.h ? g.o : ME, q = gs.q || [], hi = p => g.h ? p[0] : p[1], ai = p => g.h ? p[1] : p[0];
  if (!q.length) return html`<div class="glass r-lg pad t-s mu">סיכום הרבעים של המשחק לא נטען לאפליקציה.</div>`;
  const lb = i => i < 4 ? 'Q' + (i + 1) : 'OT' + (i - 3), jw = q.filter(p => p[0] > p[1]).length;
  return html`<div class="glass r-lg pad-s"><div class="sec-t" style=${{ padding: '2px 4px 6px' }}>תוצאה לפי רבעים</div>
    <div class="tscroll"><table class="tbl"><thead><tr><th class="tn"></th>${q.map((_, i) => html`<th key=${i} aria-label=${i < 4 ? 'רבע ' + (i + 1) : 'הארכה ' + (i - 3)}>${lb(i)}</th>`)}<th>סה״כ</th></tr></thead>
    <tbody>${[[hm, hi, ai], [aw, ai, hi]].map(([n, f, o]) => html`<tr key=${n}><td class="tn"><span class="row" style=${{ gap: 8 }}><${Crest} n=${n} s=${22}/><span class=${n === ME ? 'b' : ''}>${n}</span></span></td>${q.map((p, i) => html`<td key=${i} class=${f(p) > o(p) ? 'b' : 'mu'}>${f(p)}</td>`)}<td class="xb">${q.reduce((t, p) => t + f(p), 0)}</td></tr>`)}</tbody></table></div>
    <div class="t-xs mu" style=${{ padding: '6px 4px 0' }}>${ME} ניצחה ב-${jw} מתוך ${q.length} ${q.length > 4 ? 'הרבעים וההארכות' : 'הרבעים'}.</div></div>`;
}
const pc = (m, a) => a ? Math.round(m / a * 100) : 0;
const sd = p => ({ m: pc(p[0], p[1]) + '%', s: p[0] + '/' + p[1], v: pc(p[0], p[1]) }), nv = x => ({ m: String(x), s: '', v: x });
const srcName = gs => gs && gs.src === 'el' ? 'האתר הרשמי של היורוקאפ' : gs && gs.src === 'ls' ? 'ה-Live Stats של איגוד הכדורסל' : '365scores';
export function TeamStats({ g, gs }) {
  const hj = !!g.h, hm = hj ? ME : g.o, aw = hj ? g.o : ME, sw = x => hj ? x : [x[1], x[0]], pt = g.r ? sw(g.r) : null, fg = gs.fg && sw(gs.fg), p3 = gs.p3 && sw(gs.p3), ft = gs.ft && sw(gs.ft), T = gs.t || {};
  const p2 = fg && p3 ? fg.map((x, i) => [x[0] - p3[i][0], x[1] - p3[i][1]]) : null, ok = p2 && p2.every(x => x[0] >= 0 && x[1] >= x[0]);
  const nr = (l, k) => T[k] && [l, nv(sw(T[k])[0]), nv(sw(T[k])[1])];
  const rows = [pt && ['נקודות', nv(pt[0]), nv(pt[1])], fg && ['קליעה מהשדה', sd(fg[0]), sd(fg[1])], ok && ['קליעה ל-2', sd(p2[0]), sd(p2[1])], p3 && ['קליעה לשלוש', sd(p3[0]), sd(p3[1])], ft && ['עונשין', sd(ft[0]), sd(ft[1])], nr('ריבאונדים', 'reb'), nr('ריבאונד התקפה', 'or'), nr('ריבאונד הגנה', 'dr'), nr('אסיסטים', 'ast'), nr('חטיפות', 'stl'), nr('חסימות', 'blk'), nr('איבודים', 'to'), nr('עבירות', 'pf')].filter(Boolean);
  if (!rows.length) return html`<div class="glass r-lg pad t-s mu">הסטטיסטיקה הקבוצתית של המשחק לא נטענה לאפליקציה.</div>`;
  const jSide = hj ? 0 : 1;
  return html`<div class="glass r-lg pad"><div class="sec-t" style=${{ marginBottom: 8 }}>סטטיסטיקה קבוצתית</div>
    <div class="row between t-s b" style=${{ marginBottom: 4 }}><span class="row" style=${{ gap: 8 }}><${Crest} n=${hm} s=${22}/>${hm}</span><span class="row" style=${{ gap: 8 }}>${aw}<${Crest} n=${aw} s=${22}/></span></div>
    ${rows.map(([l, a, b]) => { const t = a.v + b.v, w = t ? a.v / t * 100 : 50; return html`<div key=${l} style=${{ padding: '9px 0', borderTop: '1px solid var(--line)' }}>
      <div class="row between t-s"><span class="row" style=${{ gap: 6, width: 96 }}><b class="num">${a.m}</b>${a.s ? html`<span class="t-xxs mu num">${a.s}</span>` : null}</span><span class="t-xs mu grow" style=${{ textAlign: 'center' }}>${l}</span><span class="row" style=${{ gap: 6, width: 96, justifyContent: 'flex-end' }}>${b.s ? html`<span class="t-xxs mu num">${b.s}</span>` : null}<b class="num">${b.m}</b></span></div>
      <div class="bar2" style=${{ marginTop: 6 }}><i class=${jSide === 0 ? 'j' : 'o'} style=${{ width: w + '%' }}></i><i class=${jSide === 0 ? 'o' : 'j'} style=${{ width: (100 - w) + '%' }}></i></div></div>`; })}
    <div class="legend" style=${{ marginTop: 8 }}><span><i style=${{ background: 'var(--red)' }}></i>${ME}</span><span><i style=${{ background: 'rgb(var(--tx-rgb) / .3)' }}></i>${g.o}</span></div>
    <div class="t-xs mu" style=${{ marginTop: 4 }}>הנתונים מ${srcName(gs)}.</div></div>`;
}
const isDnp = w => w[3] === '0:00' && w.slice(4, 19).every(v => !v);
const HEADS = ['דק׳', 'נק׳', 'ריב׳', 'אס׳', 'חט׳', 'חס׳', 'מהשדה', 'שלוש', 'עונשין', 'ר״ה', 'ר״ג', 'איב׳', 'עב׳'];
const cells = w => [w[3], w[4], w[5], w[6], w[7], w[8], w[9] + '/' + w[10], w[11] + '/' + w[12], w[13] + '/' + w[14], w[15], w[16], w[17], w[18]];
/* טבלה עם עמודת שמות קבועה */
export function SplitTable({ head, heads, rows, wide = [] }) {
  return html`<div class="box">
    <div class="names"><div class="r h">${head}</div>${rows.map(r => html`<div key=${r.key} class=${cx('r', r.cls)}>${r.name}</div>`)}</div>
    <div class="cells" tabindex="0" aria-label="טבלה – אפשר לגלול לצד"><div style=${{ width: 'max-content', minWidth: '100%' }}>
      <div class="r h">${heads.map((h, i) => html`<span key=${i} class=${wide.includes(i) ? 'w' : ''}>${h}</span>`)}</div>
      ${rows.map(r => html`<div key=${r.key} class=${cx('r', r.cls)}>${r.note ? html`<span style=${{ width: 'auto', padding: '0 10px', textAlign: 'right' }} class="t-xs mu nowrap">${r.note}</span>` : r.cells.map((v, i) => html`<span key=${i} class=${cx(wide.includes(i) && 'w', i === r.hi && 'st')}>${v}</span>`)}</div>`)}
    </div></div></div>`;
}
export function BoxScore({ g, gs, pf }) {
  const [si, setSi] = useState(0), pl = gs.pl && gs.pl[0] && gs.pl[1] && (gs.pl[0].length || gs.pl[1].length) ? gs.pl : null;
  if (!pl) return pf && pf.length ? html`<div class="glass r-lg pad"><div class="sec-t" style=${{ marginBottom: 6 }}>שחקנים בולטים</div>
    ${pf.map(([n, v], i) => { const m = String(n).match(/^(.*?)\s*\((.*)\)$/); return html`<div key=${i} class="row between t-s" style=${{ padding: '8px 0', borderTop: i ? '1px solid var(--line)' : 0 }}><span><span class="b">${m ? m[1] : n}</span>${m ? html`<span class="t-xs mu"> · ${m[2]}</span>` : null}</span><b class="num">${v}</b></div>`; })}
    <div class="t-xs mu" style=${{ marginTop: 6 }}>הטבלה המלאה זמינה בעמוד המשחק ב-365scores.</div></div>` : html`<div class="glass r-lg pad t-s mu">הסטטיסטיקה האישית של המשחק לא נטענה לאפליקציה.</div>`;
  const ord = ['league', 'wcup'].includes(g.c) ? r => { const num = w => { const v = parseInt(w[1], 10); return isNaN(v) ? 999 : v; }, grp = w => w[2] ? 0 : isDnp(w) ? 2 : 1; return r.slice().sort((a, b) => grp(a) - grp(b) || num(a) - num(b)); } : r => r;
  const ws = ord(pl[si]), sum = i => ws.reduce((t, w) => t + w[i], 0);
  const tot = ['', sum(4), sum(5), sum(6), sum(7), sum(8), sum(9) + '/' + sum(10), sum(11) + '/' + sum(12), sum(13) + '/' + sum(14), sum(15), sum(16), sum(17), sum(18)];
  const rows = [...ws.map((w, i) => ({ key: i, cls: isDnp(w) ? 'mu' : '', hi: 1,
    name: html`${w[2] ? html`<span class="dot" style=${{ width: 6, height: 6 }} title="חמישייה"></span>` : null}<span class="mu t-xxs num" style=${{ minWidth: 16 }}>${w[1] ?? ''}</span><span class="trunc md">${w[0]}</span>`,
    note: isDnp(w) ? 'לא שיחק' : '', cells: isDnp(w) ? [] : cells(w) })), { key: 'tot', cls: 'b', name: 'סה״כ', cells: tot }];
  return html`<div class="glass r-lg pad-s"><div class="sec-t" style=${{ padding: '2px 4px 8px' }}>סטטיסטיקה אישית</div>
    <${Seg} items=${[[0, ME], [1, g.o]]} val=${si} set=${setSi} label="קבוצה"/>
    <div style=${{ marginTop: 8 }}><${SplitTable} key=${si} head="שחקן" heads=${HEADS} rows=${rows} wide=${[6, 7, 8]}/></div>
    <div class="t-xs mu" style=${{ padding: '8px 4px 0' }}><span class="dot" style=${{ width: 6, height: 6, display: 'inline-block', marginLeft: 4 }}></span>חמישייה · ר״ה = ריבאונד התקפה · ר״ג = ריבאונד הגנה · הנתונים מ${srcName(gs)}.</div></div>`;
}
/* מהלך המשחק עם סינון לפי רבע */
export function PlayByPlay({ pbp, g }) {
  const [qf, setQf] = useState(0), qs = useMemo(() => [...new Set(pbp.map(x => x.q))].sort((a, b) => a - b), [pbp]), list = qf ? pbp.filter(x => x.q === qf) : pbp, box = useRef(null);
  useEffect(() => { if (box.current) box.current.scrollTop = 0; }, [qf]);
  const ord = g.h ? [0, 1] : [1, 0];
  return html`<div class="glass lite r-lg" style=${{ overflow: 'hidden' }}>
    <div class="pad-s" style=${{ borderBottom: '1px solid var(--line)' }}>
      <div class="row between" style=${{ padding: '0 4px 8px' }}><span class="sec-t">מהלך המשחק</span><span class="t-xs mu">${g.h ? ME + ' – ' + g.o : g.o + ' – ' + ME}</span></div>
      <div class="chips" style=${{ paddingBottom: 2 }}>${[[0, 'כל המשחק'], ...qs.map(q => [q, PBQ(q)])].map(([k, l]) => html`<button key=${k} onClick=${() => setQf(k)} class=${cx('chip sub', qf === k && 'on')}>${l}</button>`)}</div>
    </div>
    <div ref=${box} class="pbp-win">
      ${list.map((x, i) => { const head = !qf && (i === 0 || list[i - 1].q !== x.q); return html`<${Fragment} key=${i}>${head ? html`<div class="pbp-q">${PBQ(x.q)}</div>` : null}
        <div class=${cx('pbp-row', x.big && 'big', x.side)}><span class="clk">${x.clock}</span>
          <span class="shrink0" style=${{ width: 18 }}>${x.side ? html`<${Crest} n=${x.side === 'j' ? ME : g.o} s=${18}/>` : null}</span>
          <span class=${cx('grow who', !x.side && 'mu')}>${x.text}</span>
          ${x.sc ? html`<span class="num t-xs nowrap">${ord.map((k, j) => html`<${Fragment} key=${k}>${j ? html`<span class="mu"> - </span>` : null}<span class=${(k === 0) === (x.side === 'j') ? 'xb' + (x.side === 'j' ? ' red' : '') : 'mu'}>${x.sc[k]}</span><//>`)}</span>` : null}</div><//>`; })}
      ${!list.length ? html`<div class="t-s mu" style=${{ textAlign: 'center', padding: 18 }}>אין מהלכים ברבע הזה</div>` : null}
    </div></div>`;
}
export { isDnp, Icon };
