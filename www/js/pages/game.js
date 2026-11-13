/* עמוד משחק: תוצאה (או לייב), תקציר, רבעים, סטטיסטיקה קבוצתית ואישית, מהלך המשחק */
import { html, useState, useEffect, useRef, cx, portal } from '../ui/html.js';
import { Icon } from '../ui/icons.js';
import { useApp, Crest, Seg, Back, LinkRows } from '../ui/kit.js';
import { LiveScore, MiniScore, Quarters, TeamStats, BoxScore, PlayByPlay } from '../ui/score.js';
import { ME } from '../data/constants.js';
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
export default function Game({ g: g0 }) {
  const A = useApp(); let g = DB.F.find(f => f.id === g0.id) || g0;
  const live = A.live && A.live.app === g.id && A.live.status === 'live' ? A.live : null;
  const cardR = useRef(null), [tab, setTab] = useState('g'), [pbp, setPbp] = useState(null), [ld, setLd] = useState(null);
  useEffect(() => { setLd(null); if (!live && g.r && official(g.c)) { let on = true; pastLs(g).then(d => { if (on && d) setLd(d); }); return () => { on = false; }; } }, [g.id, !!live]);
  useEffect(() => { setPbp(null); if (!live && g.r) { let on = true; pastPbp(g).then(x => { if (on) setPbp(x); }); return () => { on = false; }; } }, [g.id, !!live, !!g.r]);
  const lsGs = ld ? (() => { try { return { ...gsLs(ld), src: 'ls' }; } catch (e) { return null; } })() : null, lr = ld ? lsFinal(ld) : null;
  if (lr && (lr[0] || lr[1])) g = { ...g, r: lr };
  const gs = live && live.gs ? { ...live.gs, src: live.src } : (lsGs || gsOf(g.id)), gg = live ? { ...g, r: live.r } : g;
  const pf = Array.isArray(DB.GP[g.id]) ? DB.GP[g.id].filter(p => Array.isArray(p) && p.length > 1) : null;
  const hm = g.h ? ME : g.o, aw = g.h ? g.o : ME, sc = g.r ? (g.h ? [g.r[0], g.r[1]] : [g.r[1], g.r[0]]) : null, wn = sc && sc[0] !== sc[1] ? (sc[0] > sc[1] ? 0 : 1) : -1;
  const plays = live && live.pbp ? live.pbp : pbp;
  const tabs = [['g', 'משחק'], ['s', 'קבוצתי'], ['p', 'אישי'], ...(plays ? [['b', 'מהלך']] : [])];
  return html`<div>
    ${live || sc ? html`<${Dock} cardR=${cardR} g=${g} L=${live} sc=${sc}/>` : null}
    <${Back} onClick=${A.back}/>
    <div class="stack">
      <div ref=${cardR} class="glass-red r-lg pad">
        <div class="t-xs" style=${{ opacity: .9, marginBottom: 12 }}>${compLabel(g)} · ${fdLong(g)}</div>
        ${live ? html`<${LiveScore} L=${live} g=${g} big=${1}/>` : html`<div class="row" style=${{ justifyContent: 'space-around', textAlign: 'center' }}>
          <div class="col" style=${{ alignItems: 'center', gap: 6, width: 110 }}><${Crest} n=${hm} s=${68}/><div class=${cx('t-s', wn === 0 ? 'xb' : 'md')}>${hm}</div></div>
          ${sc ? html`<div class="row num" style=${{ gap: 8, fontSize: 42 }}><span class=${wn === 0 ? 'xb' : 'lt'}>${sc[0]}</span><span style=${{ opacity: .55 }}>–</span><span class=${wn === 1 ? 'xb' : 'lt'}>${sc[1]}</span></div>` : html`<div class="t-l xb" style=${{ opacity: .8 }}>VS</div>`}
          <div class="col" style=${{ alignItems: 'center', gap: 6, width: 110 }}><${Crest} n=${aw} s=${68}/><div class=${cx('t-s', wn === 1 ? 'xb' : 'md')}>${aw}</div></div></div>
          <div class="t-xs row center" style=${{ marginTop: 12, gap: 4, opacity: .9 }}><${Icon} n="pin" s=${13}/>${g.v || (g.h ? 'משחק בית' : 'משחק חוץ')}</div>`}
      </div>
      ${live ? html`<div class="t-xs mu" style=${{ textAlign: 'center' }}>סטטיסטיקות בלייב ${live.src === 'el' ? 'מהאתר הרשמי של היורוקאפ' : live.src === 'ls' ? 'מה-Live Stats של איגוד הכדורסל' : 'מ-365scores'} · מתעדכנות כל כמה שניות</div>` : null}
      ${gs ? html`<${Seg} items=${tabs} val=${tab} set=${setTab} label="פרטי המשחק"/>
        <div key=${tab} class="stack fade-in">${tab === 'b' && plays ? html`<${PlayByPlay} pbp=${plays} g=${g}/>`
          : tab === 'g' ? html`<${Quarters} g=${gg} gs=${gs}/>${g.r && !live ? html`<${Highlight} g=${g}/>` : null}`
          : tab === 's' ? html`<${TeamStats} g=${gg} gs=${gs}/>` : html`<${BoxScore} g=${gg} gs=${gs} pf=${pf}/>`}</div>`
      : html`${g.r && !live ? html`<${Highlight} g=${g}/>` : null}
        ${plays ? html`<${PlayByPlay} pbp=${plays} g=${g}/>` : null}
        ${pf && pf.length ? html`<${BoxScore} g=${gg} gs=${{}} pf=${pf}/>` : !g.r ? html`<div class="glass-2 r-md t-s mu" style=${{ padding: '10px 14px' }}>הסטטיסטיקה תופיע כאן אחרי שריקת הסיום</div>` : html`<div class="glass r-lg pad t-s mu">הסטטיסטיקה המפורטת של המשחק לא נטענה לאפליקציה.</div>`}`}
      <${LinkRows} items=${[[url365(g), 'עמוד המשחק ב-365scores'], [officialUrl(g), /^https:\/\/(basket\.co\.il\/game|www\.euroleague)/.test(g.ou || '') ? 'עמוד המשחק באתר המפעל הרשמי' : 'אתר המפעל הרשמי']]}/>
    </div></div>`;
}
