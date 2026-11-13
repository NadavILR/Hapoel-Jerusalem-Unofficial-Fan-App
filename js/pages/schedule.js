/* לוח המשחקים: הבאים / הקודמים, סינון לפי מפעל. אריח תאריך אדום – בית, דיו – חוץ */
import { html, useState, useEffect, Fragment, cx } from '../ui/html.js';
import { Icon } from '../ui/icons.js';
import { useApp, Crest, Seg, Chips, HeaderSlot, Empty, ExtLink } from '../ui/kit.js';
import { ME, COMPS, CN, LINKS } from '../data/constants.js';
import { DB, compLabel, won } from '../data/store.js';
import { H, fmt, SEASON } from '../core/env.js';

const MEM = { tab: 'up', fl: 'all', y: 0 };
const isPend = (f, now) => !f.r && f.t < now - 4 * H;
const monthOf = t => fmt.month(t) + ' ' + fmt.dmy(t).slice(-4);
export default function Schedule() {
  const A = useApp(), [tab, setTab0] = useState(MEM.tab), [fl, setFl0] = useState(MEM.fl);
  const setTab = k => { MEM.tab = k; setTab0(k); window.scrollTo(0, 0); }, setFl = k => { MEM.fl = k; setFl0(k); };
  useEffect(() => { if (MEM.y) { window.scrollTo(0, MEM.y); MEM.y = 0; } }, []);
  const now = Date.now(), comps = Object.keys(COMPS).filter(c => DB.F.some(f => f.c === c));
  const ls = DB.F.filter(f => (!f.r && !isPend(f, now)) === (tab === 'up') && (fl === 'all' || f.c === fl)).sort((a, b) => tab === 'up' ? a.t - b.t : b.t - a.t);
  const open = f => { MEM.y = window.scrollY; A.openGame(f); };
  return html`<div>
    <${HeaderSlot}><div style=${{ paddingBottom: 8 }}><${Seg} items=${[['up', 'המשחקים הבאים'], ['past', 'תוצאות']]} val=${tab} set=${setTab} label="תצוגה"/></div>
      <${Chips} items=${[['all', 'הכל'], ...comps.map(k => [k, CN[k] || COMPS[k]])]} val=${fl} set=${setFl} label="מפעל"/><//>
    ${!DB.F.length ? html`<${Empty} title=${'עונת ' + SEASON} sub="לוח המשחקים עוד לא נטען לאפליקציה."><${ExtLink} href=${LINKS.basket}>לוח המשחקים באתר הרשמי<//><//>` : html`
    <div class="legend glass-2 r-full" style=${{ display: 'inline-flex', padding: '4px 12px', marginBottom: 4 }}><span><i class="dt-h"></i>משחק בית</span><span><i class="dt-a"></i>משחק חוץ</span></div>
    <div key=${tab + fl} class="stack-s stg">${ls.length ? ls.map((f, i) => {
      const mo = monthOf(f.t), newMo = !i || monthOf(ls[i - 1].t) !== mo, w = won(f), pend = isPend(f, now);
      return html`<${Fragment} key=${f.id}>${newMo ? html`<div class="month">${mo}</div>` : null}
        <div role="button" tabindex="0" onClick=${() => open(f)} onKeyDown=${e => e.key === 'Enter' && open(f)} class="glass lite r-md pad-s row press cv" style=${{ gap: 12, cursor: 'pointer' }}>
          <div class=${cx('dt', f.h ? 'dt-h' : 'dt-a')}><div class="w">${fmt.wd(f.t)}</div><div class="d num">${fmt.day(f.t)}</div></div>
          <${Crest} n=${f.o} s=${34}/>
          <div class="grow"><div class="b trunc">${f.o}</div><div class="t-xs mu trunc">${f.tbd ? 'שעה טרם נקבעה' : fmt.time(f.t)} · ${compLabel(f)}</div>${f.v ? html`<div class="t-xs mu row trunc" style=${{ gap: 3 }}><${Icon} n="pin" s=${11}/><span class="trunc">${f.v}</span></div>` : null}</div>
          ${f.r ? html`<div class="col shrink0" style=${{ alignItems: 'center', gap: 2 }}><div class="row num" style=${{ gap: 4, fontSize: 17 }}><span class=${f.h ? 'xb' : 'md'}>${f.h ? f.r[0] : f.r[1]}</span><span class="mu">–</span><span class=${f.h ? 'md' : 'xb'}>${f.h ? f.r[1] : f.r[0]}</span></div><span class=${cx('t-xs b', w ? 'res-w' : 'res-l')}>${w ? 'ניצחון' : 'הפסד'}</span></div>`
          : pend ? html`<div class="t-xs mu shrink0" style=${{ textAlign: 'center', lineHeight: 1.25 }}>ממתין<br/>לתוצאה</div>`
          : html`<button onClick=${e => { e.stopPropagation(); A.togRem(f); }} aria-label=${A.rem[f.id] ? 'ביטול תזכורת' : 'תזכורת למשחק'} aria-pressed=${String(!!A.rem[f.id])} class=${cx('icon-btn sm press', A.rem[f.id] ? 'btn-red' : 'btn-ghost')}><${Icon} n=${A.rem[f.id] ? 'bellOn' : 'bell'} s=${17}/></button>`}
        </div><//>`; }) : html`<div class="mu t-s" style=${{ textAlign: 'center', padding: 40 }}>אין משחקים להצגה ${fl === 'all' ? '' : 'ב' + (CN[fl] || COMPS[fl])}</div>`}</div>`}
  </div>`;
}
