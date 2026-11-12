---
name: הפועל ירושלים – אפליקציית האוהדים
description: Frosted glass over a moving red arena – the unofficial Hapoel Jerusalem basketball fan app.
colors:
  jerusalem-red: "#D21919"
  jerusalem-red-deep: "#b31515"
  jerusalem-red-bright: "#ff5c5c"
  ink: "#282828"
  night: "#000000"
  mist: "#f4f4f5"
  paper: "#ffffff"
  graphite: "#333333"
  muted-light: "#71717a"
  muted-dark: "#a1a1aa"
  hairline-light: "#e4e4e7"
  hairline-dark: "#444444"
  win-green: "#16a34a"
  win-green-bright: "#4ade80"
typography:
  display:
    fontFamily: "Noto Sans Hebrew, system-ui, sans-serif"
    fontSize: "3.75rem"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "-0.01em"
    fontFeature: "tnum"
  headline:
    fontFamily: "Noto Sans Hebrew, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.25
  title:
    fontFamily: "Noto Sans Hebrew, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 700
    lineHeight: 1.4
  body:
    fontFamily: "Noto Sans Hebrew, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: "Noto Sans Hebrew, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.35
rounded:
  sm: "8px"
  md: "12px"
  card: "16px"
  card-lg: "20px"
  pill: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
components:
  button-primary:
    backgroundColor: "{colors.jerusalem-red}"
    textColor: "{colors.paper}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
  card-glass:
    backgroundColor: "rgb(255 255 255 / 0.62)"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "12px"
  card-glass-dark:
    backgroundColor: "rgb(0 0 0 / 0.62)"
    textColor: "{colors.mist}"
    rounded: "{rounded.card}"
    padding: "12px"
  red-card:
    backgroundColor: "{colors.jerusalem-red}"
    textColor: "{colors.paper}"
    rounded: "{rounded.card-lg}"
    padding: "16px"
  segmented-active:
    backgroundColor: "{colors.jerusalem-red}"
    textColor: "{colors.paper}"
    rounded: "{rounded.md}"
  nav-item-active:
    backgroundColor: "rgb(210 25 25 / 0.12)"
    textColor: "{colors.jerusalem-red}"
    rounded: "{rounded.pill}"
  my-team-row:
    backgroundColor: "rgb(210 25 25 / 0.08)"
    textColor: "{colors.jerusalem-red-deep}"
---

# Design System: הפועל ירושלים – אפליקציית האוהדים

## Overview

**Creative North Star: "Glass Over the Arena"**

