/* רכיבים משותפים */
import { html, useState, useEffect, useRef, useContext, createContext, portal, cx, memo } from './html.js';
import { Icon } from './icons.js';
import { subscribe, crestSrcs, crestBackup, crestDataOk } from '../data/store.js';
import { ME } from '../data/constants.js';

/* מצב האפליקציה (ניווט, הודעות, העדפות, לייב) */
export const AppCtx = createContext(null);
export const useApp = () => useContext(AppCtx);
/* רינדור מחדש בכל עדכון של הנתונים */
export const useDB = () => { const [, s] = useState(0); useEffect(() => subscribe(v => s(v)), []); };
export const useNow = (ms = 1000) => { const [n, s] = useState(Date.now()); useEffect(() => { const i = setInterval(() => s(Date.now()), ms); return () => clearInterval(i); }, [ms]); return n; };

/* סמל קבוצה: 365scores → עותק מקומי → אות ראשונה */
export const Crest = memo(function Crest({ n, s = 40 }) {
  const [i, setI] = useState(0), [d, setD] = useState('');
  useEffect(() => { setI(0); setD(''); }, [n]);
  const ss = n === ME ? [] : crestSrcs(n), src = n === ME ? 'icons/club-logo.png' : i < ss.length ? ss[i] : d;
  useEffect(() => { if (n !== ME && i >= ss.length && !d) crestBackup().then(j => { const v = j && j[n]; if (crestDataOk(v)) setD(v); }); }, [n, i, ss.length]);
  if (src) return html`<img src=${src} alt="" width=${s} height=${s} class="crest" style=${{ width: s, height: s }} referrerPolicy="no-referrer" decoding="async" onError=${() => i < ss.length ? setI(x => x + 1) : setD('')}/>`;
  return html`<div class="crest-l" style=${{ width: s, height: s, fontSize: s / 2.3 }} aria-hidden="true">${(n || '?')[0]}</div>`;
});

/* תמונות שחקנים: הקישור, ואם נחסם – עותק מקומי (photos.json) */
let PHP = null; const phBackup = () => PHP || (PHP = fetch('photos.json').then(r => r.ok ? r.json() : {}).catch(() => ({})));
const phOk = v => typeof v === 'string' && /^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+\/=]+$/.test(v);
export function usePhoto(u, k) {
  const [st, setSt] = useState(0), [d, setD] = useState('');
  useEffect(() => { setSt(0); setD(''); }, [u]);
  const err = () => { if (st === 0) { setSt(1); phBackup().then(j => { const v = j && j[k]; if (phOk(v)) setD(v); else setSt(2); }); } else setSt(2); };
  return [st === 0 ? u : st === 1 ? d : '', err];
}
export function Photo({ u, k, alt = '', cls = '', fallback = '' }) {
  const [src, err] = usePhoto(u, k);
  return src ? html`<img src=${src} alt=${alt} loading="lazy" decoding="async" referrerPolicy="no-referrer" onError=${err} class=${cx('ph', cls)}/>`
    : html`<div class=${cx('ph row center', cls)} style=${{ color: 'rgb(255 255 255 / .35)', fontSize: 48, fontWeight: 800 }}>${fallback}</div>`;
}

/* בורר מקטעים עם סימון שמחליק */
export function Seg({ items, val, set, cls = '', label }) {
  const i = Math.max(0, items.findIndex(x => x[0] === val)), n = items.length;
  return html`<div role="tablist" aria-label=${label} class=${cx('seg', cls)}>
    <span class="seg-ind" style=${{ width: `calc((100% - 8px) / ${n})`, transform: `translateX(${-i * 100}%)` }}></span>
    ${items.map(([k, l]) => html`<button key=${k} role="tab" aria-selected=${String(k === val)} onClick=${() => set(k)}>${l}</button>`)}
  </div>`;
}
/* כפתורי סינון */
export const Chips = ({ items, val, set, sub, label }) => html`<div class="chips" role="tablist" aria-label=${label}>${items.map(([k, l]) => html`<button key=${k} role="tab" aria-selected=${String(k === val)} onClick=${() => set(k)} class=${cx('chip', sub && 'small', k === val && 'on')}>${l}</button>`)}</div>`;
export const Toggle = ({ on, set, label, sub, disabled }) => html`<button role="switch" aria-checked=${String(!!on)} disabled=${disabled} onClick=${() => set(!on)} class="li press" style=${{ width: '100%', textAlign: 'right', opacity: disabled ? .5 : 1 }}>
  <span class="grow"><span class="li-t" style=${{ display: 'block', fontWeight: 600 }}>${label}</span>${sub ? html`<span class="li-s" style=${{ display: 'block' }}>${sub}</span>` : null}</span>
  <span class="tg" aria-checked=${String(!!on)} disabled=${disabled}></span></button>`;
