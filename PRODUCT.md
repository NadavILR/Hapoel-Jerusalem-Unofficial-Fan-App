# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

The interface is a single-file web app (React 18 + htm, Tailwind) running inside a Capacitor 6 Android shell, and also served as a web app on GitHub Pages. Android is the primary experience. Its native surfaces are drawn by Android itself: the home-screen widget, the live score notification card, Samsung Now Bar, and notifications. Their design follows the web app's look.

## Users

The owner and a small circle of friends, all Hapoel Jerusalem basketball fans. Wide public distribution is not a goal; the app is shared through GitHub Releases (APK) and the GitHub Pages web app. Typical moments: checking the next game and countdown, following a game live (often from the lock screen or notification shade), looking up tables, brackets and player stats, and sharing a game story.

## Product Purpose

Everything a Hapoel Jerusalem fan follows, in one Hebrew app: schedule and results, live games with play-by-play and stats, tables and brackets for every competition, the roster with player stats, highlights, club news, tickets/shop links, and phone notifications. Success is that the app is the one place the owner opens on game day and during the season, and it is accurate and fast.

## Positioning

- **One place, one team.** Only Hapoel Jerusalem, in Hebrew, across all competitions (Winner League, EuroCup, State Cup, Winner Cup, preseason).
- **Live from the official sources.** Live score, quarter scores, play-by-play and box scores come directly from basket.co.il livestats (Winner League / Cup) and the EuroLeague live API (EuroCup); 365scores is used for crests, names, competition labels, standings and brackets.
- **Built into the phone.** Live notification card, Samsung Now Bar, home-screen widget, game alerts, and a 9:16 story video share — things a website or general sports app does not do.
- **Fan look and feel.** It should feel like it belongs to the fans and the club's red, not like a generic sports app.

## Operating Context

- Used mostly on an Android phone, often one-handed, and often during a live game, where speed and glanceability matter.
- Releases: pre-releases (`vX.Y-beta`) from the development branch; official releases (`vX.Y`) from `main` only when the owner asks. In the app, a setting chooses whether beta versions are offered as updates.
- Season data lives in `www/data.json` and is refreshed per the procedure in `UPDATE.md`.

## Capabilities and Constraints

- **Unofficial, and says so.** The app must state that it is a fan app and not the club's, and must never present itself as official.
- **Hebrew only, right-to-left.** No other languages. Home team on the right everywhere scores are shown.
- **Android first.** The Android app (with widget and live notification) is the main experience; the web version is secondary.
- Live data, standings and stats come from third-party sources (basket.co.il, EuroLeague, 365scores, the club site, official YouTube channels); the app must not fabricate data when a source is missing.
- Custom fonts cannot be applied where Android draws the text itself (Now Bar MetricStyle / text modes).

## Brand Commitments

- Name: "הפועל ירושלים" (Hapoel Jerusalem); the app is "אפליקציית האוהדים (לא רשמית)".
- The club's red (#D21919) and the club crest are the identity and must be kept.
- Font: Noto Sans Hebrew (bundled for offline use), in the app, the widget and the live card.
- Animated, blurred light/dark backgrounds supplied by the owner (`www/bg/`).

## Evidence on Hand

- Real season data: `www/data.json` (games, results, stats, roster, news), club crest and team crests, roster photos, the owner's background clips.
- No testimonials, user counts, press or endorsements exist; do not invent any.

## Product Principles

1. **Accurate before pretty.** Live and stats data from the official sources; show nothing rather than something wrong.
2. **Glanceable on game day.** The score, quarter and clock must read instantly, in the app and on the lock screen.
3. **Feels like the club, never claims to be the club.** Red and crest, with an unmistakable "unofficial" label.
4. **Hebrew-native.** RTL layout and Hebrew wording throughout, home team on the right.
5. **Light on the phone.** Fast to open, smooth to scroll, small assets, works offline where possible.
