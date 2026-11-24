# HU Video Maker (reconstruction of SRT Video Generator)

NEW code written from the APK findings. It is NOT the original app's code and was NOT built or tested
on a device yet. Own app id: com.abulolo.srtvideogenerator.

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

## Update 4.0 (Upgrade A: never lose work)
- Whole project (pictures, files, every per-scene setting) is saved automatically and restored when you reopen the app; the previous save is kept as a backup.
- Undo button for bulk and single changes.
- "Apply to all" asks first and can skip scenes that already have settings; shuffle / reset / new project also ask.
- Seconds per scene and total length shown in the list (needs the SRT).

## Update 4.1 (Upgrade B: scene editor)
- Scenes are a horizontal strip of thumbnails (scroll sideways), with "go to scene number".
- Tap a scene to edit it in one panel: live preview with its effect, transition, transition sound, scene sound, effect, voice-over play, back/next.
- Copy a scene's settings to a range of scenes (choose which parts), with a confirmation and Undo.

## Update 5.0 (Upgrade C: pop text and pictures)
- Per scene: pop text (typed in the app: style, colour, size) and pop pictures (from the gallery), with animation, exact start second and duration, position, tilt, and a pop sound (built-in or own file), all with a live preview.
- New sounds: ding, stamp thud, typewriter.
- Pop items are saved with the project and can be copied to other scenes.

## Update 5.1 (Upgrade D)
- Quick test render: render only a range of scenes (with their effects, pops, voice-over and sounds) to check an edit fast.
- Auto-ducking: music and scene sounds lower while the voice-over speaks (switch + amount).
- Logo on the video: pick a picture, corner, size and opacity.
- Project backup: save the whole project as a file and restore it.

## 5.3
Section 1 (files) removed to be rebuilt; version number shown under the title.

## 5.4
New section 1 (tiles), section 10 App colours (accent + background, remembered).

## 5.5
Movement for pop pictures/text: direction, distance, smooth, there-and-back, size change.

## 5.6
Test render: loudness scan of the voice-over only covers the rendered part (was the whole file, very slow).
