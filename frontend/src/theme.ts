import themeCss from './theme.css?inline'

// Both page bundles are built as fully self-contained IIFEs (see
// vite.config.ts) - SonarQube loads them as classic <script> tags with no
// separate stylesheet asset, so CSS must travel inside the JS and be
// injected manually. `?inline` makes Vite return the CSS as a plain string
// instead of auto-injecting/extracting it, which keeps each bundle single-
// file. Guarded by a fixed id so mounting both page extensions in the same
// SPA session (or Fast Refresh in dev) never inserts duplicate <style> tags.
const STYLE_ID = 'chillcode-quality-theme'

export function injectTheme(): void {
  if (document.getElementById(STYLE_ID)) {
    return
  }
  const style = document.createElement('style')
  style.id = STYLE_ID
  style.textContent = themeCss
  document.head.appendChild(style)
}
