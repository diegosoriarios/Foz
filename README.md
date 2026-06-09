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

### Not Yet Fully Productionized

- Robust widget picker UX (currently first available widget flow)
- Full error handling and user-friendly recovery paths
- Launcher role onboarding and setup flow polish
- Comprehensive automated tests
- Performance profiling and optimization for large app lists
- Accessibility, i18n, and advanced theming polish

## Next Steps (Prioritized)

## High Priority

1. **Search**
   - AppDrawer search don't work how it supposed to, it filter the alphabet list
   - AppDrawer search list the app
   - Alphabet it's never filtered
   - AppDrawer search it's clean when closed

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

2. **Backup/migration improvements**
   - Better migration for pinned apps/widget setup across reinstalls/devices.

3. **Analytics/telemetry (privacy-first, optional)**
   - Local-only diagnostics or opt-in telemetry for crash/UX insights.

4. **Feature extensions**
   - Smart suggestions, widgets presets, and richer personalization.


## Release new version
´´´
git tag v1.0.0
git push origin v1.0.0
´´´