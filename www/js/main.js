/* מעטפת האפליקציה: כותרת, סרגל ניווט, תפריט "עוד", לייב, התראות, תזכורות, עדכונים וחיבור לחלק הנייטיב */
import { html, useState, useEffect, useRef, useCallback, useLayoutEffect, Fragment, cx, portal } from './ui/html.js';
import { Icon } from './ui/icons.js';
import { AppCtx, useDB, VIDC, Sheet, Skel, Back } from './ui/kit.js';
import { storyImage } from './features/story.js';
import { MiniScore } from './ui/score.js';
import { NATIVE, LS, H, MIN, plug, SEASON, VERSION, every, visible } from './core/env.js';
import { ME, LINKS, COMPS, PF0 } from './data/constants.js';
import { DB, markResult, liveGsCache, egCode, compLabel, crestSrcs, jerseyMap, changed, gsOf } from './data/store.js';
import { refresh, loadOnce, status } from './data/refresh.js';
import { gsScan, squadOf } from './data/s365.js';
import { hlScan } from './data/media.js';
import { pollLive, savedLive, saveLive, mkDemo, demoTick, demoNames } from './data/live.js';
import { checkUpdate } from './data/updates.js';
import { NT, LN, permOf, askPerm, scheduleReminder, cancelReminder, deviceNotify, nbMode, nbMsg, firedOnce } from './features/notify.js';
import Home from './pages/home.js';
import Schedule from './pages/schedule.js';
import Standings from './pages/standings.js';
import Roster, { Player } from './pages/roster.js';
import Game from './pages/game.js';
import MyTickets from './pages/mytickets.js';
import External, { FR, EXT } from './pages/external.js';
import Settings from './pages/settings.js';
import Drawer from './pages/drawer.js';
import UpdateGate from './pages/updategate.js';

/* מצב כהה / בהיר לפי המכשיר */
const mq = window.matchMedia ? matchMedia('(prefers-color-scheme: dark)') : null;
const applyTheme = () => { const d = !!(mq && mq.matches); document.documentElement.classList.toggle('dark', d); const m = document.querySelector('meta[name=theme-color]'); if (m) m.content = d ? '#282828' : '#FFFFFF'; };
applyTheme(); if (mq) (mq.addEventListener ? mq.addEventListener('change', applyTheme) : mq.addListener(applyTheme));
/* כמה גללו: הסינון בכותרת מתכווץ */
let sq = 0; addEventListener('scroll', () => { if (sq) return; sq = requestAnimationFrame(() => { sq = 0; document.documentElement.classList.toggle('scrolled', scrollY > 24); }); }, { passive: true });

const NAV = [['home', 'בית', 'home'], ['sched', 'משחקים', 'cal'], ['stand', 'טבלאות', 'table'], ['roster', 'סגל', 'users'], ['more', 'עוד', 'more']];
/* "כרטיסים ומנוי": קודם ההתחברות באתר המועדון (אותה התחברות לשני האתרים), ואחריה עמוד עם שני הכפתורים */
const MORE = [['auth', 'כרטיסים ומנוי', 'tix', 'רכישת כרטיסים ואזור המנויים'], ['mytix', 'הכרטיסים שלי', 'qr', 'כרטיסים ומנויים ששמרת'], ['shop', 'חנות', 'bag', 'החנות הרשמית']];
const ACCT = [['mem', 'מנוי', 'card', 'אזור המנויים'], ['tix', 'כרטיסים', 'tix', 'רכישת כרטיסים למשחקים']];
const AUTH = 'https://auth.hapoel.co.il/', authed = u => { try { return /^\/(personal-area|user-details)/.test(new URL(u).pathname) && new URL(u).hostname === 'auth.hapoel.co.il'; } catch (e) { return false; } };
const TITLES = { more: 'עוד', sched: 'משחקים ותוצאות', stand: 'טבלאות', roster: 'סגל הקבוצה', mytix: 'הכרטיסים שלי', acct: 'כרטיסים ומנוי', auth: 'התחברות', tix: 'כרטיסים', shop: 'חנות המועדון', mem: 'מנוי ואזור אישי' };
const TK = () => plug('Tickets');
/* העמודים שנפתחים מעמוד "עוד" – כפתור החזרה שלהם מחזיר לשם */
const SUB = ['mytix', 'acct', 'auth', 'tix', 'shop', 'mem'], SITES = ['auth', 'tix', 'shop', 'mem'];
/* לאן חוזרים מכל עמוד: הכרטיסים והמנוי – לעמוד "כרטיסים ומנוי", כל השאר – ל"עוד" */
const PARENT = k => k === 'tix' || k === 'mem' ? 'acct' : 'more';