export const Skel = ({ n = 5, h = 56 }) => html`<div class="stack-s" aria-busy="true" aria-label="טוען">${Array.from({ length: n }, (_, i) => html`<div key=${i} class="sk" style=${{ height: h }}></div>`)}</div>`;
export const Section = ({ title, aside, children, cls = '' }) => html`<section class=${cx('glass r-lg', cls)}>
  ${title ? html`<div class="row between pad-x" style=${{ paddingTop: 14, paddingBottom: 6 }}><h2 class="sec-t" style=${{ margin: 0 }}>${title}</h2>${aside || null}</div>` : null}${children}</section>`;
export const Empty = ({ title, sub, children }) => html`<div class="glass r-lg pad col center" style=${{ alignItems: 'center', gap: 8, padding: '28px 20px' }}>
  <div class="ic-soft"><${Icon} n="ball" s=${20}/></div><div class="t-m">${title}</div>${sub ? html`<div class="t-s mu">${sub}</div>` : null}${children || null}</div>`;
export const ExtLink = ({ href, children, cls = 'link t-s' }) => html`<a href=${href} target="_blank" rel="noopener noreferrer" class=${cls}>${children}<${Icon} n="ext" s=${13}/></a>`;
export const LinkRows = ({ items }) => html`<div class="glass lite r-lg" style=${{ overflow: 'hidden' }}>${items.filter(Boolean).map(([href, l], i) => html`<a key=${i} href=${href} target="_blank" rel="noopener noreferrer" class="li press"><span class="ic-soft" style=${{ width: 32, height: 32, borderRadius: 10 }}><${Icon} n="ext" s=${15}/></span><span class="grow b t-s">${l}</span><${Icon} n="fwd" s=${16} cls="mu"/></a>`)}</div>`;

/* גיליון תחתון */
export function Sheet({ onClose, children, label }) {
  useEffect(() => { const k = e => { if (e.key === 'Escape') onClose(); }; document.addEventListener('keydown', k); return () => document.removeEventListener('keydown', k); }, []);
  return portal(html`<${React.Fragment}><div class="scrim" onClick=${onClose}></div>
    <div class="sheet glass-3" role="dialog" aria-modal="true" aria-label=${label}><div class="grab"></div><div class="in">${children}</div></div><//>`);
}
/* מסך מלא מעל האפליקציה (הגדרות, התראות) */
export function FullPage({ title, onClose, children, right }) {
  return portal(html`<div class="full" role="dialog" aria-modal="true" aria-label=${title}><div class="full-bg"></div>
    <div class="full-hd bar"><div class="wrap row" style=${{ minHeight: 58 }}><button class="icon-btn press" onClick=${onClose} aria-label="חזרה"><${Icon} n="back" s=${22}/></button><div class="grow t-l">${title}</div>${right || null}</div></div>
    <div class="wrap" style=${{ padding: '14px 14px calc(30px + var(--sab))' }}>${children}</div></div>`);
}
/* תקציר יוטיוב בחלון בתוך האפליקציה */
export const VIDC = { close: null };
export function VideoPop({ id, title, onClose }) {
  useEffect(() => { VIDC.close = onClose; const k = e => { if (e.key === 'Escape') onClose(); }; document.addEventListener('keydown', k); return () => { VIDC.close = null; document.removeEventListener('keydown', k); }; }, []);
  return portal(html`<div class="vid" onClick=${onClose} role="dialog" aria-modal="true" aria-label=${title}><div style=${{ width: '100%', maxWidth: 860 }} class="pop-in" onClick=${e => e.stopPropagation()}>
    <div class="row between white" style=${{ marginBottom: 8 }}><div class="t-s md trunc">${title}</div><button onClick=${onClose} aria-label="סגירה" class="icon-btn sm press" style=${{ background: 'rgb(255 255 255 / .14)' }}><${Icon} n="x" s=${20}/></button></div>
    <div class="fr"><iframe src=${'https://www.youtube-nocookie.com/embed/' + id + '?rel=0&playsinline=1&autoplay=1'} title=${title} referrerPolicy="strict-origin-when-cross-origin" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowFullScreen></iframe></div></div></div>`);
}
/* תוכן שמוצג בתוך הכותרת (סינון): נכנס בהחלקה */
export function HeaderSlot({ children }) {
  const [el, setEl] = useState(null);
  useEffect(() => { setEl(document.getElementById('hd-slot')); }, []);
  return el ? portal(html`<div class="hd-filters fx-in">${children}</div>`, el) : null;
}
/* כפתור חזרה נעול מתחת לכותרת */
export const Back = ({ onClick, label = 'חזרה' }) => html`<div class="back-row"><button onClick=${onClick} class="back glass-2 press"><${Icon} n="back" s=${18}/><span>${label}</span></button></div>`;
export const W = (a, b) => a > b ? 'ניצחון' : 'הפסד';
