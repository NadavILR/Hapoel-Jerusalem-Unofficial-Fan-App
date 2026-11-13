/* סביבת הריצה: Capacitor (אנדרואיד) או דפדפן, אחסון מקומי, זמנים ותאריכים בשעון ישראל */
export const CAP = window.Capacitor;
export const NATIVE = !!(CAP && CAP.isNativePlatform && CAP.isNativePlatform());
const PLG = {};
/* רכיב נייטיב לפי שם (נרשם פעם אחת); בדפדפן – null */
export const plug = n => NATIVE && CAP.registerPlugin ? (PLG[n] || (PLG[n] = CAP.registerPlugin(n))) : null;
export const H = 36e5, D = 864e5, MIN = 6e4;
export const VERSION = () => window.APP_VERSION || '';

/* ---------- תאריכים (Asia/Jerusalem). פורמטרים נשמרים – יצירה של Intl.DateTimeFormat יקרה ---------- */
const DTF_C = new Map();
export const dtf = (l, o) => { const k = l + '|' + JSON.stringify(o || {}); let f = DTF_C.get(k); if (!f) { f = new Intl.DateTimeFormat(l, o); DTF_C.set(k, f); } return f; };
const IL = { timeZone: 'Asia/Jerusalem' };
const part = (f, d, k) => (f.formatToParts(d).find(x => x.type === k) || {}).value || '';
export const fmt = {
  weekday: t => dtf('he-IL', { ...IL, weekday: 'long' }).format(t),
  wd: t => dtf('he-IL', { ...IL, weekday: 'short' }).format(t),
  dm: t => { const f = dtf('en-GB', { ...IL, day: '2-digit', month: '2-digit' }), d = new Date(t); return part(f, d, 'day') + '.' + part(f, d, 'month'); },
  dmy: t => { const f = dtf('en-GB', { ...IL, day: '2-digit', month: '2-digit', year: 'numeric' }), d = new Date(t); return part(f, d, 'day') + '.' + part(f, d, 'month') + '.' + part(f, d, 'year'); },
  day: t => +part(dtf('en-GB', { ...IL, day: 'numeric' }), new Date(t), 'day'),
  month: t => dtf('he-IL', { ...IL, month: 'long' }).format(t),
  monthKey: t => dtf('en-CA', { ...IL, year: 'numeric', month: '2-digit' }).format(t),
  time: t => dtf('he-IL', { ...IL, hour: '2-digit', minute: '2-digit', hour12: false }).format(t),
  dateKey: t => dtf('en-CA', { ...IL, year: 'numeric', month: '2-digit', day: '2-digit' }).format(t),
};
/* "יום ראשון · 08.11.2026 · 20:15" */
export const fdLong = f => fmt.weekday(f.t) + ' · ' + fmt.dmy(f.t) + ' · ' + (f.tbd ? 'שעה טרם נקבעה' : fmt.time(f.t));
/* "יום ראשון, 8.11 · 20:15" */
export const fdShort = f => fmt.weekday(f.t) + ', ' + fmt.dm(f.t) + (f.tbd ? ' · מועד לא סופי' : ' · ' + fmt.time(f.t));
/* שעון ישראל → UTC (מתחשב בשעון קיץ) */
export const ilToUtc = (y, m, d, h, mi) => {
  const want = Date.UTC(y, m - 1, d, h, mi); let t = want;
  const f = dtf('en-US', { ...IL, hourCycle: 'h23', year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: 'numeric' });
  for (let i = 0; i < 2; i++) { const q = f.formatToParts(new Date(t)), g = k => +(q.find(x => x.type === k) || {}).value; t += want - Date.UTC(g('year'), g('month') - 1, g('day'), g('hour'), g('minute')); }
  return t;
};
/* העונה מתחלפת ב-1 בספטמבר (שעון ישראל): "2026/27" */
export const seasonOf = t => { const p = dtf('en-US', { ...IL, year: 'numeric', month: 'numeric' }).formatToParts(new Date(t)), y = +p.find(x => x.type === 'year').value, m = +p.find(x => x.type === 'month').value, s = m >= 9 ? y : y - 1; return s + '/' + String((s + 1) % 100).padStart(2, '0'); };
export const SEASON = seasonOf(Date.now());
export const SEASON_Y = +SEASON.slice(0, 4);
export const ago = ts => {
  const m = Math.floor(Math.max(0, Date.now() - ts) / MIN); if (m < 1) return 'עכשיו'; if (m < 60) return m === 1 ? 'לפני דקה' : 'לפני ' + m + ' דקות';
  const h = Math.floor(m / 60); if (h < 24) return h === 1 ? 'לפני שעה' : h === 2 ? 'לפני שעתיים' : 'לפני ' + h + ' שעות';
  const d = Math.floor(h / 24); return d === 1 ? 'אתמול' : d === 2 ? 'לפני יומיים' : 'לפני ' + d + ' ימים';
};

/* ---------- אחסון מקומי. מפתחות של נתוני עונה נשמרים לכל עונה בנפרד ---------- */
const SEASONAL = ['hpj-res', 'hpj-hl', 'hpj-gs365', 'hpj-lsid', 'hpj-lsmiss', 'hpj-livegs', 'hpj-final', 'hpj-fired', 'hpj-rem'];
const key = k => SEASONAL.includes(k) ? k + ':' + SEASON : k;
export const LS = {
  get(k, d) { try { let v = localStorage.getItem(key(k)); if (v == null && SEASON === '2026/27' && SEASONAL.includes(k)) v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem(key(k), JSON.stringify(v)); } catch (e) { } },
};
/* אחרי שכל המסך נטען: עבודה ברקע בזמן פנוי */
export const idle = (f, t = 3000) => (window.requestIdleCallback || (g => setTimeout(g, t)))(f, { timeout: t * 2 });
export const visible = () => document.visibilityState !== 'hidden';
/* פונקציה שרצה עכשיו, כל ms, ובכל חזרה לאפליקציה; מחזירה ביטול */
export const every = (f, ms) => {
  const run = () => { if (visible()) f(); };
  run(); const i = setInterval(run, ms), v = () => { if (document.visibilityState === 'visible') f(); };
  document.addEventListener('visibilitychange', v);
  return () => { clearInterval(i); document.removeEventListener('visibilitychange', v); };
};
/* טעינת סקריפט (ספריות כבדות – רק כשצריך) */
export const jsLoad = src => new Promise((ok, no) => { if (document.querySelector('script[src="' + src + '"]')) return ok(); const e = document.createElement('script'); e.src = src; e.onload = ok; e.onerror = no; document.head.appendChild(e); });