/* משיכה למטה לרענון */
function PullRefresh({ onRefresh, off }) {
  const [d, setD] = useState(0), [busy, setBusy] = useState(false), st = useRef(null), offR = useRef(off), fnR = useRef(onRefresh), busyR = useRef(false), dR = useRef(0), topR = useRef(0);
  offR.current = off; fnR.current = onRefresh;
  const set = v => { dR.current = v; setD(v); };
  useEffect(() => {
    const start = e => { if (offR.current || busyR.current || window.scrollY > 0 || e.touches.length !== 1) return; for (let el = e.target; el && el !== document.body; el = el.parentElement) if (el.scrollTop > 0) return; const t = e.touches[0]; st.current = { x: t.clientX, y: t.clientY, on: false }; };
    const move = e => {
      const s = st.current; if (!s) return; const t = e.touches[0], dy = t.clientY - s.y, dx = t.clientX - s.x;
      if (!s.on) { if (dy > 8 && dy > Math.abs(dx) * 1.5 && window.scrollY <= 0) { s.on = true; const h = document.getElementById('hd'); topR.current = h ? h.getBoundingClientRect().bottom : 0; } else if (Math.abs(dx) > 8 || dy < -4) { st.current = null; return; } else return; }
      set(Math.max(0, Math.min(120, dy * .5))); if (e.cancelable) e.preventDefault();
    };
    const end = () => { const s = st.current; st.current = null; if (!s || !s.on) return; if (dR.current >= 64) { busyR.current = true; setBusy(true); set(64); const t0 = Date.now(); Promise.resolve().then(() => fnR.current()).catch(() => { }).finally(() => setTimeout(() => { busyR.current = false; setBusy(false); set(0); }, Math.max(0, 700 - (Date.now() - t0)))); } else set(0); };
    addEventListener('touchstart', start, { passive: true }); addEventListener('touchmove', move, { passive: false }); addEventListener('touchend', end); addEventListener('touchcancel', end);
    return () => { removeEventListener('touchstart', start); removeEventListener('touchmove', move); removeEventListener('touchend', end); removeEventListener('touchcancel', end); };
  }, []);
  /* במקום אייקון שצף: סמל המועדון בכותרת מסתובב עם המשיכה, פס אדום מתחת לכותרת מתמלא עד נקודת הרענון,
     והתוכן יורד מעט עם האצבע. בזמן הרענון הסמל קופץ כמו כדור בכדרור והפס זורם */
  useLayoutEffect(() => {
    const de = document.documentElement, k = Math.min(d / 64, 1.4);
    de.style.setProperty('--pull', String(busy ? 1 : k));
    de.classList.toggle('pulling', !!st.current && d > 0 && !busy);
    de.classList.toggle('pull-on', d > 0 || busy);
    de.classList.toggle('pull-ready', d >= 64 && !busy);
    de.classList.toggle('refreshing', busy);
  }, [d, busy]);
  useEffect(() => () => { const de = document.documentElement; de.classList.remove('pulling', 'pull-on', 'pull-ready', 'refreshing'); de.style.removeProperty('--pull'); }, []);
  return d || busy ? html`<div class="pull-bar" aria-hidden="true"></div>` : null;
}

