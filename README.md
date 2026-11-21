# HU Video Maker (reconstruction of SRT Video Generator)

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

## Update 3.0 (Part 2)
- New transitions: dip to white, flash, circle reveal, pixelate, shake cut.
- New sounds: click, pop, zap, page turn, deep boom; each image can have its own sound (or none).
- Per-image overlay effects with strength, speed, "only at the start" timer and live preview: shaky white edge, snow, rain, dust, sparks, old film, vignette, glow edge, flash pulses, screen shake, zoom pulse.
- Subtitle styles (5 presets), size and position.
- Sharpen / contrast / colour (no AI upscaling).

## Update 3.1 (Part 3)
- Each image has a ▶ Voice button that plays just that scene's part of the voice-over.
- Scene sounds per image (or all images): rain, storm, fire, microphone crackle, horse hooves, wind, or your own file; volume and "only at the start" timer.
- 15 more overlay effects: falling leaves, lightning, fog, light rays, film burn, camera REC frame, ash, fireflies, sun glare, cracked glass corners, night tint, sepia, smoke, candlelight, cinema bars.

## Update 3.2
- Animated clips (video files) can be mixed with still images, in the picker or in a folder. Each clip is one scene: cut to the scene length, or frozen on its last frame if shorter. The clip's own sound is not used.
