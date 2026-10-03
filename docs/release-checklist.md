# Nexus Client – release checklist (build step 18)

Legend: ✅ done · 🟡 partly done / needs a decision · ⛔ needs the owner (can't be done in code)

## From the project brief section 8

| Item | Status | Notes |
|---|---|---|
| Never redistribute Minecraft's own files | ✅ | The game, libraries, assets and Java all come from Mojang's servers at launch. Only Nexus's own mod is bundled; mods come from Modrinth; FFmpeg is downloaded from gyan.dev on first use. |
| Azure app approved for Minecraft login | ⛔ | 1) Azure: add redirect `http://localhost` under "Mobile and desktop applications". 2) Apply at https://aka.ms/mce-reviewappid with client ID `b4c7e154-4e31-4ea5-8d5c-2f87357081b5`. Sign-in can't be tested until both are done. |
| Code-signed installers (Windows and Mac) | 🟡 | Installers build: `gradlew :launcher:jpackage` (app folder: `:launcher:jpackageImage`, tested). Windows `.exe/.msi` also need WiX (`winget install WiXToolset.WiXToolset`). ⛔ Signing needs a code-signing certificate (Windows: OV/EV cert; Mac: Apple Developer ID + notarisation). |
| Website, Discord server | ⛔ | Needs a domain (open question) and the owner's accounts. Then set `Updater.UPDATE_URL` and `DiscordPresence.CLIENT_ID`. |
| Privacy policy and terms of service | 🟡 | Drafts: `docs/privacy-policy-DRAFT.md`, `docs/terms-of-service-DRAFT.md`. ⛔ Must be reviewed by a lawyer (children's privacy laws). |
| Crash reporting (opt-in) | ✅ | Settings → General → Crash reports (off by default) + "Send to Nexus support" on a scan. Backend stores them (`/api/crash-reports`, staff view `/api/admin/crash-reports`). |
| "Not an official Minecraft product…" notice | ✅ in launcher / ⛔ website | Shown at the bottom of every launcher page and in Settings → About. Add it to the website. |

## Also needed before a public release
| Item | Status |
|---|---|
| Host the backend (PostgreSQL + the backend jar) and point the launcher at it (`-Dnexus.backend` → build setting) | ⛔ hosting decision |
| Set the owner's UUID in `nexus.admins` (backend `application.yml`) | ⛔ |
| Extend the chat filter word list (`backend/.../chat-filter.txt`) | ⛔ |
| Test Microsoft sign-in end to end (after Azure approval) | ⛔ then 🟡 |
| Translate remaining launcher text (only tabs/titles are in translation files so far) | 🟡 |
| Mod versions for 1.20.x (Fabric), Forge and 1.8.9 – only Fabric 26.3 exists now | 🟡 |
| Payments for Nexus Premium (price + provider) | ⛔ |