function App() {
  useDB();
  /* פתיחה ישירה ללשונית (#sched וכו׳ – מסרגל הניווט שמעל אתר הכרטיסים) */
  const [pg, setPg] = useState(() => { const h = (location.hash || '').slice(1); try { if (h) history.replaceState(null, '', location.pathname + location.search); } catch (e) { } return ['sched', 'stand', 'roster', 'mytix', 'shop', 'mem'].includes(h) ? h : 'home'; });
  useEffect(() => { const f = () => { const h = (location.hash || '').slice(1); if (['home', 'sched', 'stand', 'roster', 'mytix'].includes(h)) { retR.current = false; setPg(h); setGame(null); setPlayer(null); window.scrollTo(0, 0); try { history.replaceState(null, '', location.pathname + location.search); } catch (e) { } } }; addEventListener('hashchange', f); return () => removeEventListener('hashchange', f); }, []);
  const [arg, setArg] = useState(null), [game, setGame] = useState(null), [player, setPlayer] = useState(null), [menu, setMenu0] = useState(false), [menuOut, setMenuOut] = useState(false), [settings, setSettings] = useState(false), [drawer, setDrawer] = useState(false), [upd, setUpd] = useState(null);
  /* כל עמוד נפתח בראש העמוד: גם אחרי שהתוכן שלו נטען. רק "חזרה" מעמוד משחק ללוח המשחקים משחזרת את המקום שבו היו */
  const retR = useRef(false);
  useLayoutEffect(() => { try { history.scrollRestoration = 'manual'; } catch (e) { } }, []);
  /* כל עמוד נפתח בראש העמוד: גוללים למעלה מיד, ושוב כמה פעמים בשבריר השנייה הראשון (אם התוכן עוד משנה גובה) – אלא אם המשתמש כבר גולל */
  useLayoutEffect(() => {
    const keep = () => retR.current && pg === 'sched' && !game && !player; if (keep()) return;
    let user = false; const u = () => { user = true; }, top = () => { if (!user && !keep() && window.scrollY) window.scrollTo(0, 0); };
    window.scrollTo(0, 0); addEventListener('touchstart', u, { passive: true }); addEventListener('wheel', u, { passive: true });
    const r = requestAnimationFrame(top), ts = [80, 200, 450].map(d => setTimeout(top, d));
    return () => { cancelAnimationFrame(r); ts.forEach(clearTimeout); removeEventListener('touchstart', u); removeEventListener('wheel', u); };
  }, [pg, arg, game && game.id, player && player[1]]);
  const [toasts, setToasts] = useState([]), [rem, setRem] = useState(() => LS.get('hpj-rem', {})), [notes, setNotes] = useState(() => LS.get('hpj-notes', [])), [pf, setPf] = useState(() => ({ ...PF0, ...LS.get('hpj-pf', {}) }));
  const [perm, setPerm] = useState(NATIVE ? 'default' : typeof Notification !== 'undefined' ? Notification.permission : 'denied');
  const [live, setLive] = useState(savedLive), liveR = useRef(live), [demo, setDemo] = useState(null), taps = useRef([]);
  useEffect(() => LS.set('hpj-notes', notes.slice(0, 30)), [notes]); useEffect(() => LS.set('hpj-pf', pf), [pf]); useEffect(() => LS.set('hpj-rem', rem), [rem]);
  const toast = useCallback(t => { const id = Math.random(); setToasts(x => [...x.slice(-2), { id, t }]); setTimeout(() => setToasts(x => x.filter(y => y.id !== id)), 3500); }, []);

  /* תפריט "עוד": כשנסגר נשאר על המסך לרגע לאנימציית הסגירה (ההפך של אנימציית הפתיחה) */
  const menuWas = useRef(false);
  /* useLayoutEffect: מתחלף לפני שהמסך מצויר – בלי פריים שבו התפריט נעלם ומופיע שוב */
  useLayoutEffect(() => { if (menuWas.current && !menu) { setMenuOut(true); const t = setTimeout(() => setMenuOut(false), 200); menuWas.current = false; return () => clearTimeout(t); } menuWas.current = menu; if (menu) setMenuOut(false); }, [menu]);
  /* ---------- נתונים ---------- */
  useEffect(() => { refresh(); loadOnce(); }, []);
  useEffect(() => every(() => hlScan().then(n => { if (n) changed(); }).catch(() => { }), 10 * MIN), [DB.F.length]);
  useEffect(() => every(() => gsScan().then(([n, res]) => { res.forEach(([id, r]) => markResult(id, r)); if (n) changed(); }).catch(() => { }), 15 * MIN), [DB.F.length]);

  /* ---------- התראות ---------- */
  useEffect(() => { const L = LN(); if (!L) return; L.checkPermissions().then(r => { const p = permOf(r); setPerm(p);
  }).catch(() => { }); }, []);
  /* פתיחה ראשונה באנדרואיד: המכשיר שואל על התראות ועל פעולה ברקע (MainActivity), והתוצאה מגיעה לכאן –
     ההתראות והמעקב ברקע מופעלים באפליקציה מיד */
  useEffect(() => { window.hpjFirstRun = granted => { setPerm(granted ? 'granted' : 'denied'); if (granted) setPf(x => ({ ...x, on: 1, bg: 1 })); }; return () => { delete window.hpjFirstRun; }; }, []);
  const nfR = useRef(); nfR.current = (t, c, k, m) => {
    if (pf.on === 0) return; if (k && !pf[k]) return; if (c && pf.comps && !pf.comps[c]) return;
    setNotes(n => [{ id: Date.now() + Math.random(), t, ts: Date.now(), r: 0 }, ...n].slice(0, 30)); toast(t);
    /* בזמן משחק החלק הנייטיב שולח בעצמו את עדכוני הרבעים (גם כשהאפליקציה סגורה) */
    if (!(k === 'quarter' && pf.live !== 0 && plug('LiveActivity'))) deviceNotify(t, m);
  };
  const notify = useCallback((t, c, k, m) => nfR.current(t, c, k, m), []);
  /* תזכורות בדפדפן (באנדרואיד – מתוזמנות במערכת) */
  useEffect(() => { if (LN()) return; const chk = () => { const now = Date.now(), fired = LS.get('hpj-fired', {}); let ch = 0; for (const f of DB.F) { if (!rem[f.id] || f.r || fired[f.id] || f.tbd) continue; if (now >= f.t - H && now < f.t) { fired[f.id] = 1; ch = 1; const m = NT.hour(f); notify(NT.line(m), f.c, 'hour', m); } } if (ch) LS.set('hpj-fired', fired); }; chk(); const i = setInterval(chk, 3e4); return () => clearInterval(i); }, [rem, DB.F, pf]);
  const togRem = f => { const on = !rem[f.id]; if (on && pf.hour && pf.on !== 0) scheduleReminder(f); else cancelReminder(f); setRem(r => ({ ...r, [f.id]: on })); toast(on ? 'תזכורת שעה לפני המשחק הוגדרה' : 'התזכורת בוטלה'); };

  /* ---------- לייב: מ-10 דקות לפני שריקת הפתיחה ועד הסיום ---------- */
  useEffect(() => { if (!demo) return; const i = setInterval(() => setDemo(d => d && demoTick(d)), 4000); return () => clearInterval(i); }, [!!demo]);
  useEffect(() => {
    let stop = false, tm = null;
    const tick = async () => {
      let wait = 60000;
      try {
        const n = DB.F.filter(f => !f.r).sort((a, b) => a.t - b.t)[0];
        if (n && Date.now() >= n.t - 10 * MIN && Date.now() < n.t + 5 * H && visible()) {
          wait = 20000; const prev = liveR.current, L = await pollLive(n, prev && prev.app === n.id ? prev : null).catch(() => null);
          if (L && !stop) {
            if (L.status === 'live') wait = 3000; else if (L.status === 'pre' && Math.abs(Date.now() - n.t) < 10 * MIN) wait = 5000;
            liveR.current = L; setLive(L); saveLive(L, n);
            if (L.status === 'live' && (!prev || prev.app !== n.id || prev.status !== 'live')) { const m = NT.start(n); notify(NT.line(m), n.c, 'quarter', m); }
            if (L.status === 'end') {
              if (firedOnce('hpj-final', n.id)) { const m = NT.fin(n, L.r, (L.q || []).length > 4); notify(NT.line(m), n.c, 'quarter', m); }
              if (L.gs) { const c = liveGsCache(); c[n.id] = { ...L.gs, src: L.src }; LS.set('hpj-livegs', c); }
              markResult(n.id, L.r); hlScan().catch(() => { }); return;
            }
          }
        }
      } catch (e) { }
      if (!stop) tm = setTimeout(tick, wait);
    };
    tick(); const v = () => { if (document.visibilityState === 'visible') { clearTimeout(tm); tick(); } };
    document.addEventListener('visibilitychange', v);
    return () => { stop = true; clearTimeout(tm); document.removeEventListener('visibilitychange', v); };
  }, [DB.F]);
  const L = demo || live;

  /* ---------- חיבור לחלק הנייטיב: המשחקים הבאים לכרטיס הלייב ולווידג'ט ---------- */
  useEffect(() => {
    const P = plug('LiveActivity'); if (!P || !DB.ok) return;
    const sync = () => {
      const games = DB.F.filter(f => !f.r && !f.tbd && f.t > Date.now() - 3 * H && (!f.c || !pf.comps || pf.comps[f.c])).sort((a, b) => a.t - b.t).slice(0, 8)
        .map(f => ({ t: f.t, c: f.c || '', eg: f.c === 'euro' ? egCode(f) || 0 : 0, l: compLabel(f), o: f.o || '', h: f.h ? 1 : 0, v: f.v || '', img: crestSrcs(f.o)[0] || '' }));
      P.sync({ on: pf.on !== 0 && pf.live !== 0, games, jer: jerseyMap(), bg: pf.bg === 1, quarter: !!pf.quarter, comps: pf.comps || {}, nb: nbMode(pf) }).then(() => P.group && P.group()).catch(() => { });
    };
    sync(); const v = () => { if (document.visibilityState === 'visible') sync(); }; document.addEventListener('visibilitychange', v); return () => document.removeEventListener('visibilitychange', v);
  }, [DB.F, DB.ok, pf]);
  /* מצב בדיקה גם בכרטיס הלייב הנייטיב */
  const demoKey = useRef('');
  useEffect(() => {
    const P = plug('LiveActivity'); if (!P) return;
    if (!demo) { if (demoKey.current) { demoKey.current = ''; P.demo({}).catch(() => { }); } return; }
    const n = DB.F.find(f => f.id === demo.app), op = n ? n.o : '', r = demo.r || [0, 0];
    const me = { name: ME, score: r[0], img: 'https://imagecache.365scores.com/image/upload/f_png,w_96,h_96,c_limit,q_auto:eco,dpr_2,d_Competitors:default1.png/v8/Competitors/634' }, them = { name: op, score: r[1], img: crestSrcs(op)[0] || null }, p0 = (demo.pbp || [])[0];
    const o = { t: n ? n.t : 0, comp: (n && COMPS[n.c]) || '', play: p0 ? (p0.clock ? p0.clock + ' · ' : '') + p0.text : '', status: demo.st || '', clock: demo.clock || '', home: n && !n.h ? them : me, away: n && !n.h ? me : them }, k = JSON.stringify(o), first = !demoKey.current;
    if (k === demoKey.current) return; demoKey.current = k;
    P.demo({ ...o, check: first }).then(x => { if (first && x && x.shown === false) toast('כדי לראות את התוצאה בלייב במגש ההתראות יש לאשר התראות לאפליקציה בהגדרות המכשיר'); else if (first && nbMsg(x, pf)) toast(nbMsg(x, pf)); }).catch(() => { });
  }, [demo]);

  /* ---------- ניווט ---------- */
  const botPx = () => { const n = document.getElementById('nav'); return Math.round((n ? n.offsetHeight : 64) * (window.devicePixelRatio || 1)); };
  /* האתר נפתח מתחת לכותרת של האפליקציה (בה "חזרה" במקום הסמל) */
  const topPx = () => { const h = document.getElementById('hd'); return Math.round((h ? h.getBoundingClientRect().bottom : 64) * (window.devicePixelRatio || 1)); };
  /* כרטיסים / חנות / מנוי באנדרואיד: חלון נייטיב לכל אתר (עם משיכה לרענון), מעל האפליקציה וסרגל הניווט גלוי מתחתיו */
  const OVL = { auth: AUTH, tix: LINKS.tickets, shop: EXT.shop, mem: EXT.mem }, isOvl = k => !!(TK() && OVL[k]);
  const ovlShow = (k, home) => { const T = TK(); if (T) return T.show({ key: k, url: OVL[k], bottom: botPx(), top: topPx(), home }); };
  useEffect(() => { const T = TK(); if (!T) return; T.preload({ url: LINKS.tickets }).catch(() => { }); let h; try { h = T.addListener('closed', e => { const k = (e && e.key) || 'tix'; setPg(x => x === k ? PARENT(k) : x); }); } catch (e) { }
    /* אחרי ההתחברות (אתר ההתחברות עובר לאזור האישי): סוגרים אותו ופותחים את עמוד "כרטיסים ומנוי" */
    try { T.addListener('url', e => { if (e && e.key === 'auth' && authed(e.url)) setPg(x => { if (x !== 'auth') return x; T.hide({ key: 'auth' }).catch(() => { }); return 'acct'; }); }); } catch (e) { } return () => { try { h && h.then ? h.then(r => r.remove()) : h && h.remove && h.remove(); } catch (e) { } }; }, []);
  const setMenu = v => { const T = TK(); if (v && isOvl(pg)) T.hide().catch(() => { }); if (!v && menu && isOvl(pg)) ovlShow(pg, false).catch(() => { }); setMenu0(v); };
  const go = (p, a) => {
    retR.current = false; setMenu0(false); const T = TK();
    if (isOvl(p)) { ovlShow(p, pg === p || p === 'auth').catch(() => location.assign(OVL[p])); setPg(p); setGame(null); setPlayer(null); return; }
    if (p === 'auth') p = 'acct';
    if (p === 'tix') { window.open(LINKS.tickets, '_blank', 'noopener'); return; }
    if (T) T.hide().catch(() => { });
    FR.n = 0; FR.skip = 0; setPg(p); setArg(a || null); setGame(null); setPlayer(null); window.scrollTo(0, 0);
  };
  /* "חזרה" בכותרת של אתר: קודם לעמוד הקודם בתוך האתר, ומהעמוד הראשון שלו – לעמוד "עוד" */
  const siteBack = () => { const T = TK(); if (isOvl(pg)) { T.back({ key: pg }).catch(() => go(PARENT(pg))); return; } if (FR.n > 1) { FR.n--; FR.skip++; history.back(); return; } go(PARENT(pg)); };
  /* מרכז ההתראות נפתח מעל האתר: החלון של האתר מוסתר בינתיים */
  useEffect(() => { if (!isOvl(pg)) return; const T = TK(); if (drawer) T.hide().catch(() => { }); else ovlShow(pg, false).catch(() => { }); }, [drawer]);
  const openGame = g => { retR.current = false; const T = TK(); if (isOvl(pg)) T.hide().catch(() => { }); setGame(g); window.scrollTo(0, 0); };
  const openPlayer = p => { retR.current = false; setGame(null); if (pg !== 'roster') setPg('roster'); setPlayer(p); window.scrollTo(0, 0); };
  const back = () => { retR.current = true; if (game) setGame(null); else if (player) setPlayer(null); };
  /* לחיצה על כרטיס הלייב במגש ההתראות: האפליקציה נפתחת בעמוד המשחק */
  const openAt = t => { retR.current = false; const f = DB.F.slice().sort((a, b) => Math.abs(a.t - t) - Math.abs(b.t - t))[0]; if (!f || Math.abs(f.t - t) > 6 * H) return; const T = TK(); if (T) T.hide().catch(() => { }); setSettings(false); setMenu0(false); setDrawer(false); setPlayer(null); setPg('home'); setGame(f); window.scrollTo(0, 0); };
  const openR = useRef(); openR.current = openAt;
  useEffect(() => { const P = plug('LiveActivity'); if (!P || !P.takeOpen) return; const f = () => P.takeOpen().then(r => { if (r && r.t) openR.current(+r.t); }).catch(() => { }); f(); const v = () => { if (document.visibilityState === 'visible') f(); }; document.addEventListener('visibilitychange', v); return () => document.removeEventListener('visibilitychange', v); }, [DB.ok]);

  /* ---------- עדכונים ---------- */
  const checkUpd = useCallback(async () => { const r = await checkUpdate(); if (r.s === 'new') setUpd(r.r); return r.s; }, []);
  useEffect(() => { if (!NATIVE) return; checkUpd(); const f = () => { if (document.visibilityState === 'visible') checkUpd(); }; document.addEventListener('visibilitychange', f); return () => document.removeEventListener('visibilitychange', f); }, []);
  useEffect(() => { if (!upd) return; setSettings(false); setDrawer(false); setMenu0(false); const T = TK(); if (T) T.hide().catch(() => { }); }, [upd]);

  /* ---------- כפתור "חזור" ---------- */
  const backR = useRef(); backR.current = () => {
    if (VIDC.close) { VIDC.close(); return true; } if (menu) { setMenu(false); return true; } if (settings) { setSettings(false); return true; } if (drawer) { setDrawer(false); return true; }
    if (game) { setGame(null); return true; } if (player) { setPlayer(null); return true; }
    if (SUB.includes(pg)) { go(PARENT(pg)); return true; }
    if (pg !== 'home') { go('home'); return true; } return false;
  };
  useEffect(() => { window.hpjBack = () => backR.current(); }, []);
  const atRoot = !settings && !drawer && !game && !player && !menu && pg === 'home', skipPop = useRef(false);
  useEffect(() => { if (NATIVE) return; const f = () => { if (skipPop.current) { skipPop.current = false; return; } backR.current(); }; addEventListener('popstate', f); return () => removeEventListener('popstate', f); }, []);
  useEffect(() => { if (NATIVE) return; const marked = !!(history.state && history.state.hpj); try { if (!atRoot && !marked) history.pushState({ hpj: 1 }, ''); else if (atRoot && marked) { skipPop.current = true; history.back(); } } catch (e) { } }, [atRoot]);
  /* גלילה נעולה מאחורי מסכים מלאים */
  useEffect(() => { const lock = settings || !!upd; document.documentElement.style.overflow = lock ? 'hidden' : ''; }, [settings, upd]);

  /* ---------- מצב בדיקה: 3 לחיצות על השורה שבתחתית מסך הבית ---------- */
  const nx = DB.F.filter(f => !f.r).sort((a, b) => a.t - b.t)[0];
  const tapFoot = () => {
    if (pg !== 'home' || game) return; const t = Date.now(); taps.current = taps.current.filter(x => t - x < 700).concat(t); if (taps.current.length < 3) return; taps.current = [];
    if (demo) { setDemo(null); toast('מצב בדיקה כובה'); }
    else if (nx) { const n = nx; setDemo(mkDemo(n, gsOf)); toast('מצב בדיקה: המשחק הבא מוצג כמשחק בלייב'); squadOf(n).then(s => setDemo(d => d && d.app === n.id ? demoNames(d, s) : d)).catch(() => { }); }
  };

  /* ---------- גובה הכותרת (לכפתורי חזרה ולתוצאה הנצמדת) ---------- */
  useLayoutEffect(() => { const h = document.getElementById('hd'); if (!h) return; const set = () => document.documentElement.style.setProperty('--hdh', h.getBoundingClientRect().height + 'px'); set(); let ro = null; try { ro = new ResizeObserver(set); ro.observe(h); } catch (e) { } return () => ro && ro.disconnect(); }, []);

  /* ---------- סטורי: בחירת גרסה וחיווי הכנה ---------- */
  const [pick, setPick] = useState(null), [busy, setBusy] = useState(null);
  const storyUI = { toast, pick: g => new Promise(done => setPick({ g, done })), busy: () => { setBusy(0); return { set: p => setBusy(p), done: () => setBusy(null) }; } };

  /* פס הלייב: בכל העמודים, חוץ מעמוד המשחק עצמו וממסך הבית (שם הכרטיס האדום כבר מציג את התוצאה) */
  const dockL = !upd && L && L.status === 'live' && !(game && game.id === L.app) && !(pg === 'home' && !game && !player) ? L : null, dockG = dockL ? DB.F.find(f => f.id === dockL.app) : null;
  useLayoutEffect(() => { document.documentElement.classList.toggle('ld-on', !!(dockL && dockG)); }, [!!(dockL && dockG)]);
  useEffect(() => { if (isOvl(pg) && !menu) { const x = ovlShow(pg, false); if (x && x.catch) x.catch(() => { }); } }, [!!(dockL && dockG)]);
  const navOn = pg === 'more' || SUB.includes(pg) ? 'more' : pg, navI = NAV.findIndex(x => x[0] === navOn), unread = notes.filter(n => !n.r).length, ext = ['shop', 'mem', 'auth'].includes(pg);
  const A = { returning: () => retR.current, pg, go, openGame, openPlayer, back, toast, rem, togRem, notes, setNotes, pf, setPf, perm, setPerm, notify, live: L, refresh, checkUpdate: checkUpd, openSettings: () => setSettings(true), storyUI };
  /* בעמוד משחק: המפעל והשלב ככותרת, והמשחק (בשמות המלאים) ככותרת משנה */
  const title = game ? compLabel(game) : player ? player[1] : TITLES[pg], gsub = game ? (game.h ? ME + ' – ' + game.o : game.o + ' – ' + ME) : '';
  const page = game ? html`<${Game} g=${game}/>` : player ? html`<${Player} p=${player}/>` : pg === 'home' ? html`<${Home}/>` : pg === 'sched' ? html`<${Schedule}/>` : pg === 'stand' ? html`<${Standings} arg=${arg}/>` : pg === 'roster' ? html`<${Roster}/>` : pg === 'more' ? html`<${MorePage} go=${go}/>` : pg === 'mytix' ? html`<${Back} onClick=${() => go('more')}/><${MyTickets}/>` : pg === 'acct' ? html`<${MorePage} go=${go} items=${ACCT}/>` : pg === 'tix' || isOvl(pg) ? html`<div class="t-s mu" style=${{ textAlign: 'center', padding: 40 }}>טוען את האתר…</div>` : html`<${External} k=${pg}/>`;
  /* בזמן רענון (וגם בפתיחה): שלד במקום הנתונים. העמוד עצמו נשאר מוסתר, כדי שהמסננים בכותרת לא ייעלמו */
  const skel = status.loading && !game && !player && ['home', 'sched', 'stand', 'roster'].includes(pg);
  const skeleton = pg === 'home' ? html`<div class="stack" aria-busy="true" aria-label="טוען"><div class="sk" style=${{ height: 420, borderRadius: 16 }}></div><div class="grid2"><div class="sk" style=${{ height: 92 }}></div><div class="sk" style=${{ height: 92 }}></div></div><div class="sk" style=${{ height: 260 }}></div></div>` : html`<${Skel} n=${8} h=${pg === 'roster' ? 120 : 64}/>`;

  return html`<${AppCtx.Provider} value=${A}>
    <${PullRefresh} onRefresh=${refresh} off=${!!(ext || drawer || settings || menu || upd || pg === 'more' || pg === 'acct')}/>
    <header id="hd" class="bar"><div class="wrap">
      <div class="hd-in">${(SITES.includes(pg) || pg === 'acct') && !game && !player ? html`<button onClick=${siteBack} class="back glass-2 press" style=${{ position: 'static' }}><${Icon} n="back" s=${18}/><span>חזרה</span></button>` : html`<img src="icons/club-logo.png" alt="הפועל ירושלים" class="logo"/>`}
        <div class="grow">${title ? html`<h1 class="ttl trunc" style=${{ margin: 0 }}>${title}</h1>${game ? html`<div class="sub trunc">${gsub}</div>` : player || pg === 'more' || SUB.includes(pg) ? null : html`<div class="sub">עונת ${SEASON}</div>`}` : html`<h1 class="ttl" style=${{ margin: 0 }}>${ME}</h1><span class="unofficial">אפליקציית אוהדים · לא רשמית</span>`}</div>
        ${pg === 'home' && !game && !player ? html`<button onClick=${() => setSettings(true)} class="icon-btn press" aria-label="הגדרות"><${Icon} n="cog" s=${22}/></button>` : null}
        <button onClick=${() => setDrawer(true)} class="icon-btn press" aria-label=${'התראות' + (unread ? ', ' + unread + ' חדשות' : '')}><${Icon} n="bell" s=${22}/>${unread > 0 ? html`<span class="badge">${unread}</span>` : null}</button>
      </div>
      <div id="hd-slot"></div></div></header>
    <div class="toasts" aria-live="polite">${toasts.map(t => html`<div key=${t.id} class="toast">${t.t}</div>`)}</div>
    <main class="wrap"><div key=${game ? 'g' + game.id : player ? 'p' + player[1] : 'p' + pg} class=${ext ? '' : 'pg-in'}>${skel ? skeleton : null}${skel && pg === 'home' ? null : html`<div hidden=${skel}>${page}</div>`}</div>
      ${ext || pg === 'more' || pg === 'acct' ? null : html`<footer class="col" style=${{ alignItems: 'center', gap: 8, marginTop: 28, textAlign: 'center' }}>
        <span onClick=${tapFoot} class="glass-2 r-md t-xs mu" style=${{ padding: '6px 16px', userSelect: 'none', lineHeight: 1.6 }}>${demo ? html`<b class="red">מצב בדיקה · </b>` : null}אפליקציית אוהדים לא רשמית<br/>נדב רובינסון © גרסה ${VERSION()}</span>
        ${game || player || pg === 'stand' ? null : html`<a href=${LINKS.club} target="_blank" rel="noopener noreferrer" class="glass-2 r-full t-xs mu tap row" style=${{ padding: '4px 16px', textDecoration: 'underline' }}>לאתר הרשמי של המועדון</a>`}</footer>`}</main>
    <nav id="nav" class="bar" aria-label="ניווט ראשי" ref=${el => { if (el) document.documentElement.style.setProperty('--navh', el.offsetHeight + 'px'); }}>
      ${dockL && dockG ? html`<button class="dock press" onClick=${() => openAt(dockG.t)} aria-label=${'משחק בלייב: ' + ME + ' ' + dockL.r[0] + ' – ' + dockL.r[1] + ' ' + dockG.o + '. לעמוד המשחק'}><${MiniScore} g=${dockG} L=${dockL}/></button>` : null}
      <div class="nav-in"><span class="nav-ind" style=${{ transform: `translateX(${-navI * 100}%)`, opacity: navI < 0 ? 0 : 1 }}></span>
        ${NAV.map(([k, l, ic]) => { const on = k === navOn; return html`<button key=${k} class=${cx('nav-b', on && 'on')} aria-current=${on && k !== 'more' ? 'page' : undefined} onClick=${() => go(k)}><span class="pill"><${Icon} n=${ic} s=${22} w=${on ? 2.3 : 2}/></span>${l}</button>`; })}</div></nav>
    ${drawer ? html`<${Drawer} onClose=${() => setDrawer(false)}/>` : null}
    ${settings ? html`<${Settings} onClose=${() => setSettings(false)}/>` : null}
    ${pick ? html`<${StoryPick} g=${pick.g} done=${v => { pick.done(v); setPick(null); }}/>` : null}
    ${busy != null ? html`<div class="glass-3 r-full t-s b" style=${{ position: 'fixed', left: '50%', transform: 'translateX(-50%)', bottom: 'calc(var(--navh, 72px) + 16px)', zIndex: 60, padding: '10px 18px' }}>מכין סרטון לסטורי… ${Math.round(busy * 100)}%</div>` : null}
    ${upd ? html`<${UpdateGate} u=${upd}/>` : null}
  <//>`;
}

