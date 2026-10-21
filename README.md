# magnum

A music player for Android styled after Apple Music, built for lossless files.

- 32-bit float audio pipeline (24/32-bit FLAC never gets squeezed to 16-bit before EQ)
- Parametric EQ with AutoEQ `ParametricEQ.txt` import, plus a 10-band tweak EQ and automatic clip protection
- Live quality badge on Now Playing: Lossless / Hi-Res Lossless, bit depth, sample rate, bitrate
- Background playback with lock-screen and notification controls
- Light and dark mode following the system

## Build on GitHub

1. Create a new GitHub repository and push this folder to the `main` branch.
2. Open the **Actions** tab. The "Build APK" workflow runs on every push (or click **Run workflow**).
3. When it finishes, download the **magnum-apk** artifact, unzip it, and install `app-release.apk`.
