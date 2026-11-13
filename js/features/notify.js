/* התראות: נוסח זהה לחלק הנייטיב (GameAlerts), תזכורות שעה לפני, והרשאות */
import { NATIVE, plug, H, LS } from '../core/env.js';
import { ME, NGROUP } from '../data/constants.js';
import { compLabel } from '../data/store.js';
export const LN = () => plug('LocalNotifications');
export const NT = {
  home: f => f.h ? ME : f.o, away: f => f.h ? f.o : ME, match: f => NT.home(f) + ' מול ' + NT.away(f),
  score: (f, r) => NT.home(f) + ' ' + (f.h ? r[0] : r[1]) + ' - ' + (f.h ? r[1] : r[0]) + ' ' + NT.away(f),
  qt: q => q === 1 ? 'סיום רבע ראשון' : q === 2 ? 'מחצית' : q === 3 ? 'סיום רבע שלישי' : q === 4 ? 'סיום רבע רביעי' : 'סיום הארכה ' + (q - 4),
  hour: f => ({ title: 'יושב סופר את הדקות... עוד שעה מתחילים!!!', l1: compLabel(f), l2: NT.match(f) }),
  start: f => ({ title: 'המשחק התחיל', l1: compLabel(f), l2: NT.match(f) }),
  quarter: (f, r, q) => ({ title: NT.qt(q), l2: NT.score(f, r) }),
  fin: (f, r, ot) => ({ title: (ot ? 'סיום המשחק (לאחר הארכה) - ' : 'סיום המשחק – ') + (r[0] > r[1] ? 'ניצחון' : 'הפסד'), l2: NT.score(f, r) }),
  line: m => m.title + (m.l1 ? ' · ' + m.l1 : '') + ' · ' + m.l2,
  ln: m => ({ title: m.title, body: m.l2, largeBody: (m.l1 ? m.l1 + '\n' : '') + m.l2 }),
};
const base = { smallIcon: 'ic_stat_hpj', iconColor: '#D21919', group: NGROUP };
export const remId = f => Number(f.id) % 2147483647;
export function scheduleReminder(f) {
  const L = LN(); if (!L || f.tbd || f.t - H <= Date.now()) return;
  L.schedule({ notifications: [{ id: remId(f), ...NT.ln(NT.hour(f)), schedule: { at: new Date(f.t - H), allowWhileIdle: true }, ...base }] }).catch(() => { });
}
export function cancelReminder(f) { const L = LN(); if (L) L.cancel({ notifications: [{ id: remId(f) }] }).catch(() => { }); }
export const permOf = r => r && r.display === 'granted' ? 'granted' : r && r.display === 'denied' ? 'denied' : 'default';
export function checkPerm() {
  const L = LN(); if (L) return L.checkPermissions().then(permOf).catch(() => 'default');
  return Promise.resolve(typeof Notification !== 'undefined' ? Notification.permission : 'denied');
}
export function askPerm() {
  try {
    const L = LN(); if (L) return L.requestPermissions().then(permOf).catch(() => 'denied');
    if (typeof Notification === 'undefined') return Promise.resolve('denied');
    return Promise.resolve(Notification.requestPermission()).then(p => p || Notification.permission);
  } catch (e) { return Promise.resolve('denied'); }
}
/* התראה במגש של המכשיר */
export function deviceNotify(text, m) {
  try {
    const L = LN();
    if (L) { L.schedule({ notifications: [{ id: Math.floor(Math.random() * 2e9), ...(m ? NT.ln(m) : { title: ME, body: text }), ...base }] }).then(() => { const P = plug('LiveActivity'); if (P && P.group) return P.group(); }).catch(() => { }); return; }
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
    const o = { body: text, badge: 'icons/notify-badge.png', lang: 'he', dir: 'rtl', tag: 'hpj-' + Date.now(), data: { url: location.href } };
    const plain = () => { try { new Notification(ME, o); } catch (e) { } };
    if ('serviceWorker' in navigator) navigator.serviceWorker.getRegistration().then(r => r || Promise.race([navigator.serviceWorker.ready, new Promise(z => setTimeout(z, 4000))])).then(r => r ? r.showNotification(ME, o) : plain()).catch(plain);
    else plain();
  } catch (e) { }
}
/* איך מוצגת התוצאה בלייב: c – כרטיס, m/t – Now Bar (שורה / טקסט) */
export const nbMode = pf => pf && (pf.nb === 'm' || pf.nb === 't') ? pf.nb : 'c';
export const nbMsg = (x, pf) => !x || nbMode(pf) === 'c' || !(x.sdk >= 36) || x.shown === false ? '' : x.promoted ? 'התוצאה מוצגת עכשיו גם ב-Now Bar' : x.nowbar ? 'אנדרואיד לא הציג את התוצאה ב-Now Bar' : 'כדי לראות את התוצאה ב-Now Bar יש להפעיל "עדכונים חיים" לאפליקציה בהגדרות ההתראות של המכשיר';
export const firedOnce = (k, id) => { const m = LS.get(k, {}); if (m[id]) return false; m[id] = 1; LS.set(k, m); return true; };
export { NATIVE };
