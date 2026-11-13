/* רענון: קובץ הנתונים העדכני, ואחריו המקורות החיים (תוצאות, מועדים, טבלאות, בראקטים, חדשות) */
import { NATIVE } from '../core/env.js';
import { SITE } from './constants.js';
import { DB, applyData, keepResults, changed } from './store.js';
import { loadLeague, loadBrackets, loadLabels } from './s365.js';
import { loadIsrael } from './basket.js';
import { loadEuro } from './euro.js';
import { loadNews } from './media.js';

export const status = { loading: false, at: null, live: false };
let running = null;
export function refresh() {
  if (running) return running;
  running = (async () => {
    status.loading = true; changed();
    let fresh = false;
    for (const u of NATIVE ? [SITE + 'data.json', 'data.json'] : ['data.json']) {
      try { const r = await fetch(u + '?t=' + Date.now(), { cache: 'no-store' }); if (r.ok && applyData(await r.json())) { fresh = true; break; } } catch (e) { }
    }
    if (DB.ok) {
      changed();
      const ok = await Promise.all([
        loadEuro().catch(() => 0),
        (NATIVE ? loadIsrael().catch(() => 0) : Promise.resolve(0)).then(async n => n + (await loadLeague().catch(() => 0))),
        loadBrackets().catch(() => 0),
        NATIVE ? loadNews().then(() => 0).catch(() => 0) : 0,
      ]);
      if (ok.some(n => n > 0) || fresh) status.at = new Date();
      keepResults();
    }
    status.live = fresh; status.loading = false; changed();
  })().finally(() => { running = null; });
  return running;
}
/* תוויות המפעל והמחזור – פעם אחת בפתיחה */
export const loadOnce = () => loadLabels().catch(() => 0);
