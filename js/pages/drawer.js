/* מרכז ההתראות, ומסך בדיקת ההתראות (3 לחיצות על הכותרת) */
import { html, useState, useEffect, useRef, Fragment, cx, portal } from '../ui/html.js';
import { Icon } from '../ui/icons.js';
import { useApp, FullPage } from '../ui/kit.js';
import { ME } from '../data/constants.js';
import { DB, compLabel, crestSrcs } from '../data/store.js';
import { plug, ago, dtf } from '../core/env.js';
import { NT, deviceNotify, askPerm, nbMsg } from '../features/notify.js';
import { NbPick } from './settings.js';

/* החלקה הצידה מוחקת התראה */
function NoteRow({ n, i, out, onGone }) {
  const r = useRef(null), st = useRef(null), [gone, setGone] = useState(0);
  const set = (x, t) => { const el = r.current; if (!el) return; el.style.transition = t ? 'transform .25s var(--ease),opacity .25s var(--ease)' : 'none'; el.style.transform = x ? 'translateX(' + x + 'px)' : ''; el.style.opacity = x ? String(Math.max(0, 1 - Math.abs(x) / 260)) : ''; };
  const down = e => { if (out || gone) return; st.current = { x: e.clientX, y: e.clientY, dx: 0, on: false, id: e.pointerId }; };
  const move = e => { const s = st.current; if (!s) return; const dx = e.clientX - s.x, dy = e.clientY - s.y; if (!s.on) { if (Math.abs(dy) > 10 && Math.abs(dy) > Math.abs(dx)) { st.current = null; return; } if (Math.abs(dx) > 10) { s.on = true; try { r.current.setPointerCapture(s.id); } catch (_) { } } } if (s.on) { s.dx = dx; set(dx, false); } };
  const up = () => { const s = st.current; st.current = null; if (!s || !s.on) return; if (Math.abs(s.dx) > 90) { const w = (r.current ? r.current.offsetWidth : 360) + 40; set(s.dx > 0 ? w : -w, true); setGone(1); setTimeout(onGone, 260); } else set(0, true); };
  return html`<div ref=${r} onPointerDown=${down} onPointerMove=${move} onPointerUp=${up} onPointerCancel=${up} style=${{ touchAction: 'pan-y', animation: out ? `noteOut .26s var(--ease) ${i * 40}ms both` : undefined, opacity: n.r ? .65 : 1 }} class="glass r-md pad-s row t-s" >
    ${!n.r ? html`<span class="dot" style=${{ alignSelf: 'flex-start', marginTop: 7 }}></span>` : null}<div class="grow"><div>${n.t}</div><div class="t-xs mu">${n.ts ? ago(n.ts) : ''}</div></div></div>`;
}
function NativeState({ P }) {
  const [x, setX] = useState(null);
  useEffect(() => { const load = () => P.state().then(setX).catch(e => setX({ error: String(e) })); load(); const i = setInterval(load, 3000); return () => clearInterval(i); }, []);
  if (!x) return null;
  const t = v => v ? dtf('he-IL', { timeZone: 'Asia/Jerusalem', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(+v).replace(',', '') : '—';
  const why = x.error ? x.error : !x.notif ? 'ההתראות של האפליקציה חסומות בהגדרות המכשיר' : !x.on ? 'כרטיס התוצאה כבוי בהגדרות האפליקציה' : x.channel === 0 ? 'הערוץ "תוצאה בלייב" כבוי בהגדרות ההתראות של המכשיר' : !x.games ? 'אין משחקים קרובים ברשימה של הטלפון' : x.current ? (x.shown ? 'פועל – הכרטיס מוצג' : 'משחק עכשיו, אבל הכרטיס לא מוצג') : x.done && x.done === x.next ? 'הכרטיס הוסר במשחק הזה – ייפתח שוב כשפותחים את האפליקציה' : 'אין משחק עכשיו (מופעל 10 דקות לפני שריקת הפתיחה)';
  return html`<div class="glass r-lg pad-s"><div class="b t-s">מצב כרטיס התוצאה בטלפון</div><div class="t-s">${why}</div>
    <div class="t-xs mu">משחק עכשיו: ${t(x.current)} · המשחק הבא: ${t(x.next)} · ברשימה: ${x.games} · מוצג: ${x.shown ? 'כן' : 'לא'} · ערוץ: ${x.channel} · Now Bar: ${x.nb} · רקע: ${x.bg ? 'כן' : 'לא'}</div></div>`;
}
function NotifTest({ onClose }) {
  const A = useApp(), P = plug('LiveActivity'), f = DB.F.filter(x => !x.r).sort((a, b) => a.t - b.t)[0] || DB.F[DB.F.length - 1] || { id: 0, h: 1, o: 'יריבה', c: 'league', t: Date.now() };
  const ME365 = 'https://imagecache.365scores.com/image/upload/f_png,w_96,h_96,c_limit,q_auto:eco,dpr_2,d_Competitors:default1.png/v8/Competitors/634';
  const nat = (kind, r, extra) => P && P.testAlert ? P.testAlert({ kind, ...extra, comp: compLabel(f), t: f.t || 0, meHome: !!f.h, home: { name: NT.home(f), score: f.h ? r[0] : r[1] }, away: { name: NT.away(f), score: f.h ? r[1] : r[0] } }) : null;
  const game = (m, kind, r, extra) => [m, () => nat(kind, r, extra) || deviceNotify(NT.line(m), m)];
  const items = [
    ['לפני המשחק', [NT.hour(f), () => deviceNotify(NT.line(NT.hour(f)), NT.hour(f))], 'תזכורת שעה לפני שריקת הפתיחה'],
    ['במהלך המשחק', game(NT.start(f), 'start', [0, 0]), 'תחילת המשחק'],
    [null, game(NT.quarter(f, [22, 18], 1), 'quarter', [22, 18], { q: 1 }), 'סיום רבע ראשון'],
    [null, game(NT.quarter(f, [45, 40], 2), 'quarter', [45, 40], { q: 2 }), 'מחצית'],
    [null, game(NT.quarter(f, [66, 61], 3), 'quarter', [66, 61], { q: 3 }), 'סיום רבע שלישי'],
    [null, game(NT.fin(f, [88, 80], false), 'final', [88, 80], { ot: false }), 'סיום המשחק – ניצחון'],
    [null, game(NT.fin(f, [80, 88], false), 'final', [80, 88], { ot: false }), 'סיום המשחק – הפסד'],
    [null, game(NT.quarter(f, [80, 80], 4), 'quarter', [80, 80], { q: 4 }), 'סיום רבע רביעי (משחק עם הארכה)'],
    [null, game(NT.quarter(f, [90, 90], 5), 'quarter', [90, 90], { q: 5 }), 'סיום הארכה'],
    [null, game(NT.fin(f, [101, 97], true), 'final', [101, 97], { ot: true }), 'סיום אחרי הארכה – ניצחון'],
    [null, game(NT.fin(f, [97, 101], true), 'final', [97, 101], { ot: true }), 'סיום אחרי הארכה – הפסד'],
    [null, [{ title: 'כרטיס התוצאה בלייב', l2: 'הסמלים, התוצאה, הרבע, השעון ומהלך המשחק (נעלם אחרי 20 שניות)' }, () => {
      if (!P || !P.demo) return A.toast('כרטיס התוצאה בלייב קיים רק באפליקציית האנדרואיד');
      P.demo({ t: f.t || 0, comp: compLabel(f), status: 'רבע 3', clock: '04:12', play: '04:12 · ' + ME + ' – שלשה', home: { name: NT.home(f), score: 66, img: f.h ? ME365 : crestSrcs(f.o)[0] || null }, away: { name: NT.away(f), score: 61, img: f.h ? crestSrcs(f.o)[0] || null : ME365 }, check: true }).then(x => { if (nbMsg(x, A.pf)) A.toast(nbMsg(x, A.pf)); }).catch(() => { });
      setTimeout(() => P.demo({}).catch(() => { }), 2e4);
    }], 'כרטיס התוצאה בלייב'],
    ['כללי', [{ title: ME, l2: 'ההתראות הופעלו – עדכונים יופיעו גם במגש ההתראות של המכשיר' }, () => deviceNotify('ההתראות הופעלו – עדכונים יופיעו גם במגש ההתראות של המכשיר')], 'הפעלת ההתראות'],
    [null, [{ title: ME, l2: 'התראת בדיקה: ההתראות של הפועל ירושלים פועלות' }, () => deviceNotify('התראת בדיקה: ההתראות של הפועל ירושלים פועלות')], 'התראת בדיקה'],
  ];
  const send = (go, l) => { const run = () => { try { const x = go(); if (x && x.catch) x.catch(() => { }); } catch (e) { } A.toast('נשלחה: ' + l); }; if (A.perm === 'default') askPerm().then(p => { A.setPerm(p); run(); }); else run(); };
  return html`<${FullPage} title="בדיקת התראות" onClose=${onClose}><div class="stack-s">
    <div class="t-xs mu">כל ההתראות של האפליקציה בנוסח המדויק. "שליחה" שולחת עכשיו למכשיר. הנתונים מהמשחק הבא (${NT.match(f)}); התוצאות לדוגמה.</div>
    ${P && P.state ? html`<${NativeState} P=${P}/>` : null}
    ${P && P.demo ? html`<div class="glass r-lg pad-s stack-s"><div class="b t-s">התוצאה בלייב</div><div class="t-xs mu">כרטיס התוצאה של האפליקציה, או ב-Now Bar (אנדרואיד 16 ומעלה). זו אותה בחירה כמו בהגדרות.</div><${NbPick} pf=${A.pf} setPf=${A.setPf}/></div>` : null}
    ${items.map(([sec, [m, go], l], i) => html`<${Fragment} key=${i}>${sec ? html`<div class="sec-t" style=${{ marginTop: 10 }}>${sec}</div>` : null}
      <div class="glass r-md pad-s row"><div class="grow"><div class="t-xs mu">${l}</div><div class="b t-s">${m.title}</div>${m.l1 ? html`<div class="t-xs mu">${m.l1}</div>` : null}<div class="t-xs mu">${m.l2}</div></div>
      <button onClick=${() => send(go, l)} class="btn btn-red btn-s press">שליחה</button></div><//>`)}
  </div><//>`;
}
export default function Drawer({ onClose }) {
  const A = useApp(), [clr, setClr] = useState(''), [test, setTest] = useState(false), taps = useRef([]), list = A.notes.slice(0, 30);
  const tapTitle = () => { const t = Date.now(); taps.current = taps.current.filter(x => t - x < 700).concat(t); if (taps.current.length < 3) return; taps.current = []; setTest(true); };
  const clearAll = () => { if (!A.notes.length || clr) return; setClr('out'); setTimeout(() => { A.setNotes([]); setClr('done'); }, Math.min(list.length, 8) * 40 + 280); };
  useEffect(() => { const k = e => { if (e.key === 'Escape') onClose(); }; document.addEventListener('keydown', k); return () => document.removeEventListener('keydown', k); }, []);
  return portal(html`<${Fragment}><div class="scrim" onClick=${onClose}></div>
    <div role="dialog" aria-modal="true" aria-label="מרכז התראות" class="glass-3" style=${{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 51, maxHeight: '88vh', overflowY: 'auto', borderRadius: '0 0 26px 26px', paddingTop: 'calc(var(--sat) + 10px)', animation: 'dropIn .3s var(--ease) both' }}>
      <div class="wrap stack-s" style=${{ paddingBottom: 16 }}>
        <div class="row between"><h2 onClick=${tapTitle} class="t-l" style=${{ margin: 0, userSelect: 'none' }}>מרכז התראות</h2><button onClick=${onClose} aria-label="סגירה" class="icon-btn press"><${Icon} n="x" s=${22}/></button></div>
        ${A.notes.length && clr !== 'done' ? html`<button onClick=${clearAll} class="btn btn-glass btn-s press" style=${{ width: '100%' }}><${Icon} n="check" s=${16}/>סמן הכל כנקרא</button>` : null}
        ${list.length && !clr ? html`<div class="t-xs mu">החליקו התראה הצידה כדי למחוק אותה</div>` : null}
        ${clr === 'done' || (!list.length && !clr) ? html`<div class="row center t-s mu fade-in" style=${{ padding: '20px 0', gap: 8 }}><span class="ic-soft" style=${{ background: 'rgb(19 128 74 / .12)', color: 'var(--green)', borderRadius: 99 }}><${Icon} n="check" s=${18}/></span>${clr === 'done' ? 'כל ההתראות נקראו' : 'אין התראות חדשות'}</div>` : null}
        ${list.map((n, i) => html`<${NoteRow} key=${n.id} n=${n} i=${i} out=${clr === 'out'} onGone=${() => A.setNotes(x => x.filter(y => y.id !== n.id))}/>`)}
        <button onClick=${() => { onClose(); A.openSettings(); }} class="btn btn-glass press" style=${{ width: '100%' }}><${Icon} n="cog" s=${16}/>הגדרות התראות</button>
      </div></div>
    ${test ? html`<${NotifTest} onClose=${() => setTest(false)}/>` : null}<//>`);
}
