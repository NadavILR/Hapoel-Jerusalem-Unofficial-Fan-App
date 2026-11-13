/* קבועים: שמות, מפעלים, קישורים */
export const ME = 'הפועל ירושלים';
export const TEAM365 = 634;
export const COMPS = { league: 'ליגת ווינר סל', euro: 'יורוקאפ', wcup: 'גביע ווינר סל', cup: 'גביע המדינה', super: 'הסופרקאפ הישראלי', prep: 'משחקי הכנה' };
export const CN = { league: 'ליגת ווינר', euro: 'יורוקאפ', wcup: 'גביע ווינר' };
export const compShort = c => CN[c] || COMPS[c] || '';
export const SITE = 'https://nadavilr.github.io/Hapoel-Jerusalem-Unofficial-Fan-App/';
export const REPO = 'NadavILR/Hapoel-Jerusalem-Unofficial-Fan-App';
export const LINKS = {
  tickets: 'https://tickets.hapoel.co.il/', shop: 'https://shop.hapoel.co.il', club: 'https://hapoel.co.il',
  memberAuth: 'https://auth.hapoel.co.il/fanza?external_url=true', memberPortal: 'https://fortress.arena-jer.co.il/HPJRMBB/NfcMemberPortal/',
  e365: 'https://www.365scores.com/en-us/basketball/league/eurocup-329/standings', basket: 'https://basket.co.il',
  eurocup: 'https://www.euroleaguebasketball.net/en/eurocup/', team365: 'https://www.365scores.com/basketball/team/hapoel-jerusalem-634',
  wcup365: 'https://www.365scores.com/he/basketball/league/winner-cup-609/standings',
  league365: 'https://www.365scores.com/he/basketball/league/winner-league-47/standings',
  cup365: 'https://www.365scores.com/he/basketball/league/state-cup-51/standings',
};
/* אתר המפעל של כל משחק */
export const OFFICIAL = { league: LINKS.basket, wcup: LINKS.basket, cup: LINKS.basket, super: LINKS.basket, euro: LINKS.eurocup, prep: LINKS.club };
/* שמות הקבוצות ב-basket.co.il → השמות באפליקציה */
export const BNAME = { 'הפועל י-ם': ME, 'הפועל ת"א': 'הפועל תל אביב', 'מכבי ת"א': 'מכבי תל אביב', 'מכבי ר"ג': 'מכבי רמת גן', 'הפועל ב"ש': 'הפועל ב״ש/דימונה', 'נס ציונה': 'עירוני נס ציונה', 'קריית אתא': 'עירוני קריית אתא', 'ראשון לציון': 'מכבי ראשון לציון' };
export const bName = n => { n = String(n || '').replace(/[״”]/g, '"').trim(); return BNAME[n] || n; };
export const ECG = { A: 'בית א׳', B: 'בית ב׳', C: 'בית ג׳', D: 'בית ד׳' };
/* שלבי הבראקטים ב-365scores: [שם השלב, הטוב מ-, מספר צמדים] */
const B365 = 'https://www.365scores.com/he/basketball/league/';
export const BRT = {
  euro: { t: 'פלייאוף היורוקאפ', f: 'שמינית גמר, רבע גמר, חצי גמר וגמר · כל השלבים בסדרות הטוב מ-3', u: B365 + 'eurocup-329/brackets', r: [['שמינית גמר', 3, 8], ['רבע גמר', 3, 4], ['חצי גמר', 3, 2], ['גמר', 3, 1]] },
  league: { t: 'פלייאוף ליגת ווינר סל', f: 'רבע גמר בסדרות הטוב מ-3 · חצי גמר וגמר בסדרות הטוב מ-5', u: B365 + 'winner-league-47/brackets', r: [['רבע גמר', 3, 4], ['חצי גמר', 5, 2], ['גמר', 5, 1]] },
  cup: { t: 'גביע המדינה', f: 'נוקאאוט במשחק אחד: רבע גמר, חצי גמר וגמר', u: B365 + 'state-cup-51/brackets', r: [['רבע גמר', 1, 4], ['חצי גמר', 1, 2], ['גמר', 1, 1]] },
  wcup: { t: 'שלב הנוקאאוט', f: 'נוקאאוט במשחק אחד: רבע גמר, חצי גמר וגמר', u: B365 + 'winner-cup-609/brackets', r: [['רבע גמר', 1, 4], ['חצי גמר', 1, 2], ['גמר', 1, 1]] },
};
export const NGROUP = 'hpj-all';
/* העדפות ברירת מחדל של ההתראות */
export const PF0 = { hour: 1, quarter: 1, sales: 1, comps: { league: 1, euro: 1, wcup: 1, cup: 1, super: 1, prep: 1 } };
