/* מסך הבית: קרוסלת משחקים (קודם / הבא / שאחריו), מיקום בטבלאות, מובילי הקבוצה וחדשות */
import { html, useState, useEffect, useRef, useLayoutEffect, memo, portal, cx } from '../ui/html.js';
import { Icon } from '../ui/icons.js';
import { useApp, useNow, Crest, Seg, Empty, ExtLink, VideoPop, usePhoto, Photo } from '../ui/kit.js';
import { LiveScore } from '../ui/score.js';
import { ME, LINKS } from '../data/constants.js';
import { DB, compLabel, won } from '../data/store.js';
import { H, D, SEASON, fdLong, dtf } from '../core/env.js';
import { ytId, hlUrl, CLUB, loadLeaders, savedLeaders, LCAT } from '../data/media.js';
import { status } from '../data/refresh.js';
import { shareStory } from '../features/story.js';

/* כמה זמן עבר מהעדכון האחרון של הלייב */
export const fresh = (L, now) => { const s = Math.max(0, Math.round((now - (L.at || now)) / 1000)); return s < 5 ? 'עודכן עכשיו' : s < 60 ? 'עודכן לפני ' + s + ' שנ׳' : 'עודכן לפני ' + Math.round(s / 60) + ' דק׳'; };
const PEND = 4 * H, isPend = (f, now) => !f.r && f.t < now - PEND;
const openExt = u => window.open(u, '_blank', 'noopener');

