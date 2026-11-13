/* עמוד משחק: תוצאה (או לייב), תקציר, רבעים, סטטיסטיקה קבוצתית ואישית, מהלך המשחק */
import { html, useState, useEffect, useRef, cx, portal } from '../ui/html.js';
import { Icon } from '../ui/icons.js';
import { useApp, useNow, Crest, Seg, Back, LinkRows } from '../ui/kit.js';
import { fresh } from './home.js';
import { LiveScore, MiniScore, Quarters, TeamStats, BoxScore, PlayByPlay } from '../ui/score.js';
import { ME, LINKS } from '../data/constants.js';
import { DB, compLabel, gsOf, url365, officialUrl } from '../data/store.js';
import { pastLs, pastPbp, gsLs, lsFinal } from '../data/live.js';
import { ytId, hlUrl } from '../data/media.js';
import { fdLong } from '../core/env.js';

const official = c => c === 'league' || c === 'wcup';
function Highlight({ g }) {
  const id = ytId(g);
  return id ? html`<div class="r-lg fade-in" style=${{ overflow: 'hidden', aspectRatio: '16/9', background: '#000' }}><iframe key=${id} src=${'https://www.youtube-nocookie.com/embed/' + id + '?rel=0&playsinline=1'} title="תקציר המשחק" style=${{ width: '100%', height: '100%', border: 0 }} loading="lazy" referrerPolicy="strict-origin-when-cross-origin" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowFullScreen></iframe></div>`
    : html`<a href=${hlUrl(g)} target="_blank" rel="noopener noreferrer" class="btn btn-red press" style=${{ width: '100%' }}><${Icon} n="play" s=${16}/>לתקציר המשחק ביוטיוב</a>`;
}
/* תוצאה מכווצת שנצמדת מתחת לכותרת כשגוללים מעבר לכרטיס */
function Dock({ cardR, g, L, sc }) {
  const [on, setOn] = useState(false), [el, setEl] = useState(null);
  useEffect(() => {
    setEl(document.getElementById('hd-slot'));
    let q = 0; const f = () => { q = 0; const c = cardR.current; if (!c) return; const top = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--hdh')) || 64, b = c.getBoundingClientRect(); setOn(b.bottom - b.height * .4 < top); };
    const s = () => { if (!q) q = requestAnimationFrame(f); }; f(); window.addEventListener('scroll', s, { passive: true });
    return () => { window.removeEventListener('scroll', s); cancelAnimationFrame(q); };
  }, []);
  return el ? portal(html`<div class=${cx('fx-sub', on && 'on')} aria-hidden=${on ? undefined : 'true'}><div><div style=${{ padding: '2px 2px 10px' }}><${MiniScore} g=${g} L=${L} sc=${sc}/></div></div></div>`, el) : null;
}
/* לפני שריקת הפתיחה: המפגש הקודם העונה, הכושר של הפועל, ופרטי המשחק */
function PreGame({ g }) {
  const done = DB.F.filter(f => f.r && f.t < g.t && f.c !== 'prep').sort((a, b) => b.t - a.t), met = done.filter(f => f.o === g.o), form = done.slice(0, 5);
  return html`<section class="glass r-lg" style=${{ overflow: 'hidden' }}>
    ${met.length ? html`<div class="li"><span class="grow"><span class="li-s" style=${{ display: 'block' }}>המפגש הקודם העונה</span><span class="b">${met[0].h ? ME + ' ' + met[0].r[0] + ' – ' + met[0].r[1] + ' ' + met[0].o : met[0].o + ' ' + met[0].r[1] + ' – ' + met[0].r[0] + ' ' + ME}</span></span><span class=${met[0].r[0] > met[0].r[1] ? 'res-w b t-s' : 'res-l b t-s'}>${met[0].r[0] > met[0].r[1] ? 'ניצחון' : 'הפסד'}</span></div>` : null}
    ${form.length ? html`<div class="li"><span class="grow li-s">${form.length} המשחקים האחרונים של הפועל</span><span class="row" style=${{ gap: 4 }} aria-label=${form.map(f => f.r[0] > f.r[1] ? 'ניצחון' : 'הפסד').join(', ')}>${form.slice().reverse().map(f => html`<span key=${f.id} class=${f.r[0] > f.r[1] ? 'res-w' : 'res-l'} style=${{ width: 24, height: 24, borderRadius: 8, background: 'currentColor', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><span style=${{ color: '#fff', fontSize: 12, fontWeight: 800 }}>${f.r[0] > f.r[1] ? 'נ' : 'ה'}</span></span>`)}</span></div>` : null}
    <div class="li"><span class="grow"><span class="li-s" style=${{ display: 'block' }}>${g.h ? 'משחק בית' : 'משחק חוץ'}</span><span class="b">${g.v || 'האולם טרם פורסם'}</span></span>${g.h ? html`<a href=${LINKS.tickets} target="_blank" rel="noopener noreferrer" class="btn btn-red btn-s press">כרטיסים</a>` : null}</div>
    <div class="li t-xs mu">הסטטיסטיקה של המשחק תופיע כאן אחרי שריקת הסיום</div>
  </section>`;
}
export default function Game({ g: g0 }) {
  const A = useApp(); let g = DB.F.find(f => f.id === g0.id) || g0;
  const live = A.live && A.live.app === g.id && A.live.status === 'live' ? A.live : null;
  const now = useNow(), cardR = useRef(null), [tab0, setTab] = useState(null), [pbp, setPbp] = useState(null), [ld, setLd] = useState(null);
  useEffect(() => { setLd(null); if (!live && g.r && official(g.c)) { let on = true; pastLs(g).then(d => { if (on && d) setLd(d); }); return () => { on = false; }; } }, [g.id, !!live]);
  useEffect(() => { setPbp(null); if (!live && g.r) { let on = true; pastPbp(g).then(x => { if (on) setPbp(x); }); return () => { on = false; }; } }, [g.id, !!live, !!g.r]);
  const lsGs = ld ? (() => { try { return { ...gsLs(ld), src: 'ls' }; } catch (e) { return null; } })() : null, lr = ld ? lsFinal(ld) : null;
  if (lr && (lr[0] || lr[1])) g = { ...g, r: lr };
  const gs = live && live.gs ? { ...live.gs, src: live.src } : (lsGs || gsOf(g.id)), gg = live ? { ...g, r: live.r } : g;
  const pf = Array.isArray(DB.GP[g.id]) ? DB.GP[g.id].filter(p => Array.isArray(p) && p.length > 1) : null;
  const hm = g.h ? ME : g.o, aw = g.h ? g.o : ME, sc = g.r ? (g.h ? [g.r[0], g.r[1]] : [g.r[1], g.r[0]]) : null, wn = sc && sc[0] !== sc[1] ? (sc[0] > sc[1] ? 0 : 1) : -1;
  const plays = live && live.pbp ? live.pbp : pbp;
  /* בזמן לייב: מהלך המשחק ראשון (הרבעים כבר בכרטיס שלמעלה) */
  const tabs = live ? [...(plays ? [['b', 'מהלך']] : []), ['s', 'קבוצתי'], ['p', 'אישי']] : [['g', 'משחק'], ['s', 'קבוצתי'], ['p', 'אישי'], ...(plays ? [['b', 'מהלך']] : [])];
  const tab = tabs.some(t => t[0] === tab0) ? tab0 : tabs[0][0];
  return html`<div>
    ${live || sc ? html`<${Dock} cardR=${cardR} g=${g} L=${live} sc=${sc}/>` : null}
    <${Back} onClick=${A.back}/>
    <div class="stack">
      <div ref=${cardR} class="glass-red r-lg pad">
        <div class="t-xs" style=${{ marginBottom: 12 }}>${compLabel(g)} · ${fdLong(g)}</div>
        ${live ? html`<${LiveScore} L=${live} g=${g} big=${1}/>` : html`<div class="row" style=${{ justifyContent: 'space-around', textAlign: 'center' }}>
          <div class="col" style=${{ alignItems: 'center', gap: 6, width: 110 }}><${Crest} n=${hm} s=${68}/><div class=${cx('t-s', wn === 0 ? 'xb' : 'md')}>${hm}</div></div>
          ${sc ? html`<div class="row num" style=${{ gap: 8, fontSize: 42 }}><span class=${wn === 0 ? 'xb' : 'lt'}>${sc[0]}</span><span style=${{ opacity: .55 }}>–</span><span class=${wn === 1 ? 'xb' : 'lt'}>${sc[1]}</span></div>` : html`<div class="t-l xb" style=${{ }}>VS</div>`}
          <div class="col" style=${{ alignItems: 'center', gap: 6, width: 110 }}><${Crest} n=${aw} s=${68}/><div class=${cx('t-s', wn === 1 ? 'xb' : 'md')}>${aw}</div></div></div>
          <div class="t-xs row center" style=${{ marginTop: 12, gap: 4 }}><${Icon} n="pin" s=${13}/>${g.v || (g.h ? 'משחק בית' : 'משחק חוץ')}</div>`}
      </div>
      ${live ? html`<div class="t-xs mu" style=${{ textAlign: 'center' }}>סטטיסטיקות בלייב ${live.src === 'el' ? 'מהאתר הרשמי של היורוקאפ' : live.src === 'ls' ? 'מה-Live Stats של איגוד הכדורסל' : 'מ-365scores'} · ${fresh(live, now)}</div>` : null}
      ${gs ? html`<${Seg} items=${tabs} val=${tab} set=${setTab} label="פרטי המשחק"/>
        <div key=${tab} class="stack fade-in">${tab === 'b' && plays ? html`<${PlayByPlay} pbp=${plays} g=${g}/>`
          : tab === 'g' ? html`<${Quarters} g=${gg} gs=${gs}/>${g.r && !live ? html`<${Highlight} g=${g}/>` : null}`
          : tab === 's' ? html`<${TeamStats} g=${gg} gs=${gs}/>` : html`<${BoxScore} g=${gg} gs=${gs} pf=${pf}/>`}</div>`
      : html`${g.r && !live ? html`<${Highlight} g=${g}/>` : null}
        ${plays ? html`<${PlayByPlay} pbp=${plays} g=${g}/>` : null}
        ${pf && pf.length ? html`<${BoxScore} g=${gg} gs=${{}} pf=${pf}/>` : !g.r ? html`<${PreGame} g=${g}/>` : html`<div class="glass r-lg pad t-s mu">הסטטיסטיקה המפורטת של המשחק לא נטענה לאפליקציה.</div>`}`}
      <${LinkRows} items=${[[url365(g), 'עמוד המשחק ב-365scores'], [officialUrl(g), /^https:\/\/(basket\.co\.il\/game|www\.euroleague)/.test(g.ou || '') ? 'עמוד המשחק באתר המפעל הרשמי' : 'אתר המפעל הרשמי']]}/>
    </div></div>`;
}
