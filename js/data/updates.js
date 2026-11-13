/* עדכוני האפליקציה (אנדרואיד): הגרסה האחרונה ב-GitHub Releases לפי ערוץ העדכונים */
import { getText } from '../core/net.js';
import { NATIVE, LS, VERSION } from '../core/env.js';
import { REPO } from './constants.js';
const DL = 'https://github.com/' + REPO + '/releases/download/';
export const vcmp = (a, b) => { const x = String(a).split('.').map(Number), y = String(b).split('.').map(Number); for (let i = 0; i < 3; i++) if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) - (y[i] || 0); return 0; };
const relVer = tag => String(tag || '').replace(/^v/, '').replace(/-(beta|alpha)$/, '').replace(/^(\d+\.\d{2,})\.0$/, '$1');
/* 0 – רשמיות בלבד, 1 – גם בטא, 2 – גם אלפא */
export const channel = () => { const v = +LS.get('hpj-beta', 0); return v >= 2 ? 2 : v >= 1 ? 1 : 0; };
export const setChannel = v => LS.set('hpj-beta', v);
const notesOf = t => String(t || '').split('\n').map(l => /^\s*-\s+(.+)/.exec(l)).filter(Boolean).map(m => m[1].replace(/`|\*\*/g, '').trim()).filter(x => !/^להתקנה/.test(x)).slice(0, 60);
const unHtml = t => t.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, '&');
async function fromApi() {
  const ch = channel(), u = 'https://api.github.com/repos/' + REPO + '/releases' + (ch ? '?per_page=15' : '/latest');
  const raw = NATIVE ? await getText(u) : await (async () => { const r = await fetch(u, { cache: 'no-store', headers: { Accept: 'application/vnd.github+json' } }); if (!r.ok) throw new Error('http ' + r.status); return r.text(); })();
  const one = j => {
    const alpha = /-alpha$/.test(j.tag_name || ''); if (alpha && ch < 2) return null;
    const v = relVer(j.tag_name), a = (j.assets || []).find(x => /\.apk$/i.test(x.name || ''));
    if (j.draft || !/^\d+\.\d+(\.\d+)?$/.test(v) || !a || typeof a.browser_download_url !== 'string' || !a.browser_download_url.startsWith(DL)) return null;
    return { v, url: a.browser_download_url, notes: notesOf(j.body), pre: !!j.prerelease, alpha };
  };
  const d = JSON.parse(raw); if (!ch) return one(d);
  let best = null; for (const j of Array.isArray(d) ? d : []) { const r = one(j); if (r && (!best || vcmp(r.v, best.v) > 0)) best = r; }
  return best;
}
/* גיבוי: פיד ה-releases הציבורי (בלי מגבלת הבקשות של ה-API) */
async function fromAtom() {
  const ch = channel(), t = await getText('https://github.com/' + REPO + '/releases.atom?t=' + Date.now()); let best = null;
  const re = ch >= 2 ? /\/releases\/tag\/(v\d+\.\d+(?:\.\d+)?(?:-beta|-alpha)?)["<]/g : ch ? /\/releases\/tag\/(v\d+\.\d+(?:\.\d+)?(?:-beta)?)["<]/g : /\/releases\/tag\/(v\d+\.\d+(?:\.\d+)?)["<]/g;
  for (const m of t.matchAll(re)) { const v = relVer(m[1]); if (!best || vcmp(v, best.v) > 0) best = { v, tag: m[1] }; }
  if (!best) return null; let notes = [];
  try { const e = t.split('<entry>').find(x => x.includes('/releases/tag/' + best.tag + '"')), c = e && /<content[^>]*>([\s\S]*?)<\/content>/.exec(e); if (c) notes = [...unHtml(c[1]).matchAll(/<li>([\s\S]*?)<\/li>/g)].map(m => unHtml(m[1].replace(/<[^>]+>/g, '')).trim()).filter(x => x && !/^להתקנה/.test(x)).slice(0, 60); } catch (_) { }
  return { v: best.v, url: DL + best.tag + '/hapoel-jerusalem-v' + best.v + '.apk', notes, pre: /-(beta|alpha)$/.test(best.tag), alpha: /-alpha$/.test(best.tag) };
}
export async function latestRelease() { try { const r = await fromApi(); if (r) return r; } catch (e) { } return fromAtom(); }
/* 'new' + הגרסה, 'none', או 'fail' */
export async function checkUpdate() {
  if (!NATIVE) return { s: 'none' };
  try { const r = await latestRelease(); return r && vcmp(r.v, VERSION() || '0.0.0') > 0 ? { s: 'new', r } : { s: 'none' }; } catch (e) { return { s: 'fail' }; }
}
