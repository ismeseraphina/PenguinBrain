<h1 align="center">
  <img alt="Penguin Brain" src="core/ui/src/main/res/drawable/penguin_app_icon.jpg" width="160" />
  <br>
  Penguin Brain
</h1>

<h3 align="center">A cute penguin remix of MyBrain: Tasks, Notes, Diary, Bookmarks, Calendar and AI assistant, now with sign in &amp; sync to the Penguin Brain website.</h3>

> **Modified by Seraphina**
> GitHub: [@ismeseraphina](https://github.com/ismeseraphina) · old account [@Cryjai](https://github.com/Cryjai)
> Feedback: [ismeseraphina.com](https://ismeseraphina.com)
>
> Penguin Brain is modified from the code of **[MyBrain](https://github.com/mhss1/MyBrain)** by **mhss1 (Mohamed Shaaban)** and is released under the same **GNU GPL v3.0** license (see [LICENSE](LICENSE)). It is a personal, non-commercial project. Please read [Usage, credit and commercial use](#usage-credit-and-commercial-use).

## Download
Every push to `master` builds a debug APK with GitHub Actions:
- **Releases** tab → `Penguin Brain (latest build)` → `PenguinBrain-debug.apk`
- or **Actions** tab → latest run → artifact `PenguinBrain-debug-apk`

## Web version
[Penguin Brain Website](https://penguinbrain.acry.workers.dev/) ([source](https://github.com/ismeseraphina/PenguinBrainWebsite)) runs in the browser and syncs with this app.

## What Seraphina changed and added

**Look and feel**
- Penguin app icon, penguin space cards and a pastel pink theme.
- Settings shortcut on the dashboard; "Modified by" credits and feedback link in Settings → About.

**Sign in & Sync with the website** (Settings → Sign in & Sync)
- Two ways to sign in: a **Penguin Brain account** (email + password on the website, then an app token) or a **GitHub fine-grained token** with a private data repository.
- Two-way sync of notes, note folders, tasks, diary, bookmarks and calendar events. Per item, the newest `updatedDate` wins; deletions sync with tombstones. Works offline and uploads later.
- The sync file uses the same format as MyBrain's JSON backup, so it can also be imported from Settings → Export/Import.
- Optional auto sync on app start and every hour.

**Calendar**
- A "Penguin Brain" phone calendar that syncs with the website calendar: add, edit or delete on either side and it shows on the other, with no duplicates. Hong Kong (local) time is kept correctly, all-day events use Android's UTC convention.
- Event reminders from the app itself (default 10 minutes before), rescheduled after a reboot.
- Event colours from the website's categories.

**Clock (Dashboard)**
- Big ring countdown timer with a progress bar, Pause / Resume / +5 min / Stop.
- Swipeable cards: event in progress, routines (lunch, eye break, dinner, evening shutdown), tasks inside their focus window before the deadline, upcoming events, and a manual 25 / 50 / 90 minute timer.
- Notifications when an event starts, a task's focus window opens, a routine is due and when the timer ends. Nothing starts by itself: you press START.

**Bug fixes backported from [mhss1/MyBrain](https://github.com/mhss1/MyBrain)**
- Keyboard covering task details, task edits lost after re-entering the app, date picker using the wrong day in some time zones (UTC fix), Markdown tables not rendering, broken GitHub links, a string typo.

## Setting up sync (one time)
**Option A, Penguin Brain account (easier):** on the [website](https://penguinbrain.acry.workers.dev/) open Settings → Sign in & Sync → create an account → *Create app token*. In the app (Settings → Sign in & Sync) paste the token, set Repository owner to `https://penguinbrain.acry.workers.dev` and Repository name to `data`.

**Option B, GitHub repository:**
1. [Create a private repository](https://github.com/new?name=PenguinBrainData&visibility=private) called `PenguinBrainData`.
2. [Create a fine-grained token](https://github.com/settings/personal-access-tokens/new?name=Penguin+Brain+Sync&contents=write) → Repository access: *Only select repositories* → `PenguinBrainData` → Permissions: **Contents: Read and write**.
3. In the app: Settings → Sign in &amp; Sync → paste token, owner (your GitHub username) and repo (`PenguinBrainData`) → Sign in.
4. Do the same on the website. Both now share the same data.

Keep tokens private. You can revoke them anytime (website: Settings → Sign in & Sync; GitHub: token settings).

## Original MyBrain features
- Local and private: without signing in, nothing leaves your phone.
- Tasks with priority, sub-tasks, due date, reminders and recurrence.
- Markdown notes with folders.
- Diary with mood tracking and charts.
- Bookmarks from the share menu.
- Calendar events list / month view and widgets.
- Dashboard and an AI assistant.

## Technologies
Kotlin, multi-module Clean Architecture, Jetpack Compose, Glance widgets, MVI, Room, Koin, Ktor, DataStore, Coroutines/Flows, WorkManager, AlarmManager, Koog.

## Usage, credit and commercial use

Penguin Brain is Seraphina's personal remix. It is free and not sold.

**Please do not:**
- use Penguin Brain, its name or its penguin look for a **commercial** product, paid app, paid service or ads;
- present this remix, or a copy of it, as **your own project**, or remove the "Modified by Seraphina" and MyBrain credits.

**What the license requires (GPL-3.0, same as MyBrain):** if you share a copy or a modified version, you must
- keep all copyright notices and credits (MyBrain by mhss1, and this remix by Seraphina);
- clearly say that you changed it and what you changed;
- release your version under GPL-3.0 with its full source code.

Because this code comes from a GPL-3.0 project, the requests above about commercial use are Seraphina's personal request and cannot add extra legal limits to the GPL code. The credit and source rules above are required by the license. Questions or permission requests: [ismeseraphina.com](https://ismeseraphina.com).

### Penguin artwork

The penguin pictures (app icon, space cards, Clock penguin and the other `penguin_*` images) were made by Seraphina with AI image tools. They are **not** part of the GPL-3.0 code license. All rights reserved to the extent the law allows: please do not reuse, sell or redistribute them, or use them in another app, without Seraphina's permission. If you fork this project, replace the penguin images with your own. (Hong Kong's Copyright Ordinance protects computer-generated works; rules differ in other countries.)

---
Original icons attribution (from MyBrain):
<a href="https://www.flaticon.com/free-icons/document" title="document icons">Document icons created by Freepik - Flaticon</a>,
<a href="https://www.flaticon.com/free-icons/list" title="list icons">List icons created by Freepik - Flaticon</a>,
<a href="https://www.flaticon.com/free-icons/3d-calendar" title="3d calendar icons">3d calendar icons created by Freepik - Flaticon</a>,
<a href="https://www.flaticon.com/free-icons/bookmark" title="bookmark icons">Bookmark icons created by Freepik - Flaticon</a>,
<a href="https://www.flaticon.com/free-icons/book" title="book icons">Book icons created by Freepik - Flaticon</a>,
<a href="https://www.flaticon.com/free-icons/chatbot" title="chatbot icons">Chatbot icons created by HideMaru - Flaticon</a>
