# Foz

Foz is a custom Android launcher app built with Kotlin and Jetpack Compose.

## Current App Scope

This app is already implemented as a working launcher foundation with:

- Home screen with:
  - Live clock/date
  - Favorites (pinned apps)
  - Widget area
  - Swipe gestures
- App drawer with:
  - Search
  - Alphabet index sidebar
  - App list sorted alphabetically
- App actions (long press):
  - Open app info
  - Uninstall
  - Pin/unpin favorites
  - Dynamic shortcuts (when available)
- Widget support:
  - Add/remove widget IDs
  - Host and render widget views
- Package change handling:
  - Refresh app list on install/uninstall
- Persisted launcher preferences with DataStore:
  - Pinned apps
  - Widget IDs

## Tech Stack

- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: MVVM-style (`LauncherViewModel` + state holder)
- Async: Kotlin Coroutines + Flow
- Storage: AndroidX DataStore (Preferences)
- Build: Gradle Kotlin DSL

## Android Configuration

- Namespace / Application ID: `com.example.foz`
- `minSdk`: 24
- `targetSdk`: 36
- `compileSdk`: 36
- Java/Kotlin target: 11
- Compose enabled

Manifest highlights:

- `MainActivity` is both:
  - standard launcher entry (`LAUNCHER`)
  - home app entry (`HOME`, `DEFAULT`)
- Launcher app query intent is declared in `<queries>`.

## Main Code Structure

- Entry/activity:
  - `app/src/main/java/com/example/foz/MainActivity.kt`
- ViewModel/state:
  - `app/src/main/java/com/example/foz/ui/LauncherViewModel.kt`
  - `app/src/main/java/com/example/foz/ui/LauncherUiState.kt`
- Data layer:
  - `app/src/main/java/com/example/foz/data/AppRepository.kt`
  - `app/src/main/java/com/example/foz/data/PrefsManager.kt`
- UI:
  - `app/src/main/java/com/example/foz/ui/home/HomeScreen.kt`
  - `app/src/main/java/com/example/foz/ui/applist/AppDrawerScreen.kt`
  - `app/src/main/java/com/example/foz/ui/applist/AlphabetSidebar.kt`
  - `app/src/main/java/com/example/foz/ui/applist/AppIcon.kt`
- Models:
  - `app/src/main/java/com/example/foz/model/AppInfo.kt`
  - `app/src/main/java/com/example/foz/model/AppShortcut.kt`
- Broadcast receiver:
  - `app/src/main/java/com/example/foz/receiver/PackageChangeReceiver.kt`

## Dependencies (Current)

Key libraries from `gradle/libs.versions.toml`:

- Android Gradle Plugin `8.11.2`
- Kotlin `2.0.21`
- Compose BOM `2024.09.00`
- Navigation Compose `2.9.0`
- DataStore Preferences `1.1.1`
- Coroutines Android `1.9.0`
- JUnit4 + AndroidX test libs

## How to Build / Run

1. Ensure Android Studio (or SDK + Gradle) is installed.
2. Ensure a valid JDK is configured (JAVA_HOME must point to a real Java installation).
3. Build/debug from Android Studio or run Gradle commands.

Common commands:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

Note: In this environment, Gradle task listing failed because `JAVA_HOME` is currently invalid.

## Implementation Status

### Implemented

- Core launcher home UI
- App drawer + search + alphabet jump
- Favorites pin/unpin persistence
- Long-press app actions
- Shortcut launch support (API level permitting)
- Widget add/remove and persistence
- App install/uninstall refresh handling
- Weather forecast (Open-Meteo): header display + forecast bottom sheet
- Media playback controls card (media sessions, legacy + Media3)
- Notification badges + per-app notification dialogs (NotificationListenerService)
- Hide apps
- App rename + custom icons from icon packs
- Notification quick replies (RemoteInput) + home notification summary + per-app mute
- Smart search: apps + contacts + keyless web instant answers (DuckDuckGo/Wikipedia) + web search row
- App usage: break reminder with daily screen time limit, suggested apps row, optional most-used sorting
- Calendar: agenda sheet on date tap + upcoming events preview on home (`READ_CALENDAR`)
- Folders in favorites with popup contents, rename/reorder/delete, add/remove apps

### Not Yet Fully Productionized

- Robust widget picker UX (currently first available widget flow)
- Full error handling and user-friendly recovery paths
- Launcher role onboarding and setup flow polish
- Comprehensive automated tests
- Performance profiling and optimization for large app lists
- Accessibility, i18n, and advanced theming polish

## Feature Roadmap (Niagara-inspired)

Feature ideas imported from Niagara Launcher and their current status in Foz.

Legend: ✅ implemented · 🟡 partially implemented · ❌ not implemented

