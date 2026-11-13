/* הכרטיסים שלי: מעלים צילום או PDF, האפליקציה מוצאת את קוד ה-QR ומציגה אותו נקי בכרטיס בסגנון המועדון */
import { html, useState, useEffect, useRef, useMemo, portal } from '../ui/html.js';
import { Icon } from '../ui/icons.js';
import { useApp, Seg, Skel } from '../ui/kit.js';
import { ME } from '../data/constants.js';
import { DB } from '../data/store.js';
import { D, H, SEASON, fdLong, fdShort } from '../core/env.js';
import { TIX, TIX_TTL, purge, pdfPages, findQR, qrPath } from '../data/tickets.js';

function QR({ text, size = 200 }) {
  const [d, setD] = useState(null);
  useEffect(() => { let on = true; qrPath(text).then(x => { if (on) setD(x); }).catch(() => { }); return () => { on = false; }; }, [text]);
  return d ? html`<svg width=${size} height=${size} viewBox=${'-2 -2 ' + (d.n + 4) + ' ' + (d.n + 4)} role="img" aria-label="קוד QR של הכרטיס" shape-rendering="crispEdges" style=${{ display: 'block' }}><rect x="-2" y="-2" width=${d.n + 4} height=${d.n + 4} fill="#fff"/><path d=${d.d} fill="#000"/></svg>`
    : html`<div style=${{ width: size, height: size, color: 'rgb(0 0 0 / .4)' }} class="row center t-s">טוען…</div>`;
}
function Viewer({ it, onClose }) {
  const urls = useMemo(() => it.qr ? [] : it.pages.map(b => URL.createObjectURL(b)), [it]);
  useEffect(() => () => urls.forEach(u => URL.revokeObjectURL(u)), [urls]);
  const sz = Math.min(window.innerWidth - 72, 420);
  return portal(html`<div role="dialog" aria-modal="true" aria-label="הכרטיס במסך מלא" class="full white" style=${{ background: '#1c1c1c', paddingTop: 'calc(var(--sat) + 12px)', paddingBottom: 'calc(24px + var(--sab))' }}>
    <div class="row between" style=${{ padding: '0 16px 12px' }}><span class="t-s" style=${{ opacity: .9 }}>הגבירו את בהירות המסך לסריקה בכניסה</span><button onClick=${onClose} aria-label="סגירה" class="icon-btn press" style=${{ background: 'rgb(255 255 255 / .14)' }}><${Icon} n="x" s=${20}/></button></div>
    ${it.qr ? html`<div class="col" style=${{ alignItems: 'center', paddingTop: 24 }}><div class="qr-box" style=${{ padding: 24 }}><${QR} text=${it.qr} size=${sz - 48}/></div><div class="t-s md ltr" style=${{ marginTop: 16, opacity: .9, wordBreak: 'break-all', textAlign: 'center', padding: '0 16px' }}>${it.qr.length > 60 ? it.qr.slice(0, 60) + '…' : it.qr}</div></div>`
    : html`<div class="stack" style=${{ padding: '0 12px' }}>${urls.map((u, i) => html`<img key=${i} src=${u} alt=${'עמוד ' + (i + 1) + ' של הכרטיס'} style=${{ width: '100%', borderRadius: 14, background: '#fff' }}/>`)}</div>`}</div>`);
}
function TixCard({ it, onOpen, onDel }) {
  const g = it.gid ? DB.F.find(f => String(f.id) === String(it.gid)) : null, [qr, setQr] = useState(it.qr), noQr = qr === '';
  useEffect(() => { if (it.qr !== undefined) return; let on = true; findQR(it.pages).then(t => { if (!on) return; setQr(t); TIX.put({ ...it, qr: t }).catch(() => { }); }).catch(() => on && setQr('')); return () => { on = false; }; }, [it.id]);
  const thumb = useMemo(() => noQr && it.pages[0] ? URL.createObjectURL(it.pages[0]) : '', [it, noQr]);
  useEffect(() => () => thumb && URL.revokeObjectURL(thumb), [thumb]);
  const left = it.kind === 'season' ? 0 : Math.max(0, Math.ceil((it.exp - Date.now()) / D));
  const sub = it.kind === 'season' ? 'עונת ' + SEASON : g ? (g.h ? 'משחק בית מול ' : 'משחק חוץ מול ') + g.o : '';
  return html`<article class="tix">
    <div class="col" style=${{ alignItems: 'center', textAlign: 'center', padding: '24px 20px 20px' }}>
      <div class="ring"><img src="icons/club-logo.png" alt="" style=${{ width: 74, height: 74, objectFit: 'contain' }}/></div>
      <div class="t-m md" style=${{ marginTop: 12, opacity: .95 }}>${ME}</div>
      <div class="lt" style=${{ fontSize: 34, lineHeight: 1.15, marginTop: 4 }}>${it.kind === 'season' ? 'מנוי' : 'כרטיס למשחק'}</div>
      ${sub ? html`<div class="t-m md" style=${{ marginTop: 4, opacity: .9 }}>${sub}</div>` : null}
      ${g ? html`<div class="t-s" style=${{ opacity: .75, marginTop: 2 }}>${fdLong(g)}</div>` : null}
      <button onClick=${onOpen} aria-label="הצגת הקוד במסך מלא" class="qr-box press" style=${{ marginTop: 20 }}>${qr ? html`<${QR} text=${qr} size=${190}/>` : noQr ? html`<img src=${thumb} alt="הכרטיס" style=${{ width: 190, maxHeight: 260, objectFit: 'contain' }}/>` : html`<div style=${{ width: 190, height: 190, color: 'rgb(0 0 0 / .4)' }} class="row center t-s">מחפש את הקוד…</div>`}</button>
      ${qr ? html`<div class="t-s md ltr" style=${{ marginTop: 12, wordBreak: 'break-all' }}>${qr.length > 40 ? qr.slice(0, 40) + '…' : qr}</div>` : noQr ? html`<div class="t-xs" style=${{ marginTop: 12, opacity: .75 }}>לא נמצא קוד QR בקובץ – מוצג הקובץ המלא</div>` : null}
    </div>
    <div class="foot row between" style=${{ padding: '12px 20px' }}><span class="t-xs" style=${{ opacity: .75 }}>${it.kind === 'season' ? 'נשמר עד שתמחקו אותו' : left <= 1 ? 'יימחק אוטומטית היום' : 'יימחק אוטומטית בעוד ' + left + ' ימים'}</span>
      <div class="row" style=${{ gap: 8 }}><button onClick=${onOpen} class="btn btn-red btn-s press">הצגה</button><button onClick=${onDel} aria-label="מחיקת הכרטיס" class="btn btn-s press" style=${{ background: 'rgb(255 255 255 / .1)' }}>מחיקה</button></div></div>
  </article>`;
}
export default function MyTickets() {
  const A = useApp(), [items, setItems] = useState(null), [kind, setKind] = useState('game'), [busy, setBusy] = useState(''), [view, setView] = useState(null), fr = useRef(null);
  const up = DB.F.filter(f => !f.r && f.t > Date.now() - 6 * H).sort((a, b) => a.t - b.t), [gid, setGid] = useState(up[0] ? String(up[0].id) : '');
  const load = () => purge().then(() => TIX.all()).then(a => setItems((a || []).sort((x, y) => y.at - x.at))).catch(() => setItems([]));
  useEffect(() => { load(); }, []);
  const pick = async e => {
    const file = e.target.files && e.target.files[0]; e.target.value = ''; if (!file) return;
    if (file.size > 25 * 1024 * 1024) { A.toast('הקובץ גדול מדי (עד 25MB)'); return; }
    const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name), isImg = /^image\//.test(file.type);
    if (!isPdf && !isImg) { A.toast('אפשר להעלות תמונה או PDF בלבד'); return; }
    setBusy(isPdf ? 'ממיר את ה-PDF…' : 'שומר את הכרטיס…');
    try {
      const pages = isPdf ? await pdfPages(file) : [file], now = Date.now(); if (!pages.length) throw new Error('empty');
      setBusy('מחפש את קוד ה-QR…'); const qr = await findQR(pages).catch(() => '');
      await TIX.put({ id: 't' + now, kind, gid: kind === 'game' && gid ? gid : null, name: file.name || 'כרטיס', at: now, exp: kind === 'season' ? 0 : now + TIX_TTL, pages, qr });
      A.toast(kind === 'season' ? 'המנוי נשמר באפליקציה' : 'הכרטיס נשמר – יימחק אוטומטית בעוד שבוע'); await load();
    } catch (err) { A.toast(isPdf ? 'לא הצלחנו לקרוא את ה-PDF. נסו צילום מסך של הכרטיס' : 'לא הצלחנו לשמור את הקובץ'); } finally { setBusy(''); }
  };
  const del = it => { if (!confirm(it.kind === 'season' ? 'למחוק את המנוי מהאפליקציה?' : 'למחוק את הכרטיס מהאפליקציה?')) return; TIX.del(it.id).then(load).catch(() => { }); };
  if (!window.indexedDB) return html`<div class="glass r-lg pad t-s mu">המכשיר לא מאפשר לשמור קבצים באפליקציה.</div>`;
  return html`<div class="stack">
    <section class="glass r-lg pad stack-s">
      <h2 class="sec-t" style=${{ margin: 0 }}>הוספת כרטיס או מנוי</h2>
      <p class="t-s mu" style=${{ margin: 0 }}>מעלים צילום מסך או PDF של הכרטיס, והאפליקציה מציגה רק את קוד ה-QR שלו. הקובץ נשמר רק בטלפון שלך. כרטיס למשחק נמחק אחרי שבוע; מנוי נשמר עד שתמחקו אותו.</p>
      <${Seg} items=${[['game', 'כרטיס למשחק'], ['season', 'מנוי']]} val=${kind} set=${setKind} label="סוג"/>
      ${kind === 'game' ? html`<label class="col t-s" style=${{ gap: 4 }}><span class="mu">לאיזה משחק?</span><select value=${gid} onChange=${e => setGid(e.target.value)} class="glass-2 r-md" style=${{ padding: '10px 12px', border: '1px solid var(--edge)' }}>${up.map(f => html`<option key=${f.id} value=${String(f.id)}>${(f.h ? 'בית' : 'חוץ') + ' · ' + f.o + ' · ' + fdShort(f)}</option>`)}<option value="">משחק אחר / לא מהרשימה</option></select></label>` : null}
      <input ref=${fr} type="file" accept="image/*,application/pdf" hidden onChange=${pick}/>
      <button disabled=${!!busy} onClick=${() => fr.current && fr.current.click()} class="btn btn-red press" style=${{ width: '100%' }}><${Icon} n=${busy ? 'sync' : 'upload'} s=${18} cls=${busy ? 'spin' : ''}/>${busy || (kind === 'season' ? 'העלאת מנוי (תמונה או PDF)' : 'העלאת כרטיס (תמונה או PDF)')}</button>
    </section>
    ${items === null ? html`<${Skel} n=${2} h=${200}/>` : items.length ? items.map(it => html`<${TixCard} key=${it.id} it=${it} onOpen=${() => setView(it)} onDel=${() => del(it)}/>`) : html`<div class="t-s mu" style=${{ textAlign: 'center', padding: '20px 12px' }}>עוד לא שמרת כרטיסים. הכרטיס יופיע כאן, ובלחיצה ייפתח במסך מלא לסריקה בכניסה.</div>`}
    ${view ? html`<${Viewer} it=${view} onClose=${() => setView(null)}/>` : null}
  </div>`;
}
