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

בכל דחיפה ל-`main`, ה-workflow שב-`.github/workflows/android.yml` בונה את האפליקציה ומפרסם אותה כ-release לפי מספר הגרסה.
הגרסה האחרונה תמיד כאן: <https://github.com/NadavILR/Hapoel-Jerusalem-Unofficial-Fan-App/releases/latest>
מורידים מהטלפון את `hapoel-jerusalem-vX.X.X.apk`, פותחים ומתקינים מעל הגרסה הקיימת.

באפליקציית האנדרואיד:
- אתר הכרטיסים נפתח כמסך של האפליקציה (`TicketsPlugin.java`), וסרגל הניווט התחתון נשאר גלוי מתחתיו.
- קישורים לאתרים חיצוניים נפתחים בחלון מעל האפליקציה (`MainActivity.java`) עם כפתור "חזור" בצד ימין למעלה,
  שמחזיר למסך שממנו יצאו. כפתור "חזור" של המכשיר חוזר קודם אחורה בתוך האתר, ובדף הראשון שלו סוגר אותו.
- ההתראות עוברות דרך מערכת ההתראות של הטלפון, ותזכורות למשחקים מגיעות גם כשהאפליקציה סגורה.

בנייה מקומית: `npm install`, `npx cap sync android`, ואז `cd android && ./gradlew assembleRelease` (צריך Android SDK ו-JDK 17).

### חתימה
כל ה-APK-ים נחתמים באותו מפתח (`android/keystore/hpj-release.jks`), כדי שכל גרסה תותקן כעדכון מעל הקודמת.
המפתח נמצא בריפו הציבורי לבקשת בעל הריפו, ולכן כל אחד יכול לחתום בו: מתקינים רק APK שהורדו מעמוד ה-Releases של הריפו הזה.

## מספר גרסה

מספר הגרסה נמצא בקובץ `VERSION` ומוצג בתחתית האפליקציה, בשם קובץ ה-APK ובמידע על האפליקציה באנדרואיד.
בכל תיקון מעלים גרסה: `node scripts/bump-version.js` (1.0.0 ← 1.0.1 … 1.0.9 ← 1.1.0 … 1.9.9 ← 2.0.0).
הסקריפט מעדכן גם את `www/version.js` ואת שם המטמון ב-`www/sw.js`, כך שהטלפונים מקבלים את הגרסה החדשה.

## עדכון נתונים ושינוי עיצוב
- נתונים: מחליפים את `www/data.json` לפי `UPDATE.md`.
- אם מוסיפים ל-`index.html` מחלקות Tailwind חדשות: `npm install` ואז `npm run build:css`.
