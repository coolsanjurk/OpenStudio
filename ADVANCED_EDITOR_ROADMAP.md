# OpenStudio Advanced Editor Implementation Plan

This is an implementation checklist, not a claim that every item already works. Features marked Planned need implementation and automated/device testing.

## Offline image engine
- [x] Brightness, contrast, saturation, warmth controls
- [x] Rotate and flip
- [x] Chunked sharpen filter
- [x] Radial vignette
- [x] Center crop presets (1:1, 4:5, 16:9)
- [x] Basic text overlay stack (white text with shadow)
- [ ] Crop handles, straighten and perspective correction
- [ ] Undo/redo command history and edit-session persistence
- [ ] Highlights, shadows, whites, blacks, curves, HSL and white balance
- [ ] Denoise, deblur and robust high-resolution memory handling

## Retouching and portraiture
- [ ] Spot healing / inpainting for blemishes, dust and small objects
- [ ] Patch selection and content-aware fill
- [ ] Clone stamp with source-point selection and brush controls
- [ ] Dodge and burn brush with soft brush, flow and exposure controls
- [ ] Face-aware skin smoothing and portrait corrections
- [ ] Neural face controls (expression, age, gaze) — requires suitable licensed model(s), evaluation and device-performance checks

## Layers and graphic design
- [ ] Persistent non-destructive layer/document model
- [ ] Layer visibility, opacity, reorder, duplicate, groups and blend modes
- [ ] Text formatting: font, size, colour, alignment, spacing/kerning/leading
- [ ] Vector paths, pen tool and editable shapes
- [ ] Multiple artboards and export sizes
- [ ] Smart-object-like linked source layers and non-destructive transforms

## Painting and illustration
- [ ] Brush engine with size, hardness, opacity, flow and pressure where hardware supports it
- [ ] Preset brush families (ink, charcoal, watercolor/oil-like)
- [ ] Mixer brush / wet-paint simulation
- [ ] Symmetry axes and mirrored painting

## Selection, compositing and AI
- [ ] Subject/object selection and editable masks
- [ ] Adjustment layers and blending modes
- [ ] Sky replacement with foreground-aware colour matching
- [ ] Generative fill and generative expand using a selected model/provider
- [ ] Background removal/replacement
- [ ] AI preview, undo, explicit consent, clear local-vs-cloud labels

## Privacy and release gates
- Core editing stays offline; do not add network permission unless a separately reviewed opt-in AI architecture requires it.
- Never transmit photos without a clear, explicit user action and disclosure.
- Keep originals unchanged and export a separate copy.
- Automated build, algorithm tests, large-image tests, privacy checks, and real Android device tests are release gates.
- No feature is considered done merely because a button or placeholder exists.
