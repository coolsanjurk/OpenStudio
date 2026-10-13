# OpenStudio Feature Roadmap

## Product direction
Build a capable Android editor for the Samsung Galaxy S24 Ultra and other Android phones. Prefer privacy, no mandatory subscription, clear controls, and reliable exports.

## Phase 1 — Stable foundation
- [ ] Compile and package a debug APK
- [ ] Open images through Android's system photo picker
- [ ] Preview edits and preserve the source image
- [ ] Brightness, contrast, saturation, warmth
- [ ] Rotate, flip, reset
- [ ] Export JPEG into Pictures/OpenStudio
- [ ] Test picker cancellation, invalid files, large images, repeated edits, export errors, and offline operation

## Phase 2 — Essential editor
- [ ] Crop and straighten
- [ ] Auto colour correction
- [ ] Blur effect with strength control
- [ ] Background correction controls
- [ ] Skin-tone correction that avoids changing the whole image unnecessarily
- [ ] Undo/redo and before/after comparison
- [ ] Better high-resolution processing and memory safeguards

## Phase 3 — Retouching
- [ ] Spot/healing tool for small blemishes and unwanted objects
- [ ] Brush size, zoom, pan, undo, and preview
- [ ] Subject/background selection and masking
- [ ] AI background removal, with edge refinement and hair-aware mask if feasible

## Phase 4 — AI tools
- [ ] Auto sky enhancement/replacement with mask and preview
- [ ] AI edit suggestions that are optional and reversible
- [ ] Evaluate on-device AI first where practical
- [ ] Optional cloud AI for heavier tasks; explicit consent before upload, progress indicator, error handling, and clear privacy explanation
- [ ] No secret uploads; never upload automatically just because a photo is opened

## Phase 5 — RAW workflow
- [ ] Inspect supported camera RAW formats and Android decoder support
- [ ] Preserve RAW original and export a rendered copy
- [ ] Exposure, highlights, shadows, white balance, and detail controls where supported
- [ ] Gracefully explain unsupported RAW files rather than crashing

## Test policy
- Do not claim a feature is implemented until code is present and tested.
- Do not claim 100 tests passed unless 100 distinct test cases were actually executed and results recorded.
- Automated build/unit checks do not replace testing on a physical Samsung Galaxy S24 Ultra.
- Image quality requires visual evaluation on representative photos, not just compilation.
