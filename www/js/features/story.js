/* שיתוף לסטורי: כרטיס זכוכית מעל הרקע המונפש של האפליקציה. סרטון של 20 שניות (H.264 במכשיר, WebCodecs + mp4-muxer),
   ואם המכשיר לא תומך – תמונה. במשחק הבא: בחירה בין גרסה עם ספירה לאחור לגרסה עם מקום למדבקת הספירה של אינסטגרם */
import { plug, D, H, jsLoad, fdLong } from '../core/env.js';
import { ME } from '../data/constants.js';
import { compLabel, crestSrcs, crestBackup, crestDataOk } from '../data/store.js';
import { NT } from './notify.js';

const W = 1080, HH = 1920, RED = '#D21919', INK = '#282828';
export const isDark = () => document.documentElement.classList.contains('dark');
const imgLoad = src => new Promise(res => { if (!src) return res(null); const i = new Image(); i.crossOrigin = 'anonymous'; i.referrerPolicy = 'no-referrer'; i.onload = () => res(i); i.onerror = () => res(null); i.src = src; });
async function crestImg(n) {
  if (n === ME) return imgLoad('icons/club-logo.png');
  for (const s of crestSrcs(n)) { const i = await imgLoad(s); if (i) return i; }
  const j = await crestBackup().catch(() => null), v = j && j[n]; return crestDataOk(v) ? imgLoad(v) : null;
}
/* טשטוש זול לרקע שמאחורי הזכוכית: הקטנה והגדלה חזרה */
const small = document.createElement('canvas'); small.width = W / 12; small.height = HH / 12;
function blurInto(x, src, rect) {
  const s = small.getContext('2d'); s.drawImage(src, 0, 0, small.width, small.height);
  x.save(); x.beginPath(); rect(); x.clip(); x.imageSmoothingQuality = 'high'; x.drawImage(small, 0, 0, W, HH); x.restore();
}
/* הכרטיס עצמו (על שקוף). bgSrc – הרקע שמתחתיו, לטשטוש */
export async function storyCard(g, L, o = {}) {
  const c = document.createElement('canvas'); c.width = W; c.height = HH; c.setAttribute('dir', 'rtl');
  c.style.cssText = 'position:fixed;left:-99999px;top:0;width:1px;height:1px;opacity:0;pointer-events:none'; document.body.appendChild(c);
  const x = c.getContext('2d'), dark = o.dark ?? isDark(), fg = dark ? '#fff' : INK, mu = dark ? 'rgba(255,255,255,.72)' : 'rgba(40,40,40,.68)';
  try { await document.fonts.load('800 80px "Noto Sans Hebrew"'); await document.fonts.load('400 40px "Noto Sans Hebrew"'); } catch (e) { }
  const F = (w, s) => w + ' ' + s + 'px "Noto Sans Hebrew", sans-serif';
  const T = (t, y, w, s, col = fg, al = 'center', xx = W / 2) => { x.font = F(w, s); x.fillStyle = col; x.textAlign = al; x.direction = 'rtl'; x.fillText(t, xx, y); };
  const rr = (X, Y, w, h, r) => { x.beginPath(); x.moveTo(X + r, Y); x.arcTo(X + w, Y, X + w, Y + h, r); x.arcTo(X + w, Y + h, X, Y + h, r); x.arcTo(X, Y + h, X, Y, r); x.arcTo(X, Y, X + w, Y, r); x.closePath(); };
  const hm = g.h ? ME : g.o, aw = g.h ? g.o : ME, [ih, ia] = await Promise.all([crestImg(hm), crestImg(aw)]);
  const live = L && L.status === 'live', ch = live ? 1240 : 1180, cy = (HH - ch) / 2, cx0 = 70, cw = W - 140;
  /* זכוכית: הרקע מטושטש, שכבה שקופה, קו מתאר וברק עליון */
  if (o.bgSrc) blurInto(x, o.bgSrc, () => rr(cx0, cy, cw, ch, 72));
  x.save(); x.shadowColor = 'rgba(0,0,0,.28)'; x.shadowBlur = 80; x.shadowOffsetY = 24; rr(cx0, cy, cw, ch, 72); x.fillStyle = dark ? 'rgba(40,40,40,.70)' : 'rgba(255,255,255,.72)'; x.fill(); x.restore();
  rr(cx0 + 1, cy + 1, cw - 2, ch - 2, 71); x.strokeStyle = dark ? 'rgba(255,255,255,.16)' : 'rgba(255,255,255,.9)'; x.lineWidth = 3; x.stroke();
  /* הפס האדום העליון */
  x.save(); rr(cx0, cy, cw, ch, 72); x.clip(); x.fillStyle = RED; x.fillRect(cx0, cy, cw, 210); x.restore();
  if (live) {
    T(ME, cy + 128, '800', 64, '#fff', 'right', W - 140);
    x.font = F('800', 52); const lw = x.measureText('לייב').width + 64; rr(140, cy + 64, lw, 86, 43); x.fillStyle = '#fff'; x.fill(); T('לייב', cy + 125, '800', 52, RED, 'center', 140 + lw / 2);
  } else { T('המשחק הבא', cy + 100, '800', 60, '#fff'); T(compLabel(g), cy + 160, '400', 38, 'rgba(255,255,255,.9)'); }
  const top = cy + 270, cs = live ? 190 : 220, xr = W - 130 - cs, xl = 130;
  const crest = (im, X, n) => { if (im) { const k = Math.min(cs / im.width, cs / im.height), w = im.width * k, h = im.height * k; x.drawImage(im, X + (cs - w) / 2, top + (cs - h) / 2, w, h); } else { x.beginPath(); x.arc(X + cs / 2, top + cs / 2, cs / 2, 0, 7); x.fillStyle = 'rgba(128,128,128,.2)'; x.fill(); T(n[0], top + cs / 2 + 40, '800', 110, fg, 'center', X + cs / 2); } };
  crest(ih, xr, hm); crest(ia, xl, aw);
  const nm = (t, X, me) => { let fs = 46, s = t; x.font = F(me ? '800' : '500', fs); while (x.measureText(s).width > 380 && fs > 34) { fs -= 2; x.font = F(me ? '800' : '500', fs); } while (x.measureText(s).width > 380 && s.length > 3) s = s.slice(0, -2) + '…'; T(s, top + cs + 76, me ? '800' : '500', fs, fg, 'center', X); };
  nm(hm, xr + cs / 2, hm === ME); nm(aw, xl + cs / 2, aw === ME);
  if (live) {
    T(String(L.hs), top + cs / 2 + 45, '800', 130, fg, 'center', W / 2 + 150); T(String(L.as), top + cs / 2 + 45, '800', 130, fg, 'center', W / 2 - 150); T('–', top + cs / 2 + 35, '400', 90, mu);
    const st = (L.st || 'LIVE') + (L.clock ? ' · ' + L.clock : ''); x.font = F('800', 44); const sw = x.measureText(st).width + 80; rr(W / 2 - sw / 2, top + cs + 120, sw, 84, 42); x.fillStyle = RED; x.fill(); T(st, top + cs + 177, '800', 44, '#fff');
    T(compLabel(g) + ' · ' + fdLong(g).split(' · ').slice(0, 2).join(' · '), top + cs + 285, '400', 34, mu);
    const qs = (L.q || []).filter(q => q && q.n).slice(0, 8), ty = top + cs + 340, row = 80;
    if (qs.length) {
      const bx = 130, bw = W - 260, pad = 40, n = qs.length + 2, cwd = (bw - 2 * pad) / n, ccx = k => bx + bw - pad - cwd * (k + .5);
      rr(bx, ty, bw, row * 3 + 20, 36); x.fillStyle = dark ? 'rgba(255,255,255,.08)' : 'rgba(40,40,40,.06)'; x.fill();
      [...qs.map(q => q.n), 'סה״כ'].forEach((q, i) => T(q, ty + 58, '800', 30, mu, 'center', ccx(i + 1)));
      [[ih, 'h', L.hs], [ia, 'a', L.as]].forEach(([im, k, tot], r) => {
        const y = ty + row * (r + 1) + 10;
        if (im) { const kk = Math.min(56 / im.width, 56 / im.height), w = im.width * kk, h = im.height * kk; x.drawImage(im, ccx(0) - w / 2, y + 26 - h / 2, w, h); }
        qs.forEach((q, i) => T(String(q[k] ?? ''), y + 44, '500', 38, fg, 'center', ccx(i + 1)));
        T(String(tot), y + 44, '800', 40, fg, 'center', ccx(qs.length + 1));
      });
    }
  } else {
    T('VS', top + cs / 2 + 26, '800', 60, mu);
    T(fdLong(g), top + cs + 180, '500', 42); T(g.v || (g.h ? 'משחק בית' : 'משחק חוץ'), top + cs + 242, '400', 38, mu);
    if (!o.noCd) {
      const df = Math.max(0, g.t - (o.now || Date.now())), u = [['ימים', df / D | 0], ['שעות', (df % D) / H | 0], ['דקות', (df % H) / 6e4 | 0], ['שניות', (df % 6e4) / 1e3 | 0]], bw = 180, gap = 26, y0 = top + cs + 320, x0 = (W - (4 * bw + 3 * gap)) / 2;
      u.forEach(([l, v], i) => { const X = x0 + (3 - i) * (bw + gap); rr(X, y0, bw, 190, 40); x.fillStyle = RED; x.fill(); T(String(v).padStart(2, '0'), y0 + 112, '800', 84, '#fff', 'center', X + bw / 2); T(l, y0 + 164, '500', 32, 'rgba(255,255,255,.85)', 'center', X + bw / 2); });
    }
  }
  c.remove(); return c;
}
/* תמונה סטטית: הרקע (פריים ראשון) + הכרטיס */
export async function storyImage(g, L, o = {}) {
  const dark = isDark(), c = document.createElement('canvas'); c.width = W; c.height = HH; const x = c.getContext('2d');
  x.fillStyle = dark ? INK : '#fff'; x.fillRect(0, 0, W, HH);
  const pat = await imgLoad(dark ? 'bg/pattern-dark.jpg' : 'bg/pattern-light.jpg');
  if (pat) { const k = Math.max(W / pat.width, HH / pat.height), w = pat.width * k, h = pat.height * k; x.drawImage(pat, (W - w) / 2, (HH - h) / 2, w, h); }
  x.drawImage(await storyCard(g, L, { ...o, bgSrc: c }), 0, 0);
  return c;
}
export async function storyVideo(g, L, prog, opt = {}) {
  if (!window.VideoEncoder || !window.VideoFrame || !window.ImageDecoder) return null;
  const fps = 30, N = fps * 20, us = 1e6 / fps, dark = isDark();
  const base = { width: W, height: HH, bitrate: 4e6, framerate: fps, avc: { format: 'avc' } }; let cfg = null;
  for (const codec of ['avc1.640028', 'avc1.4d0028', 'avc1.42e028', 'avc1.640032', 'avc1.420032']) try { const r = await VideoEncoder.isConfigSupported({ ...base, codec }); if (r && r.supported) { cfg = { ...base, codec }; break; } } catch (e) { }
  if (!cfg) return null;
  await jsLoad('vendor/mp4-muxer.js'); const M = window.Mp4Muxer; if (!M) return null;
  const dec = new ImageDecoder({ data: await (await fetch(dark ? 'bg/pattern-dark.webp' : 'bg/pattern-light.webp')).arrayBuffer(), type: 'image/webp' });
  await dec.tracks.ready; const fc = Math.max(1, dec.tracks.selectedTrack.frameCount || 1);
  /* משך כל פריים ברקע, כדי שהרקע ינוע בקצב האמיתי שלו */
  const du = []; for (let k = 0; k < fc; k++) { const { image: f } = await dec.decode({ frameIndex: k }); du.push(f.duration || 40000); f.close(); }
  const cum = []; du.reduce((t, d, k) => (cum[k] = t + d), 0); const TT = cum[fc - 1] || 1, at = t => { t %= TT; let k = 0; while (k < fc - 1 && cum[k] <= t) k++; return k; };
  const mux = new M.Muxer({ target: new M.ArrayBufferTarget(), video: { codec: 'avc', width: W, height: HH, frameRate: fps }, fastStart: 'in-memory' });
  let err = null; const enc = new VideoEncoder({ output: (c, m) => mux.addVideoChunk(c, m), error: e => { err = e; } }); enc.configure(cfg);
  const cv = document.createElement('canvas'); cv.width = W; cv.height = HH; const x = cv.getContext('2d');
  const bgc = document.createElement('canvas'); bgc.width = W; bgc.height = HH; const bx = bgc.getContext('2d');
  const t0 = Date.now(), live = L && L.status === 'live'; let card = null, sec = -1;
  try {
    for (let i = 0; i < N; i++) {
      if (err) throw err;
      bx.fillStyle = dark ? INK : '#fff'; bx.fillRect(0, 0, W, HH);
      const { image: fr } = await dec.decode({ frameIndex: at(i * us) }), iw = fr.displayWidth, ih = fr.displayHeight, k = Math.max(W / iw, HH / ih);
      bx.drawImage(fr, (W - iw * k) / 2, (HH - ih * k) / 2, iw * k, ih * k); fr.close();
      const s = Math.floor(i / fps);
      if (!card || (!live && !opt.noCd && s !== sec)) { sec = s; card = await storyCard(g, L, { now: t0 + s * 1000, noCd: opt.noCd }); }
      x.drawImage(bgc, 0, 0);
      /* הזכוכית: רק אזור הכרטיס מטושטש */
      const ch = live ? 1240 : 1180, cy = (HH - ch) / 2;
      blurInto(x, bgc, () => { const X = 70, Y = cy, w = W - 140, h = ch, r = 72; x.moveTo(X + r, Y); x.arcTo(X + w, Y, X + w, Y + h, r); x.arcTo(X + w, Y + h, X, Y + h, r); x.arcTo(X, Y + h, X, Y, r); x.arcTo(X, Y, X + w, Y, r); x.closePath(); });
      x.drawImage(card, 0, 0);
      const vf = new VideoFrame(cv, { timestamp: Math.round(i * us), duration: Math.round(us) }); enc.encode(vf, { keyFrame: i % (fps * 2) === 0 }); vf.close();
      while (enc.encodeQueueSize > 6) await new Promise(r => setTimeout(r, 4));
      if (prog && i % 10 === 0) prog(i / N);
    }
    await enc.flush(); if (err) throw err; mux.finalize(); prog && prog(1);
    return new Blob([mux.target.buffer], { type: 'video/mp4' });
  } finally { try { enc.state !== 'closed' && enc.close(); } catch (e) { } try { dec.close(); } catch (e) { } }
}
const b64 = blob => new Promise((ok, no) => { const r = new FileReader(); r.onload = () => ok(String(r.result).split(',')[1]); r.onerror = no; r.readAsDataURL(blob); });
/* ui: { pick(g) → {noCd}|null, busy() → {set,done}, toast } */
export async function shareStory(ui, g, L) {
  let bz = null;
  try {
    const live = L && L.status === 'live'; let opt = {};
    if (!live) { opt = await ui.pick(g); if (!opt) return; }
    const txt = (live ? 'הפועל לייב! ' : 'המשחק הבא של הפועל ירושלים: ') + NT.match(g);
    let blob = null, mime = 'image/png', name = 'hapoel-story.png';
    bz = ui.busy(); try { blob = await storyVideo(g, L, bz.set, opt); } catch (e) { console.warn('story video', e); blob = null; }
    if (blob) { mime = 'video/mp4'; name = 'hapoel-story.mp4'; }
    else { bz.done(); bz = null; ui.toast('מכין תמונה לשיתוף…'); const c = await storyImage(g, L, { noCd: opt.noCd }); blob = await new Promise(r => c.toBlob(r, 'image/png')); }
    if (bz) { bz.done(); bz = null; }
    const P = plug('LiveActivity'); if (P && P.shareImage) { await P.shareImage({ data: await b64(blob), text: txt, mime, name }); return; }
    const file = new File([blob], name, { type: mime });
    if (navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file], text: txt }); return; }
    const a = document.createElement('a'), u = URL.createObjectURL(blob); a.href = u; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(u), 6e4);
    ui.toast(mime === 'video/mp4' ? 'הסרטון נשמר – אפשר להעלות אותו לסטורי' : 'התמונה נשמרה – אפשר להעלות אותה לסטורי');
  } catch (e) { if (!(e && e.name === 'AbortError')) ui.toast('לא ניתן לשתף כרגע'); } finally { bz && bz.done(); }
}
