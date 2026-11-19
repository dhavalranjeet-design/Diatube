package com.steeben.dial.tube

/**
 * JavaScript injected into m.youtube.com — the same idea NouTube uses:
 * hide Shorts, skip/hide ads, and stop YouTube pausing when the app is in the background.
 */
object TubeScripts {

    private const val SHORTS_CSS = """
ytm-reel-shelf-renderer,
ytm-shorts-lockup-view-model,
ytm-shorts-lockup-view-model-v2,
ytm-rich-section-renderer:has(ytm-shorts-lockup-view-model),
ytm-pivot-bar-item-renderer:has(.pivot-shorts) { display: none !important; }
"""

    private const val ADS_CSS = """
ytm-promoted-sparkles-web-renderer,
ytm-promoted-video-renderer,
ytm-companion-slot,
ad-slot-renderer,
ytm-statement-banner-renderer { display: none !important; }
"""

    fun build(
        hideShorts: Boolean = true,
        blockAds: Boolean = true,
        keepPlaying: Boolean = true,
        customCss: String = "",
    ): String = """
(function () {
  if (window.__dialTube) return;
  window.__dialTube = true;

  var css = `${if (hideShorts) SHORTS_CSS else ""}${if (blockAds) ADS_CSS else ""}${customCss}`;
  var style = document.createElement('style');
  style.textContent = css;
  (document.head || document.documentElement).appendChild(style);

  if ($keepPlaying) {
    // Make the page believe it is always visible so playback never auto-pauses.
    try {
      Object.defineProperty(document, 'hidden', { get: function () { return false; } });
      Object.defineProperty(document, 'visibilityState', { get: function () { return 'visible'; } });
    } catch (e) {}
    document.addEventListener('visibilitychange', function (e) { e.stopImmediatePropagation(); }, true);
    window.addEventListener('blur', function (e) { e.stopImmediatePropagation(); }, true);
  }

  setInterval(function () {
    if ($hideShorts) {
      var m = location.pathname.match(/^\/shorts\/([\w-]+)/);
      if (m) location.replace('/watch?v=' + m[1]);
    }
    if ($blockAds) {
      var v = document.querySelector('video');
      var ad = document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay');
      if (ad && v && isFinite(v.duration) && v.duration > 0) {
        v.muted = true;
        v.currentTime = v.duration;
      }
      var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
      if (skip) skip.click();
    }
  }, 400);
})();
"""
}
