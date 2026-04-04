# Fixing Android Screen Capture Crash

- [ ] Investigate `AndroidManifest.xml` for foreground service type <!-- id: 0 -->
- [ ] Check `AndroidScreenManager` and `MainActivity` for service start logic <!-- id: 1 -->
- [ ] Fix: Add `mediaProjection` type to Service in Manifest <!-- id: 2 -->
- [ ] Fix: Ensure Service is started *before* requesting MediaProjection <!-- id: 3 -->
- [ ] Verify fix by reviewing code logic <!-- id: 4 -->
