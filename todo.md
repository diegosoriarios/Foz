# Foz TODO

## High Priority

- [x] Fix crash when tapping "Use system wallpaper"; app currently crashes and then fails to open afterward.
- [x] In app drawer, when list is scrolled to top and user swipes down, close the app drawer.
- [x] Remove floating action button from home screen (currently unused).
- [x] Improve app list alphabet navigation:
  - [x] Tapping a letter should jump to that letter and scroll to first matching app.
  - [x] Add haptic feedback while sliding across alphabet index.
- [ ] Fix app drawer behavior so when drawer is opened by swipe-up, it can be closed by swipe-down.
- [ ] Fix crash when long-pressing an app item inside the app drawer.
- [ ] Add wallpaper controls:
  - [ ] Toggle to use system wallpaper as launcher background.
  - [ ] Action to open system wallpaper picker and change wallpaper.

## Medium Priority

- [ ] Polish wallpaper UI placement and styling consistency with home layout.
- [ ] Add user feedback when wallpaper picker is unavailable on specific OEM builds.
- [ ] Add test coverage for drawer gestures and long-press app actions.

## Low Priority

- [ ] Add support for custom static image background as alternative to system wallpaper.
- [ ] Add blur/dim strength controls for wallpaper readability.
- [ ] Add launcher settings screen section for background personalization.
