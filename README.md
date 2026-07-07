#     Motion Studio

A Jetpack Compose video editor for Android — timeline-based layer editing,
keyframe animation (position, opacity, bezier easing), blend modes and visual
effects, vector shape drawing, and MediaCodec/MediaMuxer-based video export.

## Build

**Prerequisites:** Android Studio (or Gradle + Android SDK from the command line)

1. Open the project directory in Android Studio and let it sync.
2. Run on an emulator or physical device (`minSdk 24`).

### Release builds

Release builds are signed using a keystore referenced via environment
variables (see `app/build.gradle.kts`):

- `KEYSTORE_PATH` — path to the upload keystore (defaults to
  `my-upload-key.jks` in the project root)
- `STORE_PASSWORD`
- `KEY_PASSWORD`

These are expected to be provided by the CI environment (e.g. GitHub Actions
secrets) rather than committed to the repo.
