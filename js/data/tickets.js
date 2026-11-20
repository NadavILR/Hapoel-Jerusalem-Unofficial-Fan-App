/* הכרטיסים שלי: נשמרים רק במכשיר (IndexedDB). כרטיס למשחק נמחק אחרי שבוע, מנוי – עד שמוחקים */
import { D, jsLoad, idle } from '../core/env.js';
export const TIX_TTL = 7 * D;
let p = null;
const db = () => p || (p = new Promise((ok, no) => { const r = indexedDB.open('hpj-tix', 1); r.onupgradeneeded = () => r.result.createObjectStore('t', { keyPath: 'id' }); r.onsuccess = () => ok(r.result); r.onerror = () => { p = null; no(r.error); }; }));
const tx = (m, f) => db().then(d => new Promise((ok, no) => { const t = d.transaction('t', m), r = f(t.objectStore('t')); t.oncomplete = () => ok(r && r.result); t.onerror = t.onabort = () => no(t.error); }));
export const TIX = { all: () => tx('readonly', s => s.getAll()), put: v => tx('readwrite', s => s.put(v)), del: id => tx('readwrite', s => s.delete(id)) };
export const expired = x => x.kind !== 'season' && x.exp && x.exp <= Date.now();
export const purge = () => window.indexedDB ? TIX.all().then(a => Promise.all((a || []).filter(expired).map(x => TIX.del(x.id)))).catch(() => { }) : Promise.resolve();
idle(() => purge(), 4000);
/* PDF → תמונות (עד 4 עמודים) */
export async function pdfPages(file) {
  await jsLoad('vendor/pdfjs/pdf.min.js'); const L = window.pdfjsLib; if (!L) throw new Error('pdf.js');
  L.GlobalWorkerOptions.workerSrc = 'vendor/pdfjs/pdf.worker.min.js';
  const doc = await L.getDocument({ data: new Uint8Array(await file.arrayBuffer()) }).promise, out = [];
  for (let i = 1; i <= Math.min(doc.numPages, 4); i++) {
    const pg = await doc.getPage(i), v1 = pg.getViewport({ scale: 1 }), sc = Math.min(3, 1400 / v1.width), vp = pg.getViewport({ scale: sc });
    const c = document.createElement('canvas'); c.width = Math.round(vp.width); c.height = Math.round(vp.height);
    const x = c.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height);
    await pg.render({ canvasContext: x, viewport: vp }).promise; out.push(await new Promise(r => c.toBlob(r, 'image/png')));
  }
  return out;
}
const qrLibs = () => Promise.all([jsLoad('vendor/qr/jsQR.js'), jsLoad('vendor/qr/qrcode.js')]);
/* קוד ה-QR מתוך הקובץ (jsQR) */
export async function findQR(blobs) {
  await qrLibs(); const J = window.jsQR; if (!J) return '';
  for (const b of blobs) {
    let bm = null; try { bm = await createImageBitmap(b); } catch (e) { continue; }
    for (const w of [1600, 900, 600]) {
      const k = Math.min(1, w / bm.width), c = document.createElement('canvas'); c.width = Math.round(bm.width * k); c.height = Math.round(bm.height * k);
      const x = c.getContext('2d', { willReadFrequently: true }); x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height); x.drawImage(bm, 0, 0, c.width, c.height);
      const r = J(x.getImageData(0, 0, c.width, c.height).data, c.width, c.height, { inversionAttempts: 'attemptBoth' }); if (r && r.data) return r.data;
    }
  }
  return '';
}
/* ציור מחדש נקי (qrcode-generator): מספר המודולים ונתיב SVG */
export async function qrPath(text) {
  await qrLibs(); const Q = window.qrcode; if (!Q) return null;
  const q = Q(0, 'M'); if (/[^\x00-\x7f]/.test(text) && Q.stringToBytesFuncs && Q.stringToBytesFuncs['UTF-8']) Q.stringToBytes = Q.stringToBytesFuncs['UTF-8'];
  q.addData(text); q.make(); const n = q.getModuleCount(); let d = '';
  for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) if (q.isDark(r, c)) d += 'M' + c + ' ' + r + 'h1v1h-1z';
  return { n, d };
}
