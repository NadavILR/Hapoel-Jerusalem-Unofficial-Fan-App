/* מסך עדכון חובה: גרסה חדשה – הורדה והתקנה מתוך האפליקציה */
import { html, useState, portal } from '../ui/html.js';
import { Icon } from '../ui/icons.js';
import { plug, VERSION } from '../core/env.js';
export default function UpdateGate({ u }) {
  const [st, setSt] = useState(''), [p, setP] = useState(-1), notes = (u.notes || []).filter(Boolean), busy = st === 'dl';
  const run = async () => {
    const U = plug('Updater'); if (!U) { location.href = u.url; return; }
    try { const c = await U.canInstall(); if (!c || !c.allowed) { setSt('perm'); await U.openInstallSettings(); return; } } catch (e) { }
    setSt('dl'); setP(0); let h = null; try { h = await U.addListener('progress', e => setP(Math.round((e && e.p) || 0))); } catch (e) { }
    try { await U.downloadAndInstall({ url: u.url }); setSt('inst'); } catch (e) { setSt('err'); } finally { try { h && h.remove && Promise.resolve(h.remove()).catch(() => { }); } catch (e) { } }
  };
  return portal(html`<div class="gate fade-in" role="dialog" aria-modal="true" aria-label="עדכון חדש זמין"><div class="full-bg"></div>
    <div class="wrap col" style=${{ flex: 1, justifyContent: 'center', alignItems: 'center', textAlign: 'center', gap: 14, maxWidth: 460 }}>
      <img src="icons/icon-192.png" alt="" class="pop-in" style=${{ width: 92, height: 92, borderRadius: 26, boxShadow: 'var(--shadow-lg)' }}/>
      <span class="glass-2 r-full t-xs b" style=${{ padding: '3px 12px' }}>${u.alpha ? 'גרסת אלפא חדשה' : u.pre ? 'גרסת ניסיון חדשה' : 'גרסה חדשה'}</span>
      <h1 class="t-xl" style=${{ margin: 0 }}>עדכון חדש זמין</h1>
      <div class="row center" style=${{ gap: 10 }}><span class="glass-2 r-md num mu b" style=${{ padding: '6px 12px' }}>${VERSION()}</span><${Icon} n="fwd" s=${18} cls="mu"/><span class="btn-red r-md num b" style=${{ padding: '6px 12px' }}>${u.v}</span></div>
      <div class="t-s mu">כדי להמשיך להשתמש באפליקציה צריך לעדכן</div>
      ${notes.length ? html`<div class="glass r-lg pad" style=${{ width: '100%', textAlign: 'right', maxHeight: '34vh', overflowY: 'auto' }}><div class="sec-t" style=${{ marginBottom: 6 }}>מה חדש</div>${notes.map((n, i) => html`<div key=${i} class="row t-s" style=${{ alignItems: 'flex-start', gap: 8, padding: '4px 0' }}><span class="dot" style=${{ width: 6, height: 6, marginTop: 8 }}></span><span>${n}</span></div>`)}</div>` : null}
    </div>
    <div class="wrap" style=${{ maxWidth: 460, width: '100%' }}>
      ${st === 'perm' ? html`<div class="t-xs mu" style=${{ textAlign: 'center', marginBottom: 10 }}>אשרו בהגדרות שנפתחו "התרת התקנה ממקור זה", חזרו לכאן ולחצו שוב "עדכון עכשיו"</div>` : null}
      ${st === 'inst' ? html`<div class="t-xs mu" style=${{ textAlign: 'center', marginBottom: 10 }}>במסך ההתקנה שנפתח לחצו "עדכון". אם הוא לא נפתח, לחצו שוב</div>` : null}
      ${st === 'err' ? html`<div class="t-xs red" style=${{ textAlign: 'center', marginBottom: 10 }}>ההורדה נכשלה. בדקו את החיבור ונסו שוב</div>` : null}
      <button disabled=${busy} onClick=${run} class="btn btn-red press rel" style=${{ width: '100%', height: 56, overflow: 'hidden', fontSize: 16 }}>
        ${busy ? html`<span style=${{ position: 'absolute', inset: 0, background: 'rgb(255 255 255 / .25)', transform: 'scaleX(' + Math.max(3, p) / 100 + ')', transformOrigin: '100% 50%', transition: 'transform .3s var(--ease)' }}></span>` : null}
        <span class="rel row" style=${{ gap: 8 }}>${busy ? html`מוריד את העדכון <span class="num">${p >= 0 ? p + '%' : ''}</span>` : html`<${Icon} n="down" s=${18}/>עדכון עכשיו`}</span></button>
    </div></div>`);
}