/* עמוד "עוד": ארבעה כפתורים גדולים, שתי שורות של שניים, שממלאים את המסך */
function MorePage({ go, items = MORE }) {
  /* מוצג ישירות במסך (לא בתוך העמוד, שיש לו אנימציית כניסה), כדי שהמרווחים יחושבו מהכותרת ומסרגל הניווט */
  return portal(html`<div class=${'more-grid fade-in n' + items.length}>${items.map(([k, l, ic, sub]) => html`<button key=${k} onClick=${() => go(k)} class="more-b glass press">
    <span class="ic-circle" style=${{ width: 64, height: 64 }}><${Icon} n=${ic} s=${30}/></span>
    <span class="t-l xb">${l}</span><span class="t-s mu">${sub}</span></button>`)}</div>`);
}

/* בחירת גרסת הסטורי של המשחק הבא, עם תצוגה מקדימה של שתי הגרסאות */
function StoryPick({ g, done }) {
  const [im, setIm] = useState([null, null]);
  useEffect(() => { let on = true; Promise.all([storyImage(g, null), storyImage(g, null, { noCd: true })]).then(([a, b]) => { if (on) setIm([a.toDataURL('image/jpeg', .7), b.toDataURL('image/jpeg', .7)]); }).catch(() => { }); return () => { on = false; }; }, []);
  return html`<${Sheet} onClose=${() => done(null)} label="שיתוף לסטורי"><div class="stack-s" style=${{ paddingBottom: 6 }}>
    <h2 class="t-l" style=${{ margin: 0 }}>איזה סטורי לשתף?</h2><p class="t-s mu" style=${{ margin: 0 }}>בגרסה בלי הספירה לאחור נשאר מקום ריק – שם שמים את מדבקת הספירה לאחור של אינסטגרם</p>
    <div class="grid2">${[['עם ספירה לאחור', false], ['מקום למדבקה', true]].map(([l, v], i) => html`<button key=${l} onClick=${() => done({ noCd: v })} class="glass r-md press col" style=${{ padding: 8, gap: 8, alignItems: 'center' }}>${im[i] ? html`<img src=${im[i]} alt=${l} style=${{ width: '100%', aspectRatio: '9/16', objectFit: 'cover', borderRadius: 10 }}/>` : html`<div class="sk" style=${{ width: '100%', aspectRatio: '9/16' }}></div>`}<span class="t-s b">${l}</span></button>`)}</div>
    <button onClick=${() => done(null)} class="btn btn-glass press" style=${{ width: '100%' }}>ביטול</button></div><//>`;
}

ReactDOM.createRoot(document.getElementById('root')).render(React.createElement(App));
if ('serviceWorker' in navigator && location.protocol !== 'file:') {
  const had = !!navigator.serviceWorker.controller; let done = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => { if (had && !done) { done = true; location.reload(); } });
  addEventListener('load', () => navigator.serviceWorker.register('sw.js', { updateViaCache: 'none' }).then(r => { r.update(); document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') r.update().catch(() => { }); }); }).catch(() => { }));
}
