/* סרגל הניווט התחתון של האפליקציה, מוצג מעל אתרי המועדון (כמו אתר הכרטיסים) באפליקציית האנדרואיד.
   האפליקציה מזריקה את הקובץ הזה לדף אחרי שהוא נטען (MainActivity.java). */
(function(){
/* רק בדף הראשי של אתר חיצוני – לא באפליקציה עצמה ולא בתוך מסגרות (למשל טופס התשלום) */
if(window.top!==window||location.hostname==='localhost'||!/^https?:$/.test(location.protocol))return;
if(window.__hpjNav)return;window.__hpjNav=1;
var APP='https://localhost/';
const IC={home:'M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM9 22V12h6v10',cal:'M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',list:'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',users:'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',tix:'M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2zM13 5v2M13 17v2M13 11v2',bag:'M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4zM3 6h18M16 10a4 4 0 0 1-8 0',phone:'M5 2h14a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM12 18h.01',bell:'M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0',back:'M5 12h14M12 5l7 7-7 7',x:'M18 6L6 18M6 6l12 12',pin:'M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0 1 18 0zM12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',sync:'M23 4v6h-6M1 20v-6h6M3.5 9a9 9 0 0 1 14.9-3.4L23 10M1 14l4.6 4.4A9 9 0 0 0 20.5 15',ext:'M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6M15 3h6v6M10 14L21 3'};
const NAV=[['home','בית','home'],['sched','משחקים','cal'],['stand','טבלאות','list'],['roster','סגל','users'],['tix','כרטיסים','tix'],['shop','חנות','bag'],['mem','מנוי','phone']];
var host=document.createElement('div');host.id='hpj-app-nav';
host.style.cssText='position:fixed;left:0;right:0;bottom:0;z-index:2147483647;';
var sh=host.attachShadow?host.attachShadow({mode:'closed'}):host;
var items=NAV.map(function(n){var on=n[0]==='tix';
 return '<a data-k="'+n[0]+'" class="'+(on?'on':'')+'"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="'+IC[n[2]]+'"/></svg><span>'+n[1]+'</span></a>'}).join('');
sh.innerHTML='<style>:host{all:initial}nav{display:flex;direction:rtl;background:#282828;border-top:1px solid rgba(255,255,255,.1);padding-bottom:env(safe-area-inset-bottom,0px);font-family:system-ui,-apple-system,"Segoe UI",Arial,sans-serif}a{flex:1;display:flex;flex-direction:column;align-items:center;gap:2px;padding:8px 0;font-size:10px;color:#fff;opacity:.7;border-top:2px solid transparent;text-decoration:none;cursor:pointer;-webkit-tap-highlight-color:transparent}a.on{opacity:1;color:#D21919;font-weight:700;border-top-color:#D21919}</style><nav>'+items+'</nav>';
sh.addEventListener('click',function(e){var a=e.target.closest?e.target.closest('a'):null;if(!a)return;e.preventDefault();var k=a.getAttribute('data-k');
 if(k==='tix'){location.href='https://tickets.hapoel.co.il/';return}location.href=APP+'#'+k});
function pad(){if(!host.isConnected)return;var h=host.offsetHeight||58;var b=document.body;if(!b)return;if(b&&parseInt(b.style.paddingBottom||0)<h)b.style.setProperty('padding-bottom',h+'px','important')}
/* מוסיפים רק אחרי שגוף הדף קיים, כדי לא להפריע לדפדפן לקרוא את הגדרות הדף (למשל viewport) */
function put(){var root=document.body;if(!root)return;if(host.parentNode!==root)root.appendChild(host);pad()}
put();setTimeout(pad,300);window.addEventListener('resize',pad);
document.addEventListener('DOMContentLoaded',put);window.addEventListener('load',put);
/* אם האתר מחליף את התוכן של הדף, הסרגל חוזר למקומו */
try{new MutationObserver(function(){if(document.body&&host.parentNode!==document.body)put()}).observe(document,{childList:true,subtree:true})}catch(e){}
})();
