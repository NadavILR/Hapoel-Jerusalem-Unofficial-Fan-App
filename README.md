# הפועל ירושלים – אפליקציית האוהדים

אפליקציה שנבנתה מהארטיפקט <https://claude.ai/artifact/X6srLrvwjLyFaqs282DPmN>:
משחקים ותוצאות, טבלאות, סגל, כרטיסים, חנות, מנוי והתראות.

## מבנה

| נתיב | תוכן |
|---|---|
| `www/index.html` | קוד האפליקציה (React + htm), כמו בארטיפקט |
| `www/data.json`, `crests.json`, `photos.json` | נתוני העונה, סמלי קבוצות ותמונות שחקנים |
| `www/vendor/` | React, htm ו-CSS של Tailwind – מקומיים, בלי תלות ב-CDN |
| `www/manifest.webmanifest`, `www/sw.js`, `www/icons/` | הגדרות PWA: התקנה למסך הבית ועבודה בלי אינטרנט |
| `capacitor.config.json` | אריזה כאפליקציית Android / iOS |
| `UPDATE.md` | נוהל רענון `data.json` |

## 1. אפליקציית ווב שמותקנת בטלפון (PWA)

האפליקציה מתפרסמת ב-GitHub Pages: <https://nadavilr.github.io/Hapoel-Jerusalem-Unofficial-Fan-App/>
בכל דחיפה ל-`main` שמשנה את `www/`, ה-workflow שב-`.github/workflows/pages.yml` מעדכן את ענף `gh-pages`, ו-Pages מגיש אותו
(Settings → Pages → Deploy from a branch: `gh-pages` / root).
פותחים את הכתובת בטלפון:
- **אנדרואיד (Chrome):** תפריט ⋮ ← "התקנת אפליקציה".
- **אייפון (Safari):** כפתור השיתוף ← "הוספה למסך הבית".

האפליקציה נפתחת במסך מלא עם האייקון של הקבוצה, ואחרי הפתיחה הראשונה עובדת גם בלי אינטרנט
(`data.json` נטען מהרשת כשיש חיבור, ואם אין – מהעותק האחרון).

בדיקה מקומית: `npm run serve` ואז <http://localhost:8080>.

## 2. אפליקציית אנדרואיד (APK)

בכל דחיפה ל-`main`, ה-workflow שב-`.github/workflows/android.yml` בונה את האפליקציה ומפרסם אותה בגרסה
**android-latest**: <https://github.com/NadavILR/Hapoel-Jerusalem-Unofficial-Fan-App/releases/tag/android-latest>
מורידים מהטלפון את `hapoel-jerusalem.apk`, פותחים ומאשרים התקנה.

באפליקציית האנדרואיד:
- אתר הכרטיסים נפתח בתוך האפליקציה, וסרגל הניווט התחתון של האפליקציה מוצג מעליו
  (`www/native-nav.js`, מוזרק מ-`android/.../MainActivity.java` לאתרי `hapoel.co.il`).
- ההתראות עוברות דרך מערכת ההתראות של הטלפון, ותזכורות למשחקים מגיעות גם כשהאפליקציה סגורה.

בנייה מקומית: `npm install`, `npx cap sync android`, ואז `cd android && ./gradlew assembleDebug` (צריך Android SDK ו-JDK 17).

## עדכון נתונים ושינוי עיצוב
- נתונים: מחליפים את `www/data.json` לפי `UPDATE.md`.
- אם מוסיפים ל-`index.html` מחלקות Tailwind חדשות: `npm install` ואז `npm run build:css`.
- בכל שינוי בקבצים, מעלים את `CACHE` ב-`www/sw.js` (למשל `hpj-v2`) כדי שהטלפונים יקבלו את הגרסה החדשה.
