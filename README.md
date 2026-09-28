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
> Penguin Brain is modified from the code of **[MyBrain](https://github.com/mhss1/MyBrain)** by **mhss1 (Mohamed Shaaban)** and is released under the same **GNU GPL v3.0** license (see [LICENSE](LICENSE)). It is a personal, non-commercial project.

## Download
Every push to `master` builds a debug APK with GitHub Actions:
- **Releases** tab → `Penguin Brain (latest build)` → `PenguinBrain-debug.apk`
- or **Actions** tab → latest run → artifact `PenguinBrain-debug-apk`

## Web version
[Penguin Brain Website](https://ismeseraphina.github.io/PenguinBrainWebsite/) ([source](https://github.com/ismeseraphina/PenguinBrainWebsite)) runs in the browser and syncs with this app.

## What Seraphina changed
- Penguin icons, penguin space cards and a pastel pink theme.
- Settings shortcut on the dashboard.
- **Sign in &amp; Sync** (Settings → Sign in &amp; Sync): sign in with a GitHub fine-grained token and two-way sync notes, note folders, tasks, diary and bookmarks with the Penguin Brain website.
  - Data is stored as one JSON file (`penguinbrain-sync.json`) in a **private repository you own** (e.g. `PenguinBrainData`). No extra server.
  - Merge is per item (newest `updatedDate` wins); deletions are synced with tombstones.
  - The sync file uses the same format as MyBrain's JSON backup, so it can also be imported from Settings → Export/Import.
  - Optional auto sync on app start and every hour.
- "Modified by" credits and feedback link in Settings → About.
- Backported upstream bug fixes from mhss1/MyBrain: keyboard covering task details, task edits lost after re-entering the app, date picker using the wrong day in some time zones (UTC fix), Markdown tables not rendering, broken GitHub links, and a string typo.

## Setting up sync (one time)
1. [Create a private repository](https://github.com/new?name=PenguinBrainData&visibility=private) called `PenguinBrainData`.
2. [Create a fine-grained token](https://github.com/settings/personal-access-tokens/new?name=Penguin+Brain+Sync&contents=write) → Repository access: *Only select repositories* → `PenguinBrainData` → Permissions: **Contents: Read and write**.
3. In the app: Settings → Sign in &amp; Sync → paste token, owner (`ismeseraphina`) and repo (`PenguinBrainData`) → Sign in.
4. Do the same on the website. Both now share the same data.

Keep the token private. You can revoke it anytime on GitHub.

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

---
Original icons attribution (from MyBrain):
<a href="https://www.flaticon.com/free-icons/document" title="document icons">Document icons created by Freepik - Flaticon</a>,
<a href="https://www.flaticon.com/free-icons/list" title="list icons">List icons created by Freepik - Flaticon</a>,
<a href="https://www.flaticon.com/free-icons/3d-calendar" title="3d calendar icons">3d calendar icons created by Freepik - Flaticon</a>,
<a href="https://www.flaticon.com/free-icons/bookmark" title="bookmark icons">Bookmark icons created by Freepik - Flaticon</a>,
<a href="https://www.flaticon.com/free-icons/book" title="book icons">Book icons created by Freepik - Flaticon</a>,
<a href="https://www.flaticon.com/free-icons/chatbot" title="chatbot icons">Chatbot icons created by HideMaru - Flaticon</a>
