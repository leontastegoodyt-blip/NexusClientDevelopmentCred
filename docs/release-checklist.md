# Nexus Client – release checklist (build step 18)

Legend: ✅ done · 🟡 partly done / needs a decision · ⛔ needs the owner (can't be done in code)

## From the project brief section 8

| Item | Status | Notes |
|---|---|---|
| Never redistribute Minecraft's own files | ✅ | The game, libraries, assets and Java all come from Mojang's servers at launch. Only Nexus's own mod is bundled; mods come from Modrinth; FFmpeg is downloaded from gyan.dev on first use. |
| Azure app approved for Minecraft login | ✅ | Mojang approved client ID `b4c7e154-4e31-4ea5-8d5c-2f87357081b5` for the allow list (email "AppID Review Complete", 5 Oct 2026). |
| Code-signed installers (Windows and Mac) | 🟡 | Installers build: the Nexus logo is the app icon (`launcher/src/packaging/nexus.ico`); `gradlew :launcher:jpackage` (app folder: `:launcher:jpackageImage`, tested). Windows `.exe/.msi` also need WiX (`winget install WiXToolset.WiXToolset`). ⛔ Signing needs a code-signing certificate (Windows: OV/EV cert; Mac: Apple Developer ID + notarisation). |
| Website, Discord server | ⛔ | Needs a domain (open question) and the owner's accounts. Then set `Updater.UPDATE_URL` and `DiscordPresence.CLIENT_ID`. |
| Privacy policy and terms of service | 🟡 | Drafts: `docs/privacy-policy-DRAFT.md`, `docs/terms-of-service-DRAFT.md`. ⛔ Must be reviewed by a lawyer (children's privacy laws). |
| Crash reporting (opt-in) | ✅ | Settings → General → Crash reports (off by default) + "Send to Nexus support" on a scan. Backend stores them (`/api/crash-reports`, staff view `/api/admin/crash-reports`). |
| "Not an official Minecraft product…" notice | ✅ in launcher / ⛔ website | Shown at the bottom of every launcher page and in Settings → About. Add it to the website. |

## Also needed before a public release
| Item | Status |
|---|---|
| Host the backend (PostgreSQL + the backend jar) and point the launcher at it (`-Dnexus.backend` → build setting) | 🟡 ready to deploy, paused by the owner: `render.yaml` + `deploy/render/` (Render + Neon, free) or `deploy/oracle/` (Oracle VM). Until then the launcher runs it on the owner's PC (`LocalServer`). |
| Set the owner's UUID (backend `application.yml`) | ✅ `nexus.developers` = LeonTasteyy (developer role) |
| Extend the chat filter word list (`backend/.../chat-filter.txt`) | ⛔ |
| Test Microsoft sign-in end to end | ✅ works (owner tested, 2026-10-07) |
| Translations: ✅ the whole launcher and the in-game Nexus text can be translated with one strings file per language (`lang/strings_template.json` + `mod-core/.../nexus/lang/mod_strings_template.json`). Still to do: pick languages and get them translated | 🟡 |
| Mod versions: ✅ Fabric 1.20.x, 1.21.4, 1.21.11, 26.1.2, 26.2, 26.3 and Legacy Fabric 1.8.9 (Vanilla instances secretly use them too); Forge 1.20.1, 1.20.4, 1.20.6, 1.21.4, 1.21.11, 26.1.2, 26.2 and 26.3. DFR, cosmetics and every mod on all of them. Not on Forge yet: 1.20, 1.20.2, 1.20.3 (those Forge instances launch without the Nexus mod) | 🟡 |
| Payments for Nexus Premium (price + provider) | ⛔ |

## Online features that need the hosted backend to work for everyone
These all work today against the backend on the owner's PC (`LocalServer`) and were tested end to end
against a test server; they reach other players only once the backend is hosted:
Nexus Social in-game (pop-ups, @p/@c chat, party pings, clan tags), settings sync, short share codes,
Mod Rerender, screenshot links, badges and capes.
