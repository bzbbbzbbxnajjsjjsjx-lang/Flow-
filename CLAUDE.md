# Akış

Android app (Jetpack Compose, Material 3). The repo currently holds only the built release
`apk/Akis-1.18.0.apk`; the source is not here yet. The engineering setup is copied from Notlar
(bzbbbzbbxnajjsjjsjx-lang/Notlar) so work on Akış follows the same conventions once source lands.

## Conventions (same as Notlar)

- Code, comments, commit messages: English. User-facing text and `README.md`: Turkish.
- Search and dates use the Turkish locale (`tr-TR`), never the default one.
- Accessibility: icon-only buttons have a `contentDescription`, headings are marked, touch
  targets >= 48dp.
- Do not change behavior that already works in the shipped APK; add features beside it.

## Agents

`.claude/agents/` holds three subagents; use them, in this order, for every change:

- `dal-bekcisi` - at the start of a task: confirms the branch contains the latest release.
- `tasarimci` - for any new screen or vague UI request: sketches it in M3E Canvas
  (https://lnkiai.github.io/m3e-canvas/) and returns a link; get the user's approval before
  writing code. Sketches are saved under `docs/m3e-canvas/`.
- `kontrolcu` - after the change and before every push or release: detekt, lint, tests, release
  build, and a look at every changed screenshot against the request. These checks assume the
  Notlar Gradle layout (`./gradlew detekt lintDebug testDebugUnitTest assembleRelease`), so
  they apply once the source and build files are added.

## Skills

`.claude/skills/` holds Compose UI, performance audit, accessibility and testing guidance, plus
`material3-expressive`. Load the matching skill before working in that area.