function Countdown({ t, now }) {
  const df = Math.max(0, t - now), u = [['ימים', df / D | 0], ['שעות', (df % D) / H | 0], ['דקות', (df % H) / 6e4 | 0], ['שניות', (df % 6e4) / 1e3 | 0]];
  return html`<div class="cd" aria-label="ספירה לאחור">${u.reverse().map(([l, v]) => html`<div key=${l} class="on-red"><div class="v"><span key=${v} class="tick">${String(v).padStart(2, '0')}</span></div><div class="l">${l}</div></div>`)}</div>`;
}
function GameCard({ kind, g, now, L }) {
  const A = useApp(), [vid, setVid] = useState(false), red = kind === 'next', hm = g.h ? ME : g.o, aw = g.h ? g.o : ME, res = kind === 'prev' && g.r, w = res && won(g);
  /* כשהמשחק עולה לאוויר בזמן שהמסך פתוח: הספירה יוצאת והתוצאה בלייב נכנסת */
  const [intro, setIntro] = useState(false), was = useRef(!!L);
  useEffect(() => { if (L && !was.current) { setIntro(true); const t = setTimeout(() => setIntro(false), 900); was.current = true; return () => clearTimeout(t); } was.current = !!L; }, [!!L]);
  const nm = n => html`<div class=${cx('t-l', n === ME ? 'xb' : 'md')} style=${{ lineHeight: 1.2 }}>${n}</div>`;
  const kicker = kind === 'prev' || kind === 'pend' ? 'המשחק הקודם' : kind === 'after' ? 'המשחק שאחריו' : 'המשחק הבא';
  const btn = red ? 'btn on-red' : 'btn btn-glass';
  return html`<article class=${cx('r-lg pad col', red ? 'glass-red' : 'glass')} style=${{ minHeight: '100%', position: 'relative' }}>
    ${red ? html`<button onClick=${() => shareStory(A.storyUI, g, L)} aria-label="שיתוף לסטורי" class="icon-btn sm on-red press" style=${{ position: 'absolute', top: 12, left: 12, zIndex: 2 }}><${Icon} n="share" s=${17}/></button>` : null}
    ${L ? html`<div class=${intro ? 'live-in' : ''}><div class="t-xs" style=${{ marginBottom: 10, paddingLeft: 40 }}>${compLabel(g)}</div><${LiveScore} L=${L} g=${g}/>
      <button onClick=${() => A.openGame(g)} class="btn on-red press" style=${{ width: '100%', marginTop: 12 }}><${Icon} n="cal" s=${16}/>לעמוד המשחק</button>
      <div class="t-xs" style=${{ textAlign: 'center', marginTop: 8 }}>${fresh(L, now)}</div></div>`
    : html`<div class="col grow">
      <div class=${cx('t-xs', red ? '' : 'mu')} style=${{ textAlign: 'center', marginBottom: 12, padding: '0 36px' }}><b>${kicker}</b> · ${compLabel(g)}</div>
      <div class="row" style=${{ gap: 8, marginBottom: 12 }}><${Crest} n=${hm} s=${58}/>
        <div class="grow" style=${{ textAlign: 'center' }}>${nm(hm)}${res ? html`<div class="row center num" style=${{ gap: 8, fontSize: 32, fontWeight: 800, margin: '4px 0' }}><span>${g.h ? g.r[0] : g.r[1]}</span><span style=${{ opacity: .5 }}>–</span><span>${g.h ? g.r[1] : g.r[0]}</span></div>` : html`<div class="t-xs b" style=${{ opacity: .7, margin: '3px 0' }}>VS</div>`}${nm(aw)}</div>
        <${Crest} n=${aw} s=${58}/></div>
      <div class="t-s" style=${{ textAlign: 'center' }}>${fdLong(g)}</div>
      <div class=${cx('t-s row center', red ? '' : 'mu')} style=${{ gap: 4, marginBottom: 14,  }}><${Icon} n="pin" s=${14}/>${g.v || (g.h ? 'משחק בית' : 'משחק חוץ')}</div>
      ${res ? html`<div class="row center grow" style=${{ padding: '4px 0 6px' }}><span class=${cx('t-xl', w ? 'green' : 'red')}>${w ? 'ניצחון' : 'הפסד'}</span></div>
          <button onClick=${() => ytId(g) ? setVid(true) : openExt(hlUrl(g))} class="btn btn-red press" style=${{ width: '100%' }}><${Icon} n="play" s=${15}/>לתקציר המשחק</button>`
      : kind === 'pend' ? html`<div class="col center grow" style=${{ alignItems: 'center', padding: '6px 0' }}><span class="t-l xb">ממתין לתוצאה</span><span class="t-s mu">התוצאה תופיע כאן כשתתעדכן מהמקור הרשמי</span></div>`
      : red && g.t <= now ? html`<div class="on-red r-md" style=${{ padding: 14, textAlign: 'center' }}><div class="t-l xb">המשחק התחיל</div><div class="t-s" style=${{ }}>התוצאה בלייב תופיע כאן ברגע שתתקבל</div></div>`
      : html`${red ? html`<${Countdown} t=${g.t} now=${now}/>` : null}
          <button onClick=${() => A.togRem(g)} class=${cx('btn press', A.rem[g.id] ? (red ? 'btn-white' : 'btn-red') : (red ? 'on-red' : 'btn-glass'))} style=${{ width: '100%', marginTop: red ? 12 : 'auto' }}><${Icon} n=${A.rem[g.id] ? 'bellOn' : 'bell'} s=${16}/>${A.rem[g.id] ? 'תזכורת פעילה – לחצו לביטול' : 'תזכורת שעה לפני המשחק'}</button>`}
      <button onClick=${() => A.openGame(g)} class=${cx(btn, 'press')} style=${{ width: '100%', marginTop: 8 }}><${Icon} n="cal" s=${16}/>לעמוד המשחק</button>
    </div>`}
    ${vid ? html`<${VideoPop} id=${ytId(g)} title=${'תקציר: ' + (g.h ? ME + ' – ' + g.o : g.o + ' – ' + ME)} onClose=${() => setVid(false)}/>` : null}
  </article>`;
}
function Carousel({ items, start }) {
  const r = useRef(null), [i, setI] = useState(start), iR = useRef(start); iR.current = i;
  const mid = e => { const b = e.getBoundingClientRect(); return b.left + b.width / 2; };
  const to = (k, smooth) => { const el = r.current, c = el && el.children[k]; if (c) el.scrollBy({ left: mid(c) - mid(el), behavior: smooth ? 'smooth' : 'auto' }); };
  useLayoutEffect(() => { to(start); }, []);
  const raf = useRef(0);
  const onScroll = () => { if (raf.current) return; raf.current = requestAnimationFrame(() => { raf.current = 0; const el = r.current; if (!el) return; const x = mid(el); let b = 0, bd = 1e9; [...el.children].forEach((c, k) => { const d = Math.abs(mid(c) - x); if (d < bd) { bd = d; b = k; } }); if (b !== iR.current) setI(b); }); };
  return html`<div><div ref=${r} class="car" onScroll=${onScroll}>${items}</div>
    ${items.length > 1 ? html`<div class="dots">${items.map((_, k) => html`<button key=${k} aria-label=${'כרטיס ' + (k + 1)} class=${k === i ? 'on' : ''} onClick=${() => to(k, 1)}></button>`)}</div>` : null}</div>`;
}
function HomeBg() {
  const [src, err] = usePhoto(DB.TP, 'team');
  return src ? portal(html`<div class="home-bg fade-in" aria-hidden="true"><img src=${src} alt="" referrerPolicy="no-referrer" onError=${err}/></div>`) : null;
}
const Leaders = memo(function Leaders({ v }) {
  const A = useApp(), [d, setD] = useState(savedLeaders), [ci, setCi] = useState(0);
  useEffect(() => { let on = true; loadLeaders().then(x => { if (on && x) setD(x); }).catch(() => { }); return () => { on = false; }; }, [v]);
  if (!d || !d.comps.length) return null;
  const c = d.comps[Math.min(ci, d.comps.length - 1)], open = a => { const p = DB.PL.find(x => x[7] === a.id); if (p) A.openPlayer(p); };
  const face = a => { const p = DB.PL.find(x => x[7] === a.id); return p && p[6] ? html`<${Photo} u=${p[6]} k=${p[9]} cls="abs-fill"/>` : html`<img src=${'https://imagecache.365scores.com/image/upload/f_png,w_160,h_200,c_limit,q_auto:eco,d_Athletes:default.png/v' + a.iv + '/Athletes/' + a.id} alt="" loading="lazy" referrerPolicy="no-referrer" class="ph abs-fill"/>`; };
  return html`<section class="glass r-lg pad"><div class="row between" style=${{ marginBottom: 10 }}><h2 class="sec-t" style=${{ margin: 0 }}>מובילי הפועל</h2><span class="t-xs mu">ממוצע למשחק · 365scores</span></div>
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
const stamp = d => dtf('he-IL', { timeZone: 'Asia/Jerusalem', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(d).replace(',', '');

export default function Home() {
  const A = useApp(), now = useNow();
  const up = DB.F.filter(f => !f.r && !isPend(f, now)).sort((a, b) => a.t - b.t), nx = up[0], ls = DB.F.filter(f => f.r).sort((a, b) => b.t - a.t)[0], pd = DB.F.filter(f => isPend(f, now)).sort((a, b) => b.t - a.t)[0];
  const L = A.live && nx && A.live.app === nx.id && (A.live.status !== 'pre' || now >= nx.t) && A.live.status !== 'end' ? A.live : null;
  const rk = DB.LG.findIndex(r => r.n === ME), me = DB.LG[rk], eg = DB.EC.find(([, t]) => t.some(r => r[0] === ME)), er = eg ? eg[1].findIndex(r => r[0] === ME) : -1, em = er >= 0 ? eg[1][er] : null;
  if (!DB.ok) return html`<div class="stack"><div class="sk" style=${{ height: 380, borderRadius: 24 }}></div><div class="sk" style=${{ height: 90 }}></div></div>`;
  if (!nx && !ls) return html`<${Empty} title=${'עונת ' + SEASON} sub="משחקי העונה עוד לא נטענו לאפליקציה."><${ExtLink} href=${LINKS.club}>לאתר הרשמי של הקבוצה<//><//>`;
  const pv = pd && (!ls || pd.t > ls.t) ? ['pend', pd] : ls && ['prev', ls];
  const cards = [pv, nx && ['next', nx], up[1] && ['after', up[1]]].filter(Boolean);
  return html`<div class="stack stg"><${HomeBg}/>
    <${Carousel} key=${nx ? nx.id : 'x'} start=${pv && nx ? 1 : 0} items=${cards.map(([k, g]) => html`<${GameCard} key=${k} kind=${k} g=${g} now=${now} L=${k === 'next' ? L : null}/>`)}/>
    <div class="row between t-xs">
      <span class="glass-2 r-full mu" style=${{ padding: '4px 12px' }}>${status.loading ? 'מעדכן נתונים…' : status.at ? 'עודכן ב-' + stamp(status.at) : 'נתונים נכונים ל-' + DB.ASOF}</span>
      <button onClick=${A.refresh} class="glass-2 r-full row press b tap" style=${{ padding: '0 16px', gap: 6 }}><${Icon} n="sync" s=${14} cls=${status.loading ? 'spin' : ''}/>רענון</button></div>
    ${me || em ? html`<div class=${me && em ? 'grid2' : ''}>
      ${me ? html`<button onClick=${() => A.go('stand', 'league')} class="glass r-lg pad-s press" style=${{ textAlign: 'right' }}><div class="t-xs mu">ליגת ווינר סל</div><div class="t-xl num">מקום ${rk + 1}</div><div class="t-xs mu">${me.w} נצ׳ · ${me.l} הפ׳ · ${me.p} נק׳</div></button>` : null}
      ${em ? html`<button onClick=${() => A.go('stand', 'euro')} class="glass r-lg pad-s press" style=${{ textAlign: 'right' }}><div class="t-xs mu">יורוקאפ · ${eg[0]}</div><div class="t-xl num">מקום ${er + 1}</div><div class="t-xs mu">${em[1]} נצ׳ · ${em[2]} הפ׳</div></button>` : null}</div>` : null}
    <div class="grid-md2 stack"><${Leaders} v=${status.at}/><${News}/></div>
  </div>`;
}
