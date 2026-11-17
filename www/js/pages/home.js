/* מסך הבית: קרוסלת משחקים (קודם / הבא / שאחריו), מיקום בטבלאות, מובילי הקבוצה וחדשות */
import { html, useState, useEffect, useRef, useLayoutEffect, memo, portal, cx } from '../ui/html.js';
import { Icon } from '../ui/icons.js';
import { useApp, useNow, Crest, Seg, Empty, ExtLink, VideoPop, usePhoto, Photo } from '../ui/kit.js';
import { LiveScore, Pts, LiveQ, LiveSides } from '../ui/score.js';
import { ME, LINKS } from '../data/constants.js';
import { DB, compLabel, won, gsOf } from '../data/store.js';
import { H, D, SEASON, fdLong, dtf } from '../core/env.js';
import { ytId, hlUrl, CLUB, loadLeaders, savedLeaders, LCAT } from '../data/media.js';
import { status } from '../data/refresh.js';
import { shareStory } from '../features/story.js';

/* כמה זמן עבר מהעדכון האחרון של הלייב */
export const fresh = (L, now) => { const s = Math.max(0, Math.round((now - (L.at || now)) / 1000)); return s < 5 ? 'עודכן עכשיו' : s < 60 ? 'עודכן לפני ' + s + ' שנ׳' : 'עודכן לפני ' + Math.round(s / 60) + ' דק׳'; };
const PEND = 4 * H, isPend = (f, now) => !f.r && f.t < now - PEND;
const openExt = u => window.open(u, '_blank', 'noopener');

function Countdown({ t, now, red }) {
  const df = Math.max(0, t - now), u = [['ימים', df / D | 0], ['שעות', (df % D) / H | 0], ['דקות', (df % H) / 6e4 | 0], ['שניות', (df % 6e4) / 1e3 | 0]];
  return html`<div class="cd" aria-label="ספירה לאחור">${u.map(([l, v]) => html`<div key=${l} class=${red ? 'on-red' : 'cd-box'}><div class="v"><span key=${v} class="tick">${String(v).padStart(2, '0')}</span></div><div class=${cx('l', !red && 'mu')}>${l}</div></div>`)}</div>`;
}
/* המשחק בלייב בכרטיס האדום – המידות כמו בגרסה 1.161. הכותרת "הפועל לייב" ממלאת את השטח הפנוי בכרטיס
   (כל הכרטיסים בקרוסלה באותו גובה), כך שאין רווח מת */
function HomeLive({ L, g, intro, now, open, share }) {
  const hm = L.jh ? ME : g.o, aw = L.jh ? g.o : ME, on = L.status === 'live';
  return html`<div class="col grow">
    <div class="row between t-s" style=${{ minHeight: 26 }}>${on ? html`<span class="live-pill">לייב</span>` : html`<span class="b">${L.status === 'end' ? 'סיום' : 'ממתינים לשריקת הפתיחה'}</span>`}<span class="b num">${on ? L.st : ''}${on && L.clock ? html` · <span class="ltr">${L.clock}</span>` : null}</span></div>
    <div class=${cx('lv-title', intro && 'live-in')} aria-label="הפועל לייב"><span>הפועל</span><span class=${cx('live-tag', on && 'on')}><span class="lt-clip" aria-hidden="true"></span>לייב</span></div>
    <${LiveSides} L=${L} hm=${hm} aw=${aw}/>
    <${LiveQ} L=${L} hm=${hm} aw=${aw}/>
    <${WithShare} share=${share}><button onClick=${open} class="gc-btn on-red press"><${Icon} n="cal" s=${16}/>לעמוד המשחק</button><//>
  </div>`;
}
/* כפתור שיתוף לסטורי, משמאל לכפתור הפעולה של הכרטיס */
const ShareBtn = ({ on, cls = 'on-red' }) => html`<button onClick=${on} aria-label="שיתוף לסטורי" class=${'gc-btn press ' + cls} style=${{ width: 52, flexShrink: 0, padding: 0, alignSelf: 'stretch' }}><${Icon} n="share" s=${18}/></button>`;
/* כפתור הפעולה וכפתור השיתוף משמאלו: אותו גובה בדיוק (השיתוף נמתח לגובה של הכפתור שלידו), ורווח של 8px – כמו הרווח בין הכפתורים שמתחת */
const WithShare = ({ share, children, cls, mt = 12 }) => html`<div style=${{ display: 'flex', alignItems: 'stretch', gap: 8, marginTop: mt }}><div class="grow" style=${{ minWidth: 0, display: 'flex' }}>${children}</div><${ShareBtn} on=${share} cls=${cls}/></div>`;
/* משחק שהסתיים, לסטורי: התוצאה והרבעים כמו בכרטיס הלייב */
const finalOf = g => { const e = gsOf(g.id) || {}, q = Array.isArray(e.q) ? e.q : [], hi = p => g.h ? p[0] : p[1], ai = p => g.h ? p[1] : p[0];
  return { status: 'end', jh: !!g.h, hs: g.h ? g.r[0] : g.r[1], as: g.h ? g.r[1] : g.r[0], q: q.map((p, i) => ({ n: i < 4 ? 'Q' + (i + 1) : 'OT' + (i - 3), h: hi(p), a: ai(p) })) }; };