| # | Feature | Status | Notes |
|---|---|---|---|
| 4 | Weather forecast | ✅ | `WeatherRepository` (Open-Meteo), header display + forecast sheet |
| 5 | Media player | ✅ | `MediaControllerManager` + `MediaControlCard` (legacy + Media3 sessions) |
| 8 | Hide apps | ✅ | `PrefsManager` hidden apps + Hide/Unhide app action |
| 1 | Quick replies | 🟡 | Notification actions already fire in `AppNotificationsDialog`; missing `RemoteInput` extraction + inline text reply |
| 7 | Notification summary | 🟡 | Per-app grouping + badges done; missing home summary pill and per-app mute |
| 12 | Smart search | ✅ | Drawer search fixed (dedicated results + clean on close), contacts (`READ_CONTACTS`), keyless web instant answers (DuckDuckGo + Wikipedia), web search row via `ACTION_WEB_SEARCH` |
| 6 | Usage limiter | ✅ | `UsageStatsRepository` (`PACKAGE_USAGE_STATS`): break reminder card when daily screen time exceeds the configured limit |
| 10 | Suggest frequently used apps | ✅ | "Suggested" row on home: top launch-count apps (7 days) excluding pinned/hidden |
| 11 | Sort apps by most used | ✅ | Optional usage-based drawer sorting (alphabet sections disabled while active) |
| 2 | Agenda (tap the date) | ✅ | Tapping the date opens the Agenda bottom sheet (`CalendarContract.Instances`, `READ_CALENDAR`, next 7 days grouped by day); weather stays on the temperature/location |
| 3 | Calendar preview | ✅ | Next 3 upcoming events as a compact card on home (toggle in Settings → Calendar; in-sheet permission prompt) |
| 9 | Folders & pop-ups | ✅ | Folders in favorites: 2×2 icon thumbnail, tap opens popup (`FozBottomSheet`), long-press for actions (rename/reorder/delete); apps added via app actions ("Add to folder" + picker/new folder); stored ordered in DataStore; adding an app unpins it |

Note: Niagara's "quick actions button" was intentionally dropped (swipe actions already cover this need).

### Execution order

1. **Phase 0 — Roadmap + settings layout planning** ✅
2. **Phase 1 — Notifications (1 + 7)**: inline quick replies (`RemoteInput`), home notification summary pill, per-app mute + central muted-apps management, fix badges suppressed by `swipeDownEnabled`. Adds the **Notifications** settings section. ✅
3. **Phase 2 — Smart search (12)**: fix drawer search, contacts search (`READ_CONTACTS`), keyless web instant answers (DuckDuckGo Instant Answer API + Wikipedia REST), "Search the web" row via `ACTION_WEB_SEARCH` with DuckDuckGo URL fallback. Adds the **Search** settings section. ✅
4. **Phase 3 — App usage group (6 + 10 + 11)**: `PACKAGE_USAGE_STATS` unlocks the usage limiter, frequent app suggestions and most-used sorting. ✅
5. **Phase 4 — Calendar group (2 + 3)**: `READ_CALENDAR` + `CalendarContract` → agenda on date tap + upcoming events preview. ✅
6. **Phase 5 — Folders & pop-ups (9)**: folder model (`FolderInfo`) + ordered DataStore persistence, folder item in favorites, popup with contents, folder actions, add/remove app via app actions. ✅
7. **Phase 6 — Optional**: Brave Search API ("bring your own key") for full inline web results.

### Settings layout plan

Settings stays a flat scrollable list with section headers (current declarative `SettingsItem` pattern):

```
APPEARANCE      (unchanged)
GESTURES        (unchanged)
WIDGETS         (unchanged)
NOTIFICATIONS   (new, Phase 1)
                  - Notification access (moved from System, renamed from "Media controls")
                  - Quick replies toggle
                  - Notification summary on home toggle
                  - Muted apps management
SEARCH          (new, Phase 2)
                  - Contacts in search toggle (requests READ_CONTACTS)
                  - Web instant answers toggle
SYSTEM          (cleaned up)
                  - 24h clock, change launcher, haptics, ad block
```

Every new toggle follows the existing pipeline: `PrefsManager` key → `LauncherViewModel` setter → `LauncherSettingsSnapshot` field → `SettingsScreen` item → `strings.xml` → `SettingsActivity` wiring.

## Next Steps (Prioritized)

## High Priority

1. **Search**
   - ✅ Fixed in Phase 2 (Smart search): the drawer now has its own search field with a dedicated results list, and the query is cleared when the drawer closes.

2. **Proper widget picker and binding UX**
   - Replace “first provider” behavior with a user-facing widget picker.
   - Handle bind/configuration flows and cancellation reliably.

3. **Reliability and error handling hardening**
   - Add explicit handling for launch failures, shortcut errors, and permission/state edge cases.
   - Improve recovery messaging for users.

4. **Automated testing baseline**
   - Add unit tests for `LauncherViewModel` logic.
   - Add UI tests for drawer/search/favorites interactions.

## Medium Priority

1. **Performance improvements**
   - Optimize app list loading/rendering for devices with many apps.
   - Reduce unnecessary recompositions and widget host view churn.

2. **UI/UX polish**
   - Better animations/transitions for drawer and modal actions.
   - Improved app action sheet design and spacing consistency.
   - Replace modals for bottom sheet

3. **Search and app organization enhancements**
   - Add fuzzy matching and recent apps.
   - Optional app categories/tabs.

## Low Priority

1. **Advanced customization**
   - Icon packs, custom grid layouts, and gesture remapping.
   - Rename apps.

2. **Backup/migration improvements**
   - Better migration for pinned apps/widget setup across reinstalls/devices.

3. **Analytics/telemetry (privacy-first, optional)**
   - Local-only diagnostics or opt-in telemetry for crash/UX insights.

4. **Feature extensions**
   - Widgets presets and richer personalization (feature-specific ideas live in the Feature Roadmap above).


## Release new version

```bash
git tag v1.0.0
git push origin v1.0.0
```