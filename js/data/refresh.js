/* רענון: קובץ הנתונים העדכני, ואחריו המקורות החיים (תוצאות, מועדים, טבלאות, בראקטים, חדשות) */
import { NATIVE } from '../core/env.js';
import { SITE } from './constants.js';
import { DB, applyData, keepResults, changed } from './store.js';
import { loadLeague, loadBrackets, loadLabels } from './s365.js';
import { loadIsrael, BK } from './basket.js';
import { loadEuro } from './euro.js';
import { loadNews } from './media.js';
import { loadClub } from './club.js';

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
    /* בזמן הרענון המסכים מציגים שלד: לא מציגים את קובץ הנתונים (שעלול להיות ישן) לפני שהמקורות החיים עדכנו אותו.
       המקורות החיים מקבלים עד 12 שניות, כדי שהשלד לא יישאר אם אחד מהם לא עונה */
    if (DB.ok) {
      const ok = await Promise.race([new Promise(r => setTimeout(() => r([0]), 12000)), Promise.all([
        /* המועדים: קודם מהמפעלים (יורוקאפ, basket.co.il), ובסוף מהאתר הרשמי של הפועל – שקובע את התאריך, השעה והמיקום.
           טבלת ליגת ווינר: מהאתר הרשמי (basket.co.il); 365scores רק כשהאתר לא זמין (בדפדפן) */
        Promise.all([loadEuro().catch(() => 0), NATIVE ? loadIsrael().catch(() => 0) : 0])
          .then(async ([a, b]) => a + b + (NATIVE ? await loadClub().catch(() => 0) : 0) + (BK.tbl ? 0 : await loadLeague().catch(() => 0))),
        loadBrackets().catch(() => 0),
        NATIVE ? loadNews().then(() => 0).catch(() => 0) : 0,
      ])]);
      if (ok.some(n => n > 0) || fresh) status.at = new Date();
      keepResults();
    }
    status.live = fresh; status.loading = false; changed();
  })().finally(() => { running = null; });
  return running;
}
/* תוויות המפעל והמחזור – פעם אחת בפתיחה */
export const loadOnce = () => loadLabels().catch(() => 0);
