/* הגדרות: התראות (תזכורות, רבעים, כרטיס לייב, Now Bar, רקע, מבצעים, לפי מפעל), על האפליקציה, ערוץ עדכונים */
import { html, useState, useRef, Fragment } from '../ui/html.js';
import { Icon } from '../ui/icons.js';
import { useApp, Seg, Toggle, FullPage } from '../ui/kit.js';
import { COMPS } from '../data/constants.js';
import { DB } from '../data/store.js';
import { NATIVE, plug, VERSION, LS } from '../core/env.js';
import { LN, askPerm, scheduleReminder, nbMode } from '../features/notify.js';
import { channel, setChannel } from '../data/updates.js';

export const NbPick = ({ pf, setPf }) => html`<${Seg} items=${[['c', 'כרטיס התוצאה'], ['m', 'Now Bar · שורה'], ['t', 'Now Bar · טקסט']]} val=${nbMode(pf)} set=${k => setPf({ ...pf, nb: k })} label="תצוגת הלייב"/>`;
const Note = ({ children }) => html`<div class="t-xs mu" style=${{ padding: '0 16px 12px', marginTop: -4 }}>${children}</div>`;
export default function Settings({ onClose }) {
  const A = useApp(), pf = A.pf, sp = (k, v) => A.setPf({ ...pf, [k]: v }), on = pf.on !== 0 && A.perm === 'granted', [chk, setChk] = useState(''), [ch, setCh] = useState(channel);
  /* גרסאות אלפא נפתחות רק אחרי 7 לחיצות מהירות על מספר הגרסה (כמו "אפשרויות מפתחים" באנדרואיד) */
  const [alpha, setAlpha] = useState(() => channel() >= 2 || !!LS.get('hpj-alpha', 0)), taps = useRef([]);
  const tapVersion = () => {
    if (!NATIVE) return;
    if (alpha) { A.toast(ch >= 2 ? 'גרסאות אלפא כבר פעילות' : 'גרסאות אלפא כבר פתוחות – אפשר לבחור אותן למטה'); return; }
    const t = Date.now(); taps.current = taps.current.filter(x => t - x < 1500).concat(t); const n = taps.current.length;
    if (n >= 7) { taps.current = []; LS.set('hpj-alpha', 1); setAlpha(true); setChannel(2); setCh(2); A.toast('גרסאות אלפא הופעלו'); check(); }
    else if (n >= 3) A.toast('עוד ' + (7 - n) + (7 - n === 1 ? ' לחיצה' : ' לחיצות') + ' לגרסאות אלפא');
  };
  const toggle = () => {
    if (on) { A.setPf({ ...pf, on: 0 }); const L = LN(); if (L) L.getPending().then(r => { const n = ((r && r.notifications) || []).map(x => ({ id: x.id })); if (n.length) return L.cancel({ notifications: n }); }).catch(() => { }); A.toast('ההתראות כובו'); return; }
    if (typeof Notification === 'undefined' && !LN()) { A.toast('המכשיר או הדפדפן לא תומכים בהתראות'); return; }
    askPerm().then(p => { A.setPerm(p); if (p === 'granted') { A.setPf({ ...pf, on: 1 }); if (pf.hour) DB.F.forEach(f => { if (A.rem[f.id] && !f.r) scheduleReminder(f); }); setTimeout(() => A.notify('ההתראות הופעלו – עדכונים יופיעו גם במגש ההתראות של המכשיר'), 80); } else A.toast('ההרשאה להתראות לא אושרה – אפשר לאשר אותה בהגדרות המכשיר'); });
  };
  const check = () => { setChk('בודק אם יש גרסה חדשה…'); A.checkUpdate().then(r => setChk(r === 'new' ? '' : r === 'none' ? 'האפליקציה מעודכנת לגרסה האחרונה' : 'לא ניתן לבדוק כרגע. נסו שוב בעוד כמה דקות')); };
  return html`<${FullPage} title="הגדרות" onClose=${onClose}><div class="stack">
    <section class="glass r-lg" style=${{ overflow: 'hidden' }}>
      <${Toggle} on=${on} set=${toggle} label="התראות" sub=${on ? 'פעילות – יופיעו גם במגש ההתראות של המכשיר' : A.perm === 'denied' ? 'חסומות בהגדרות המכשיר' : 'כבויות. הפעילו כדי לקבל תזכורות ועדכוני תוצאות'}/>
      ${!on && A.perm === 'denied' && NATIVE ? html`<div class="pad-x" style=${{ paddingBottom: 14 }}><button onClick=${() => { const P = plug('LiveActivity'); P && P.openNotifSettings ? P.openNotifSettings().catch(() => { }) : A.toast('אפשר לאשר התראות בהגדרות המכשיר › אפליקציות'); }} class="btn btn-red press" style=${{ width: '100%' }}>לפתיחת הגדרות ההתראות במכשיר</button></div>` : null}
      ${on ? html`<${Fragment}>
        <${Toggle} on=${pf.hour} set=${v => sp('hour', +v)} label="תזכורת שעה לפני שריקת הפתיחה"/>
        <${Toggle} on=${pf.quarter} set=${v => sp('quarter', +v)} label="עדכוני תוצאה בסיום כל רבע ובסיום המשחק"/>
        ${NATIVE ? html`<${Fragment}>
          <${Toggle} on=${pf.live !== 0} set=${v => sp('live', v ? 1 : 0)} label="כרטיס התוצאה בלייב" sub="במגש ההתראות ובמסך הנעילה"/>
          ${pf.live !== 0 ? html`<div class="pad-x" style=${{ paddingBottom: 12 }}><div class="t-s md" style=${{ marginBottom: 6 }}>איך להציג את התוצאה בלייב</div><${NbPick} pf=${pf} setPf=${A.setPf}/><div class="t-xs mu" style=${{ marginTop: 6 }}>${nbMode(pf) === 'c' ? 'כרטיס התוצאה של האפליקציה, עם סמלי הקבוצות.' : 'ב-Now Bar ובשורת הסטטוס (אנדרואיד 16 ומעלה, כש"עדכונים חיים" מופעלים לאפליקציה). במכשיר אחר – כרטיס התוצאה של האפליקציה.'}</div></div>
            <${Toggle} on=${pf.bg === 1} set=${v => { sp('bg', v ? 1 : 0); const P = plug('LiveActivity'); if (v && P && P.background) P.background({ ask: true }).catch(() => { }); }} label="להמשיך לעקוב ברקע גם כשהאפליקציה סגורה"/>
            <${Note}>כרטיס התוצאה ממשיך להתעדכן גם כשהאפליקציה סגורה לגמרי. אנדרואיד ישאל אם לאפשר פעולה בלי חיסכון בסוללה – כדאי לאשר.<//>` : null}<//>` : null}
        <${Toggle} on=${pf.sales} set=${v => sp('sales', +v)} label="פתיחת מכירת כרטיסים ומבצעים בחנות"/><//>` : null}
    </section>
    ${on ? html`<section class="glass r-lg" style=${{ overflow: 'hidden' }}><div class="sec-t pad-x" style=${{ paddingTop: 14, paddingBottom: 2 }}>לפי מפעל</div>
      ${Object.entries(COMPS).map(([k, l]) => html`<${Toggle} key=${k} on=${pf.comps[k]} set=${v => sp('comps', { ...pf.comps, [k]: +v })} label=${l}/>`)}</section>` : null}
    <section class="glass r-lg pad stack-s"><div class="row" style=${{ gap: 12 }}><img src="icons/club-logo.png" alt="" style=${{ width: 44, height: 44 }}/><div><h2 class="sec-t" style=${{ margin: 0 }}>על האפליקציה</h2><span class="unofficial">לא רשמית</span></div></div>
      <p class="t-s mu" style=${{ margin: 0 }}>אפליקציית אוהדים לא רשמית. היא לא שייכת למועדון הפועל ירושלים ולא פועלת מטעמו. הנתונים מהמקורות הרשמיים: איגוד הכדורסל, EuroLeague / EuroCup, 365scores, אתר המועדון וערוצי היוטיוב הרשמיים.</p></section>
    <section class="glass r-lg pad stack-s"><h2 class="sec-t" style=${{ margin: 0 }}>עדכוני אפליקציה</h2><button onClick=${tapVersion} class="t-s mu tap" style=${{ textAlign: 'right', userSelect: 'none', WebkitUserSelect: 'none' }}>גרסה מותקנת: <span class="num">${VERSION()}</span></button>
      ${NATIVE ? html`<${Fragment}><div class="t-s md">אילו עדכונים לקבל</div>
        <${Seg} items=${alpha ? [[0, 'רשמיות בלבד'], [1, 'גם בטא'], [2, 'גם אלפא']] : [[0, 'רשמיות בלבד'], [1, 'גם בטא']]} val=${ch} set=${v => { if (v === ch) return; setChannel(v); setCh(v); check(); }} label="ערוץ עדכונים"/>
        <div class="t-xs mu">${ch === 2 ? 'גרסאות אלפא הן המוקדמות ביותר – לפני הבטא והגרסה הרשמית – ועלולות לכלול יותר תקלות.' : ch === 1 ? 'גרסאות ניסיון מגיעות לפני הגרסה הרשמית ועלולות לכלול תקלות.' : 'יוצעו רק גרסאות רשמיות.'}</div>
        <button onClick=${check} class="btn btn-red press" style=${{ width: '100%' }}><${Icon} n="sync" s=${17}/>חיפוש עדכונים</button>${chk ? html`<div class="t-xs mu">${chk}</div>` : null}
        <div class="t-xs mu">האפליקציה בודקת עדכונים בכל פתיחה. כשיש גרסה חדשה צריך לעדכן כדי להמשיך.</div><//>` : html`<div class="t-xs mu">בגרסת הדפדפן האפליקציה מתעדכנת לבד.</div>`}</section>
  </div><//>`;
}
