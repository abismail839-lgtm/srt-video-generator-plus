# SRT Video Generator+ (reconstruction)

NEW code written from the APK findings. It is NOT the original app's code and was NOT built or tested
on a device yet. Installs next to the original (id: com.bahaa.srtvideogenerator.upgraded).

## Build on your phone (no computer)
1. Create an empty repo on github.com (browser).
2. In Termux: `pkg install git unzip`, then `unzip` this zip.
3. `cd srt-video-generator-plus && git init && git add . && git commit -m init`
4. `git remote add origin <repo url> && git push -u origin master` (use a GitHub token as password).
5. GitHub > Actions > "Build APK" > open the run > download artifact > unzip > install the APK.

If the build fails, copy the red error lines from the Actions log and send them to Claude.

## Files
- gl/SurfaceRenderer.kt  – shaders: fade, dissolve, slides, spin, blur, wipe, glitch, zoom + captions
- Timeline.kt            – scene timing, default/override/random transition resolution
- NativeMediaGenerator.kt – renderImages, muxAudio, publishVideo
- audio/*                – decode, mix (voice, music, fades, loop), synthesised whoosh/swipe/impact, AAC
- ui/*, MainViewModel.kt – Compose screen, per-image transitions, live preview