The app is a stack of frosted glass panes floating over a living, blurred red arena. The background is never still: a soft animated field of red bands (light or dark with the phone's theme) moves behind everything, and every surface above it – the header, the bottom navigation, cards, menus, sheets – is glass that lets that motion glow through. Content sits on the glass; the club's red is the one loud voice, reserved for what matters on game day: the next game, the live score, the selected tab, your own team.

It should feel energetic and alive, but read precise and clean: numbers are big, tabular and instantly readable, and the hierarchy is calm so the live score can be loud. It is warm and fan-like (the red, the crests, Hebrew throughout, home team on the right) and finished with a premium touch (glass highlights, gentle depth, springy motion), without ever pretending to be the club's official product.

**Key Characteristics:**
- Frosted glass surfaces (24px blur, 1.8 saturation) over an animated, blurred red background.
- One accent: Jerusalem Red, used for the hero card, live state, selection and "my team".
- Big tabular numbers for scores, clocks and countdowns.
- Right-to-left Hebrew layout, Noto Sans Hebrew everywhere – app, widget and live card.
- Light and dark themes with the same structure; only the tints and the background clip change.
- Quick, springy motion on one easing curve; motion is removed for reduced-motion users.

## Colors

One club red over neutral glass: the palette is neutral by design so the red and the crests carry the identity.

### Primary
- **Jerusalem Red** (#D21919): the club's red and the only brand accent. It fills the hero "next game" / live card (as a diagonal gradient into Ink or Night), the active segmented-control indicator, primary buttons, the live tag, and appears as light tints (8–25% alpha) for the active nav item, focused rows and highlights.
- **Jerusalem Red Deep** (#b31515): text of "my team" rows in tables on the light theme, where full red on a red tint would be too loud.
- **Jerusalem Red Bright** (#ff5c5c): the same "my team" text on the dark theme, lifted for contrast on black.

### Neutral
- **Ink** (#282828): primary text on light, and the dark end of the red card gradient in light theme.
- **Night** (#000000): the dark theme background, and the dark end of the red card gradient in dark theme.
- **Mist** (#f4f4f5): the light theme background, and primary text on dark.
- **Paper** (#ffffff): light surfaces (glass is Paper at 62% alpha) and text on red.
- **Graphite** (#333333): solid surfaces on dark, where glass is replaced by an opaque card.
- **Muted Light** (#71717a) / **Muted Dark** (#a1a1aa): secondary text, captions, timestamps.
- **Hairline Light** (#e4e4e7) / **Hairline Dark** (#444444): solid borders and dividers; glass edges use white or black at 30% alpha instead.

### Status
- **Win Green** (#16a34a) / **Win Green Bright** (#4ade80, dark theme): wins, and playoff positions in tables.

### Named Rules
**The One Red Rule.** Jerusalem Red is the only brand color. A screen has one red hero at most; everything else uses red only as a tint or a small indicator.

**The Crest Speaks Rule.** Team colors come from the crests themselves, never from extra accent colors in the chrome.

## Typography

**Display / Body Font:** Noto Sans Hebrew (with system-ui, -apple-system, Segoe UI, Arial, sans-serif), bundled locally for offline use, weights 400–800.

**Character:** One family carries everything; hierarchy comes from size and weight. Numbers are set tabular with slight negative tracking so scores and clocks never jitter as they tick.

### Hierarchy
- **Display** (800, 3–3.75rem, line-height 1): live and final scores, countdown numerals.
- **Headline** (700, 1.5rem): team names on the hero card, page titles.
- **Title** (700, 1.125rem): card and section titles.
- **Body** (400, 0.875rem): rows, descriptions, news, play-by-play.
- **Label** (500, 0.75rem): captions, chips, tab labels, "updated at" stamps.

### Named Rules
**The Tabular Score Rule.** Every number that changes – score, clock, countdown, stat – uses tabular figures, so updates never shift the layout.

**The Bold Is Ours Rule.** In a matchup, Hapoel Jerusalem's name is bold (800) and the opponent's is regular (400).

## Layout

A single phone-width column (about 12–16px side gutters) under a sticky glass header that extends under the status bar, with a fixed glass bottom navigation. Pages stack cards with 8–12px gaps; within cards, content is grouped by 8px and separated by 12–16px. The home screen opens with a swipeable carousel of game cards (previous / next / following, snap-centered). Filter chips and segmented controls stick under the header while scrolling. While a game is live, a compact live score docks inside the bottom navigation on every page. Everything is right-to-left: the home team is on the right, and leading edges are on the right.

## Elevation & Depth

Depth comes from layered translucency, not shadows. The animated background is the floor; glass panes (blurred and saturated) sit above it; menus and sheets are glass one level higher with a soft drop shadow. Each glass pane has a 1px edge (white or black at 30%) and a 1px inner top highlight that catches light.

### Shadow Vocabulary
- **Glass highlight** (`box-shadow: inset 0 1px 0 rgb(255 255 255 / .85)` light, `inset 0 1px 0 rgb(255 255 255 / .07)` dark): the top edge of every glass pane.
- **Floating menu** (`box-shadow: inset highlight, 0 12px 32px rgb(0 0 0 / .18)`): menus and popovers above the page.
- **Red indicator** (`box-shadow: 0 2px 8px rgb(210 25 25 / .3)`): the sliding segmented-control indicator.
- **Red card edge** (`box-shadow: inset 0 1px 0 rgb(255 255 255 / .18)`): the top highlight on the red card.

### Named Rules
**The Glass Needs Something Behind It Rule.** Blur only where content or the animated background shows through. Inside the opaque red card, inner boxes drop the blur and keep only the translucent fill and edge.

## Shapes

Soft, friendly rounding: cards are 16px (large cards 20px), controls and inner boxes 12px, small indicators 8px, and nav items, chips and the live tag are full pills. Crests sit in circles or are shown as-is. No sharp corners, no dotted lines.

## Components

### Buttons
- **Shape:** gently rounded (12px).
- **Primary:** Jerusalem Red fill, white text, 8×16px padding.
- **On the red card:** translucent white glass (15% white, 1px edge) with white text.
- **Press:** every button and link scales to 0.96 on press (60ms) and springs back on the shared easing curve.

### Chips
- **Style:** glass pills (white or black glass, 1px edge) above content; inside chip rows the blur is dropped for performance and the fill is more opaque (78%).
- **Selected:** Jerusalem Red fill with white text.

### Cards / Containers
- **Corner Style:** 16px (20px for large).
- **Background:** glass (Paper or black at 62% with blur 24px and saturation 1.8); an opaque Paper / Graphite variant where blur would not help.
- **Shadow Strategy:** glass highlight only (see Elevation).
- **Border:** 1px glass edge.
- **Internal Padding:** 12px (16px on hero cards).

### Navigation
- **Header:** sticky glass bar under the status bar, club crest and "הפועל ירושלים", notification and settings icons.
- **Bottom navigation:** fixed glass bar with five icon + label items (בית, משחקים, טבלאות, סגל, עוד); the active item gets a red-tinted pill that slides between items, red icon and bold label.
- **Segmented controls:** a red pill indicator slides to the selected tab (12px radius, 8px when nested).

### Red Card (signature)
The hero "next game" card: a 135° gradient from Jerusalem Red into Ink (light) or Night (dark), white text, crests at the ends (home on the right), countdown in four translucent boxes (days, hours, minutes, seconds), and glass buttons. When the game starts it turns into the live card with the big score, quarter and clock.

### Live Tag (signature)
A red "לייב" pill that beats softly (1.8s), with a ring pulse and a light sweep across it, used only while a game is actually live.

### My-Team Row
In tables and stats, Hapoel Jerusalem's row is tinted with red (8% light, 18% dark) and its text is Jerusalem Red Deep / Bright.

## Do's and Don'ts

### Do:
- **Do** keep Jerusalem Red (#D21919) as the single brand accent, and keep the club crest as the identity.
- **Do** put content on glass (blur 24px, saturation 1.8, 1px edge, inner top highlight) over the animated background.
- **Do** use tabular numbers for scores, clocks, countdowns and stats.
- **Do** place the home team on the right and lay everything out right-to-left.
- **Do** design every screen for both light and dark themes.
- **Do** use the shared easing curve (cubic-bezier(.22,1,.36,1)) with short durations (120–280ms), and disable motion for reduced-motion users.

### Don't:
- **Don't** add a second brand color to the chrome; team colors come only from crests.
- **Don't** present anything as official club material; the app is a fan app and says so.
- **Don't** blur inside opaque surfaces (like the red card) where nothing shows through.
- **Don't** animate the live tag or pulse anything when a game isn't live.
- **Don't** use fonts other than Noto Sans Hebrew, or letter-spaced uppercase labels.
