# Foz TODO

## High Priority

- [x] Move alphabet list to the first screen next to favorites.
- [x] Change swipe-up behavior: do not open app list; only show search app input and widgets.
- [x] Move apps/widgets/search from home into swipe-up panel.
- [x] Add option to add widgets in swipe-up panel.
- [x] Close app list when at top and user pulls down further.
- [x] Touching alphabet list should open app list; swiping down from top should close app list and return to first screen.
- [x] On first screen, swipe down should open notifications tray.
- [x] Fix home sizing/layout so content uses full screen: alphabet must span full height and wallpaper controls must stay at bottom.
- [x] Ensure app drawer closes whenever app list is at top and user keeps pulling down.
- [x] Remove padding from alphabet on home screen.
- [ ] Change selected letter margin size
- [ ] Fix crash when tapping "Use system wallpaper"; app currently crashes and then fails to open afterward.
- [ ] In app drawer, when list is scrolled to top and user swipes down, close the app drawer.
- [ ] Remove floating action button from home screen (currently unused).
- [ ] Improve app list alphabet navigation:
  - [ ] Tapping a letter should jump to that letter and scroll to first matching app.
  - [ ] Add haptic feedback while sliding across alphabet index.
- [x] Fix app drawer behavior so when drawer is opened by swipe-up, it can be closed by swipe-down.
- [x] Fix crash when long-pressing an app item inside the app drawer.
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
