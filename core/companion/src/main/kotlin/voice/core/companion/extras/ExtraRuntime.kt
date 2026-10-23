package voice.core.companion.extras

/**
 * The contract between a shelf Extra and the app. Claude writes pages against [EXTRA_CONTRACT]; the viewer injects
 * [BRIDGE_SHIM] and the book's colors before the page's own code runs, and backs `EarwigNative` with the app.
 */
const val EXTRA_CONTRACT = """The page runs in an Android WebView inside the app, offline, with this
Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:;
font-src data:. So: one HTML file, inline <style> and <script> only, no external files, no fetch, images only as
inline SVG or data: URIs.

The app provides a global `earwig` object before your script runs:
- earwig.book() returns {title, author, kind, positionMs, durationMs, summary}. kind is "fiction" or "nonfiction";
  positionMs is how far the listener is in the whole book.
- earwig.transcript(maxChars) returns the transcript the listener has heard so far (up to their position), at most
  maxChars characters from the end, as plain text. Use it for anything about "what I've heard".
- earwig.ask(question) returns a Promise of Claude's answer as plain text, grounded in the book and never past the
  listener's position. Takes a few seconds; show a loading state. Use it sparingly, on a button press.
- earwig.get(key) returns a saved string or null; earwig.set(key, value) saves a string for this Extra. Use these to
  keep the listener's inputs and progress between visits.
- earwig.seek(positionMs) jumps the player to a position in the whole book and closes the page.
- earwig.close() closes the page.

Theme: use these CSS variables, already defined on :root, for every color and font so the page matches the book:
--bg (page background), --card (raised surfaces), --ink (text), --muted (secondary text and lines), --accent
(buttons and highlights), --accent-ink (text on --accent), --font-display (headings), --font-body (everything else).
The page is a light, bold design: no dark backgrounds.

Layout: a phone screen about 400 px wide, portrait, touch only. Use large tap targets (at least 44 px), 16 px side
padding, readable 16 px body text, no horizontal scrolling. The app shows its own close button at the top right,
so leave 56 px clear there. Keep the whole file under 150 KB."""

/** Defines the `earwig` object the contract promises, on top of the `EarwigNative` interface the viewer adds. */
const val BRIDGE_SHIM = """(function () {
  var pending = {};
  var next = 1;
  window.__earwigResolve = function (id, text, error) {
    var p = pending[id];
    if (!p) return;
    delete pending[id];
    if (error) { p.reject(new Error(error)); } else { p.resolve(text); }
  };
  window.earwig = {
    book: function () { return JSON.parse(EarwigNative.book()); },
    transcript: function (maxChars) { return EarwigNative.transcript(maxChars || 20000); },
    ask: function (question) {
      return new Promise(function (resolve, reject) {
        var id = next++;
        pending[id] = { resolve: resolve, reject: reject };
        EarwigNative.ask(id, String(question));
      });
    },
    get: function (key) { var v = EarwigNative.get(String(key)); return v === null || v === undefined ? null : v; },
    set: function (key, value) { EarwigNative.set(String(key), String(value)); },
    seek: function (positionMs) { EarwigNative.seek(Math.round(positionMs)); },
    close: function () { EarwigNative.close(); }
  };
  window.addEventListener('error', function (e) {
    EarwigNative.reportError(String(e.message) + ' at line ' + e.lineno);
  });
  window.addEventListener('unhandledrejection', function (e) {
    EarwigNative.reportError('Unhandled promise rejection: ' + (e.reason && e.reason.message || e.reason));
  });
})();"""

/** The page's sandbox: nothing from outside, inline code and styles only. */
const val EXTRA_CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
  "img-src data:; font-src data:"

/** The book's colors and type as the CSS variables the contract names, with 0xAARRGGBB colors. */
data class ExtraTheme(
  val background: Int,
  val card: Int,
  val ink: Int,
  val muted: Int,
  val accent: Int,
  val accentInk: Int,
  val displayFont: String,
  val bodyFont: String,
) {
  fun css(): String = ":root{--bg:${background.css()};--card:${card.css()};--ink:${ink.css()};--muted:${muted.css()};" +
    "--accent:${accent.css()};--accent-ink:${accentInk.css()};--font-display:$displayFont;--font-body:$bodyFont}" +
    "html,body{background:var(--bg);color:var(--ink);font-family:var(--font-body);margin:0}"

  private fun Int.css(): String = "#%06X".format(this and 0xFFFFFF)
}

/**
 * The page as the viewer loads it: the sandbox policy, the theme and the bridge go first in its head, so they apply
 * before any of the page's own code.
 */
fun wrapExtraPage(html: String, theme: ExtraTheme): String {
  val head = "<meta http-equiv=\"Content-Security-Policy\" content=\"$EXTRA_CSP\">" +
    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
    "<style>${theme.css()}</style><script>$BRIDGE_SHIM</script>"
  val headTag = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
  if (headTag != null) {
    return html.substring(0, headTag.range.last + 1) + head + html.substring(headTag.range.last + 1)
  }
  val htmlTag = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
  if (htmlTag != null) {
    return html.substring(0, htmlTag.range.last + 1) + "<head>$head</head>" + html.substring(htmlTag.range.last + 1)
  }
  return "<!doctype html><html><head>$head</head><body>$html</body></html>"
}
