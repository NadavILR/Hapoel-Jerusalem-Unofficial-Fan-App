import { NATIVE, CAP } from './env.js';
/* בקשות רשת: באנדרואיד דרך הרכיב הנייטיב (לא חסום ע"י CORS), בדפדפן fetch רגיל */
export async function getText(url) {
  const http = NATIVE && CAP.Plugins && CAP.Plugins.CapacitorHttp;
  if (http) {
    const r = await http.get({ url, headers: { 'User-Agent': 'Mozilla/5.0' }, responseType: 'text' });
    if (r.status !== 200) throw new Error('http ' + r.status);
    return typeof r.data === 'string' ? r.data : JSON.stringify(r.data);
  }
  const r = await fetch(url, { cache: 'no-store' });
  if (!r.ok) throw new Error('http ' + r.status);
  return r.text();
}
export const getJSON = async u => JSON.parse((await getText(u)).replace(/^﻿/, ''));
/* HTML → טקסט נקי */
export const strip = h => String(h).replace(/<[^>]+>/g, ' ').replace(/&quot;/g, '"').replace(/&amp;/g, '&').replace(/&#39;/g, "'").replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim();
export const cellsOf = r => [...r.matchAll(/<t[dh][^>]*>([\s\S]*?)<\/t[dh]>/g)].map(m => strip(m[1]));
export const rowsOf = t => t.match(/<tr[^>]*>[\s\S]*?<\/tr>/g) || [];
export const decode = x => String(x).replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
