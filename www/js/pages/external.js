/* חנות ומנוי בתוך האפליקציה (מסגרת), וכשאי אפשר – מסך עם כפתור לאתר. אתר הכרטיסים: באנדרואיד – חלון נייטיב מעל האפליקציה */
import { html, useState, useEffect, useLayoutEffect } from '../ui/html.js';
import { Icon } from '../ui/icons.js';
import { useApp, Crest } from '../ui/kit.js';
import { ME, LINKS } from '../data/constants.js';
import { DB, compLabel } from '../data/store.js';
import { fdShort } from '../core/env.js';

export const EXT = { tix: LINKS.tickets, shop: LINKS.shop, mem: LINKS.memberAuth };
const INFO = { tix: ['כרטיסים', 'לרכישת כרטיסים באתר הרשמי'], shop: ['חנות המועדון', 'לחנות הרשמית'], mem: ['מנוי ואזור אישי', 'למנוי ולאזור האישי'] };
let BLOCKED = /claudeusercontent\.com$/.test(location.hostname || '');
/* ניווט בתוך המסגרת, כדי שכפתור החזרה יחזור קודם לדף הקודם באתר */
export const FR = { n: 0, skip: 0 };
const frLoad = () => { if (FR.skip > 0) FR.skip--; else FR.n++; };
function Fallback({ k }) {
  const u = EXT[k], nx = k === 'tix' ? DB.F.filter(f => !f.r && f.h).sort((a, b) => a.t - b.t).slice(0, 6) : [];
  return html`<div class="stack">
    <div class="glass-red r-lg pad" style=${{ textAlign: 'center', padding: 24 }}><div class="t-xl">${INFO[k][0]}</div><div class="t-s" style=${{ opacity: .85, margin: '4px 0 16px' }}>${ME}</div>
      <a href=${u} target="_blank" rel="noopener noreferrer" class="btn btn-white press" style=${{ width: '100%' }}>${INFO[k][1]}<${Icon} n="ext" s=${15}/></a></div>
    <div class="t-xs mu glass-2 r-md" style=${{ padding: '8px 12px' }}>${k === 'tix' ? 'האתר הרשמי לא מאפשר הצגה בתוך אפליקציות אחרות, ולכן הוא נפתח בלשונית נפרדת. אחרי הפעולה אפשר לחזור לכאן.' : 'התצוגה הנוכחית חוסמת אתרים חיצוניים בתוך האפליקציה, ולכן האתר נפתח בלשונית נפרדת.'}</div>
    ${nx.length ? html`<section class="glass r-lg" style=${{ overflow: 'hidden' }}><div class="sec-t pad-x" style=${{ paddingTop: 14, paddingBottom: 4 }}>משחקי הבית הקרובים</div>${nx.map(f => html`<a key=${f.id} href=${u} target="_blank" rel="noopener noreferrer" class="li press"><${Crest} n=${f.o} s=${34}/><div class="grow"><div class="b trunc">${ME} – ${f.o}</div><div class="t-xs mu trunc">${fdShort(f)} · ${compLabel(f)}</div></div><span class="link t-xs nowrap">לכרטיסים</span></a>`)}</section>` : null}
  </div>`;
}
export default function External({ k }) {
  const [blk, setBlk] = useState(k === 'tix' ? true : BLOCKED), [bt, setBt] = useState(64);
  useEffect(() => { const n = document.getElementById('nav'); if (!n) return; const f = () => setBt(n.offsetHeight); f(); let ro; try { ro = new ResizeObserver(f); ro.observe(n); } catch (e) { } return () => ro && ro.disconnect(); }, []);
  useLayoutEffect(() => { const h = e => { if (/^(frame|child)-src/.test(e.effectiveDirective || e.violatedDirective || '')) { BLOCKED = true; setBlk(true); } }; document.addEventListener('securitypolicyviolation', h); return () => document.removeEventListener('securitypolicyviolation', h); }, [k]);
  if (blk) return html`<${Fallback} k=${k}/>`;
  return html`<div style=${{ position: 'fixed', left: 0, right: 0, top: 'var(--sat)', bottom: bt + 'px', zIndex: 32, background: '#fff' }}><iframe key=${k} title=${INFO[k][0]} src=${EXT[k]} onLoad=${frLoad} style=${{ width: '100%', height: '100%', border: 0 }} allow="camera; fullscreen; clipboard-write; payment"></iframe></div>`;
}