/* כרטיס משחק במסך הבית – המידות והמרווחים כמו בגרסה 1.161 */
function GameCard({ kind, g, now, L }) {
  const A = useApp(), [vid, setVid] = useState(false), red = kind === 'next', hm = g.h ? ME : g.o, aw = g.h ? g.o : ME, res = kind === 'prev' && g.r, w = res && won(g);
  const [intro, setIntro] = useState(false), was = useRef(!!L);
  useEffect(() => { if (L && !was.current) { setIntro(true); const t = setTimeout(() => setIntro(false), 900); was.current = true; return () => clearTimeout(t); } was.current = !!L; }, [!!L]);
  const nm = n => html`<div class="gc-name" style=${{ fontWeight: n === ME ? 800 : 400 }}>${n}</div>`;
  const kicker = kind === 'prev' || kind === 'pend' ? 'המשחק הקודם' : kind === 'after' ? 'המשחק שאחריו' : 'המשחק הבא';
  const btn = red ? 'gc-btn on-red' : 'gc-btn cd-box', sub = red ? '' : 'mu', share = () => res ? shareStory(A.storyUI, g, finalOf(g)) : shareStory(A.storyUI, g, L, kind === 'after' ? 'המשחק שאחריו' : 'המשחק הבא');
  return html`<article class=${cx('gc col', red ? 'glass-red' : 'glass')}>
    ${L ? html`<${HomeLive} L=${L} g=${g} intro=${intro} now=${now} open=${() => A.openGame(g)} share=${share}/>`
    : html`<div class="col grow">
      <div class=${cx('t-s', sub)} style=${{ textAlign: 'center', marginBottom: 12, padding: 0 }}>${kicker} · ${compLabel(g)}</div>
      <div class="row between" style=${{ gap: 8, marginBottom: 12 }}><${Crest} n=${hm} s=${60}/>
        <div class="grow" style=${{ textAlign: 'center' }}>${nm(hm)}${res ? html`<div class="row center num" style=${{ gap: 8, fontSize: 30, fontWeight: 600, margin: '4px 0', lineHeight: 1.2 }}><span>${g.h ? g.r[0] : g.r[1]}</span><span>–</span><span>${g.h ? g.r[1] : g.r[0]}</span></div>` : html`<div class=${cx('t-s ltr', sub)} style=${{ margin: '2px 0' }}>VS</div>`}${nm(aw)}</div>
        <${Crest} n=${aw} s=${60}/></div>
      <div class="t-s" style=${{ textAlign: 'center' }}>${fdLong(g)}</div>
      <div class=${cx('t-s row center', sub)} style=${{ gap: 4, marginBottom: 12 }}><${Icon} n="pin" s=${14}/>${g.v || (g.h ? 'משחק בית' : 'משחק חוץ')}</div>
      ${res ? html`<div class="row center grow" style=${{ padding: '8px 0' }}><span class=${w ? 'green' : 'red'} style=${{ fontSize: 30, fontWeight: 800, lineHeight: '31px' }}>${w ? 'ניצחון' : 'הפסד'}</span></div>
          <button onClick=${() => ytId(g) ? setVid(true) : openExt(hlUrl(g))} class="gc-btn btn-red press" style=${{ marginTop: 12 }}><${Icon} n="play" s=${16}/>לתקציר המשחק</button>`
      : kind === 'pend' ? html`<div class="col center grow" style=${{ alignItems: 'center', padding: '8px 0' }}><span style=${{ fontSize: 24, fontWeight: 800 }}>ממתין לתוצאה</span><span class="t-s mu" style=${{ marginTop: 4 }}>התוצאה תופיע כאן כשתתעדכן מהמקור הרשמי</span></div>`
      : red && g.t <= now ? html`<div class="on-red" style=${{ borderRadius: 12, padding: '16px 12px', textAlign: 'center', marginTop: 'auto' }}><div style=${{ fontSize: 20, fontWeight: 800 }}>המשחק התחיל</div><div class="t-s" style=${{ marginTop: 4 }}>התוצאה בלייב תופיע כאן ברגע שתתקבל</div></div>`
      : html`<div style=${{ marginTop: 'auto' }}><${Countdown} t=${g.t} now=${now} red=${red}/></div>
          ${(() => { const rb = html`<button onClick=${() => A.togRem(g)} class=${cx('gc-btn press', A.rem[g.id] ? (red ? 'btn-white' : 'btn-red') : btn)} ><${Icon} n=${A.rem[g.id] ? 'bellOn' : 'bell'} s=${16}/>${A.rem[g.id] ? 'תזכורת פעילה – לחצו לביטול' : 'הגדר תזכורת למשחק'}</button>`; return html`<${WithShare} share=${share} cls=${red ? 'on-red' : 'cd-box'}>${rb}<//>`; })()}`}
      ${(() => { const wsh = (red && g.t <= now && !res && kind !== 'pend') || res, gb = html`<button onClick=${() => A.openGame(g)} class=${cx(btn, 'press')} style=${wsh ? null : { marginTop: 8 }}><${Icon} n="cal" s=${16}/>לעמוד המשחק</button>`; return wsh ? html`<${WithShare} share=${share} cls=${red ? 'on-red' : 'cd-box'} mt=${res ? 8 : 12}>${gb}<//>` : gb; })()}
    </div>`}
    ${vid ? html`<${VideoPop} id=${ytId(g)} title=${'תקציר: ' + (g.h ? ME + ' – ' + g.o : g.o + ' – ' + ME)} onClose=${() => setVid(false)}/>` : null}
  </article>`;
}
/* הכרטיס שמוצג בקרוסלה: נשמר כשעוברים לעמוד משחק, כדי ש"חזרה" תחזיר לאותו כרטיס. רענון מחזיר לכרטיס הראשי */
const CAR = { k: null, ld: false, n: 0 };
/* רענון במשיכה: חזרה לכרטיס הראשי (המשחק הבא / בלייב) */
export const carHome = () => { CAR.k = null; CAR.n++; };
function Carousel({ items, start, kinds = [], home = start, reset = 0 }) {
  const r = useRef(null), [i, setI] = useState(start), iR = useRef(start); iR.current = i;
  useEffect(() => { CAR.k = kinds[i] || null; }, [i]);
  const first = useRef(true);
  useEffect(() => { if (first.current) { first.current = false; return; } to(home, 1); }, [reset]);
  const mid = e => { const b = e.getBoundingClientRect(); return b.left + b.width / 2; };
  const to = (k, smooth) => { const el = r.current, c = el && el.children[k]; if (c) el.scrollBy({ left: mid(c) - mid(el), behavior: smooth ? 'smooth' : 'auto' }); };
  useLayoutEffect(() => { to(start); }, []);
  const raf = useRef(0);
  const onScroll = () => { if (raf.current) return; raf.current = requestAnimationFrame(() => { raf.current = 0; const el = r.current; if (!el) return; const x = mid(el); let b = 0, bd = 1e9; [...el.children].forEach((c, k) => { const d = Math.abs(mid(c) - x); if (d < bd) { bd = d; b = k; } }); if (b !== iR.current) setI(b); }); };
  return html`<div><div ref=${r} class="car" onScroll=${onScroll}>${items}</div>
    ${items.length > 1 ? html`<div class="dots">${items.map((_, k) => html`<button key=${k} aria-label=${'כרטיס ' + (k + 1)} class=${k === i ? 'on' : ''} onClick=${() => to(k, 1)}></button>`)}</div>` : null}</div>`;
}
const Leaders = memo(function Leaders({ v }) {
  const A = useApp(), [d, setD] = useState(savedLeaders), [ci, setCi] = useState(0);
  useEffect(() => { let on = true; loadLeaders().then(x => { if (on && x) setD(x); }).catch(() => { }); return () => { on = false; }; }, [v]);
  if (!d || !d.comps.length) return null;
  const c = d.comps[Math.min(ci, d.comps.length - 1)], open = a => { const p = DB.PL.find(x => x[7] === a.id); if (p) A.openPlayer(p); };
  const face = a => { const p = DB.PL.find(x => x[7] === a.id); return p && p[6] ? html`<${Photo} u=${p[6]} k=${p[9]} cls="abs-fill"/>` : html`<img src=${'https://imagecache.365scores.com/image/upload/f_png,w_160,h_200,c_limit,q_auto:eco,d_Athletes:default.png/v' + a.iv + '/Athletes/' + a.id} alt="" loading="lazy" referrerPolicy="no-referrer" class="ph abs-fill"/>`; };
  return html`<section class="glass r-lg pad"><div class="row between" style=${{ marginBottom: 10 }}><h2 class="sec-t" style=${{ margin: 0 }}>מובילי הפועל</h2><span class="t-xs mu">ממוצע למשחק · 365Scores</span></div>
    ${d.comps.length > 1 ? html`<${Seg} items=${d.comps.map((x, i) => [i, x.name])} val=${ci} set=${setCi} cls="" label="מפעל"/>` : null}
    <div key=${c.id} class="grid3 fade-in" style=${{ marginTop: 12 }}>${LCAT.map(([tid, l]) => { const rr = c.cat[tid] || [], a = rr[0]; return html`<div key=${tid} style=${{ minWidth: 0, textAlign: 'center' }}>
      <div class="t-xs mu b" style=${{ marginBottom: 6 }}>${l}</div>
      ${a ? html`<button onClick=${() => open(a)} class="pcard press" style=${{ width: '100%' }}>${face(a)}<div class="cap"><div class="num" style=${{ fontSize: 24, fontWeight: 800, lineHeight: 1 }}>${a.v}</div><div class="t-xs b trunc">${a.n}</div></div></button>` : html`<div class="mu t-s" style=${{ padding: 20 }}>—</div>`}
      ${rr.slice(1).map((x, i) => html`<button key=${x.id} onClick=${() => open(x)} class="row between t-xs tap" style=${{ width: '100%', gap: 4 }}><span class="trunc"><span class="mu">${i + 2}.</span> ${x.n}</span><b class="num">${x.v}</b></button>`)}
    </div>`; })}</div></section>`;
});
function News() {
  const [all, setAll] = useState(false);
  if (!DB.NEWS.length) return null;
  const L = all ? DB.NEWS : DB.NEWS.slice(0, 4), more = DB.NEWS.length - 4, dd = d => { const m = /^\d{4}-(\d\d)-(\d\d)/.exec(d || ''); return m ? (+m[2]) + '.' + (+m[1]) : ''; };
  return html`<section class="glass r-lg" style=${{ overflow: 'hidden' }}><div class="row between pad-x" style=${{ paddingTop: 14, paddingBottom: 4 }}><h2 class="sec-t" style=${{ margin: 0 }}>חדשות המועדון</h2><${ExtLink} href=${CLUB + 'archive'} cls="link t-xs tap">לכל הכתבות<//></div>
    ${L.map(n => html`<a key=${n.u || n.t} href=${n.u || undefined} target="_blank" rel="noopener noreferrer" class="li press">${n.i ? html`<${NewsImg} src=${n.i}/>` : null}<div class="grow"><div class="t-s b clamp3" style=${{ lineHeight: 1.4 }}>${n.t}</div>${n.d ? html`<div class="t-xs mu" style=${{ marginTop: 3 }}>${dd(n.d)}</div>` : null}</div></a>`)}
    ${more > 0 ? html`<button onClick=${() => setAll(!all)} class="li press red b t-s" style=${{ width: '100%', justifyContent: 'center' }}>${all ? 'הצג פחות' : 'עוד כתבות (' + more + ')'}</button>` : null}</section>`;
}
function NewsImg({ src }) { const [bad, setBad] = useState(false); return bad ? null : html`<img src=${src} alt="" loading="lazy" referrerPolicy="no-referrer" onError=${() => setBad(true)} style=${{ width: 92, height: 62, borderRadius: 12, objectFit: 'cover', flexShrink: 0, background: 'var(--faint)' }}/>`; }

export default function Home() {
  const A = useApp(), now = useNow();
  const up = DB.F.filter(f => !f.r && !isPend(f, now)).sort((a, b) => a.t - b.t), nx = up[0], ls = DB.F.filter(f => f.r).sort((a, b) => b.t - a.t)[0], pd = DB.F.filter(f => isPend(f, now)).sort((a, b) => b.t - a.t)[0];
  const L = A.live && nx && A.live.app === nx.id && (A.live.status !== 'pre' || now >= nx.t) && A.live.status !== 'end' ? A.live : null;
  const rk = DB.LG.findIndex(r => r.n === ME), me = DB.LG[rk], eg = DB.EC.find(([, t]) => t.some(r => r[0] === ME)), er = eg ? eg[1].findIndex(r => r[0] === ME) : -1, em = er >= 0 ? eg[1][er] : null;
  if (!DB.ok) return html`<div class="stack"><div class="sk" style=${{ height: 380, borderRadius: 24 }}></div><div class="sk" style=${{ height: 90 }}></div></div>`;
  if (!nx && !ls) return html`<${Empty} title=${'עונת ' + SEASON} sub="משחקי העונה עוד לא נטענו לאפליקציה."><${ExtLink} href=${LINKS.club}>לאתר הרשמי של הקבוצה<//><//>`;
  const pv = pd && (!ls || pd.t > ls.t) ? ['pend', pd] : ls && ['prev', ls];
  const cards = [pv, nx && ['next', nx], up[1] && ['after', up[1]]].filter(Boolean);
  /* הכרטיס הראשי: המשחק הבא / בלייב. "חזרה" מעמוד משחק – לכרטיס שהיה מוצג; תחילת רענון – חזרה לכרטיס הראשי */
  const kinds = cards.map(c => c[0]), main = Math.max(0, kinds.indexOf('next')), back = A.returning() && CAR.k ? kinds.indexOf(CAR.k) : -1;
  return html`<div class="stack stg">
    <${Carousel} key=${nx ? nx.id : 'x'} start=${back >= 0 ? back : main} home=${main} kinds=${kinds} reset=${CAR.n} items=${cards.map(([k, g]) => html`<${GameCard} key=${k} kind=${k} g=${g} now=${now} L=${k === 'next' ? L : null}/>`)}/>
    ${me || em ? html`<div class=${me && em ? 'grid2' : ''}>
      ${me ? html`<button onClick=${() => A.go('stand', 'league')} class="glass r-lg pad-s press" style=${{ textAlign: 'center' }}><div class="t-xs mu">ליגת ווינר סל</div><div class="t-xs mu num">${me.w} נצ׳ · ${me.l} הפ׳ · ${me.p} נק׳</div><div class="t-xl num" style=${{ marginTop: 2 }}>מקום ${rk + 1}</div></button>` : null}
      ${em ? html`<button onClick=${() => A.go('stand', 'euro')} class="glass r-lg pad-s press" style=${{ textAlign: 'center' }}><div class="t-xs mu">יורוקאפ · ${eg[0]}</div><div class="t-xs mu num">${em[1]} נצ׳ · ${em[2]} הפ׳</div><div class="t-xl num" style=${{ marginTop: 2 }}>מקום ${er + 1}</div></button>` : null}</div>` : null}
    <div class="grid-md2 stack"><${Leaders} v=${status.at}/><${News}/></div>
  </div>`;
}
