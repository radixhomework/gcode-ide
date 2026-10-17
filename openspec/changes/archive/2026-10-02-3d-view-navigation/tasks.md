# Tasks: 3d-view-navigation

## 1. Pan implementation

- [x] 1.1 Add the pure pan-delta helper (camera right/up axes from yaw/pitch, scaled by distance) to `Preview3DView`; unit tests assert screen-right maps to camera-right for several yaw/pitch combinations and that scaling follows distance
- [x] 1.2 Wrap content and camera rig in a pan group with a `Translate`; handle right-button drags (press state decides orbit vs pan, events consumed) and suppress the context menu over the preview; expose pan offset getters
- [x] 1.3 TestFX tests: right-drag changes the pan offset without touching yaw/pitch; left-drag still orbits without touching pan; wheel still dollies; pan persists across a subsequent orbit and zoom

## 2. Wrap-up

- [x] 2.1 Run the full `mvn test` suite green; rebuild the app image and verify by hand on the dev machine (right-drag pans straight, no context menu)
