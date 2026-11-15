package com.toonitalia.batmanplayer.block

/**
 * Script iniettati nella WebView:
 *  - CLEANUP_JS: rimuove banner/pubblicità/overlay, blocca popunder e avvia il player.
 *  - SNIFFER_JS: intercetta l'URL reale del video per riprodurlo con il player nativo.
 */
object AdBlockScripts {

    val CLEANUP_JS: String = """
(function () {
  try {
    // ---------- 1. rimozione banner, iframe e container pubblicitari ----------
    var AD_SELECTORS = [
      'ins.adsbygoogle', '.adsbygoogle', '[data-ad-client]', '[data-ad-slot]', '[data-adzone]',
      'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]', 'iframe[src*="googleadservices"]',
      'iframe[src*="adsterra"]', 'iframe[src*="exoclick"]', 'iframe[src*="exosrv"]', 'iframe[src*="popads"]',
      'iframe[src*="popcash"]', 'iframe[src*="hilltopads"]', 'iframe[src*="juicyads"]', 'iframe[src*="propellerads"]',
      'iframe[src*="adcash"]', 'iframe[src*="onclickads"]', 'iframe[src*="adskeeper"]', 'iframe[src*="revcontent"]',
      'iframe[src*="taboola"]', 'iframe[src*="outbrain"]', 'iframe[src*="mgid"]', 'iframe[src*="monetag"]',
      'iframe[src*="adform"]', 'iframe[src*="adblade"]', 'iframe[src*="criteo"]', 'iframe[src*="pubmatic"]',
      'iframe[src*="rubiconproject"]', 'iframe[src*="smartadserver"]', 'iframe[src*="teads"]', 'iframe[src*="onedrop"]',
      'iframe[src*="popunder"]', 'iframe[src*="trafficgenerator"]', 'iframe[src*="vividcash"]', 'iframe[src*="zerads"]',
      'div[id^="ad-"]', 'div[id^="ads-"]', 'div[id^="ad_"]', 'div[id^="banner"]', 'div[id*="-banner"]',
      'div[id*="_banner"]', 'div[id*="ad-container"]', 'div[id*="ads-container"]',
      'div[class*="ad-container"]', 'div[class*="ad_wrapper"]', 'div[class*="ad-wrapper"]', 'div[class*="adsbox"]',
      'div[class*="advert"]', 'div[class*="adbox"]', 'div[class*="sponsor"]', 'div[class*="popunder"]',
      'div[class*="popup-ad"]', 'div[class*="sticky-banner"]', 'div[class*="bottom-banner"]',
      'div[class*="top-banner"]', 'div[class*="underplayer"]', 'div[class*="ad-overlay"]', 'div[class*="overlay-ad"]',
      '#ad-container', '#ads-container', '#adbox', '#advertisement', '#banner', '#ad-wrapper', '#popunder',
      '#overlay-container', '.ad-overlay', '.ad-wrapper', '.advertisement', '.sponsor', '.taboola', '.outbrain',
      'span[id^="ad-"]', 'section[id^="ad"]', 'aside[id^="ad"]'
    ];
    for (var i = 0; i < AD_SELECTORS.length; i++) {
      var nodes = null;
      try { nodes = document.querySelectorAll(AD_SELECTORS[i]); } catch (e) { nodes = null; }
      if (!nodes) continue;
      for (var j = 0; j < nodes.length; j++) {
        var el = nodes[j];
        if (!el || el.getAttribute('data-bb-keep') === '1') continue;
        try {
          el.setAttribute('data-bb-blocked', '1');
          el.style.display = 'none';
          el.style.visibility = 'hidden';
          if (el.parentNode) el.parentNode.removeChild(el);
        } catch (e2) {}
      }
    }

    // ---------- 2. avvio automatico del video ----------
    var vids = document.querySelectorAll('video');
    var hasPlayable = false;
    for (var v = 0; v < vids.length; v++) {
      var vid = vids[v];
      if (vid.getAttribute('data-bb-keep') !== '1') { vid.setAttribute('playsinline', 'playsinline'); }
      if (vid.src || vid.currentSrc || vid.querySelector('source')) hasPlayable = true;
    }
    if (!hasPlayable) {
      var PLAY_SELECTORS = ['.playbtn', '.vjs-big-play-button', '.vjs-big-play', '.big-play', '.play-button',
        'button.play', 'a.play', '.play-btn', '#play', '#playButton', '.ytp-large-play-button', '.video-play',
        '.plyr__control--overlaid', '.jw-preview', '[class*="playbtn"]', '[class*="play-button"]', '.player-play',
        '.emb-video .play', '.vdo-player .play'];
      for (var p = 0; p < PLAY_SELECTORS.length; p++) {
        var btns = null;
        try { btns = document.querySelectorAll(PLAY_SELECTORS[p]); } catch (e) { btns = null; }
        if (!btns) continue;
        for (var q = 0; q < btns.length; q++) {
          var b = btns[q];
          if (!b || b.getAttribute('data-bb-clicked') === '1') continue;
          var r = b.getBoundingClientRect();
          if (!r || r.width < 5 || r.height < 5) continue;
          try { b.setAttribute('data-bb-clicked', '1'); b.click(); } catch (e3) {}
          break;
        }
      }
    }

    // ---------- 3. avvia l'elemento video se è in pausa ----------
    for (var w = 0; w < vids.length; w++) {
      var v2 = vids[w];
      try {
        if (v2.paused && (v2.src || v2.currentSrc)) {
          var pr = v2.play();
          if (pr && pr.catch) { pr.catch(function () {}); }
        }
      } catch (e4) {}
    }

    // ---------- 4. blocco popunder e link pubblicitari ----------
    if (!window.__bbPopupPatched) {
      window.__bbPopupPatched = true;
      window.open = function () { return null; };
      var BAD = /(doubleclick|googlesyndication|googleadservices|adsterra|exoclick|exosrv|popads|popcash|hilltopads|juicyads|propellerads|adcash|onclickads|adskeeper|revcontent|taboola|outbrain|mgid|monetag|adform|trafficgenerator|zerads|vividcash|popblock|adtop)/i;
      document.addEventListener('click', function (e) {
        var a = null;
        try { a = e.target && e.target.closest ? e.target.closest('a[href]') : null; } catch (er) { a = null; }
        if (!a) return;
        var href = a.getAttribute('href') || '';
        if (/^(javascript:|#|mailto:|tel:)/i.test(href)) return;
        if (BAD.test(href)) {
          try { e.preventDefault(); e.stopPropagation(); } catch (e5) {}
          return;
        }
        // download / pulsanti ingannevoli verso l'esterno
        var txt = (a.textContent || '').toLowerCase();
        if (/(download|scarica|download now|click here|clicca qui|play now|guarda ora|offerta|bet|casino|bonus)/.test(txt)) {
          try {
            var u = new URL(href, location.href);
            if (u.hostname.indexOf(location.hostname) === -1) { e.preventDefault(); e.stopPropagation(); }
          } catch (e6) {}
        }
      }, true);
      window.addEventListener('beforeunload', function (e) { e.stopImmediatePropagation(); }, true);
    }

    // ---------- 5. rimuove overlay fissi che coprono il player ----------
    var fixedEls = document.querySelectorAll('div,section,aside');
    for (var f = 0; f < fixedEls.length; f++) {
      var el2 = fixedEls[f];
      if (!el2 || el2.getAttribute('data-bb-keep') === '1') continue;
      if (el2.querySelector('video')) continue;
      var cs = null;
      try { cs = window.getComputedStyle(el2); } catch (e7) { cs = null; }
      if (!cs) continue;
      if (cs.position !== 'fixed' && cs.position !== 'sticky' && cs.position !== 'absolute') continue;
      if (el2.offsetHeight > window.innerHeight * 0.75) continue;
      var content = (el2.textContent || '').trim();
      var lower = content.toLowerCase();
      if (/(pubblicit|advertis|sponsor|banner|download|scarica|play now|guarda ora|click|apri|betting|casino|bonus|offer)/.test(lower) ||
          content.length < 45) {
        try {
          el2.setAttribute('data-bb-blocked', '1');
          el2.style.display = 'none';
          el2.style.visibility = 'hidden';
          el2.style.opacity = '0';
          el2.style.pointerEvents = 'none';
        } catch (e8) {}
      }
    }
  } catch (err) {}
})();
""".trimIndent()

    val SNIFFER_JS: String = """
(function () {
  if (window.__bbSniffInstalled) return;
  window.__bbSniffInstalled = true;
  function report(u) {
    if (!u) return;
    try {
      if (window.bbReport && window.bbReport.report) window.bbReport.report(String(u));
    } catch (e) {}
  }
  try {
    var proto = window.HTMLMediaElement ? HTMLMediaElement.prototype : null;
    var d = proto ? Object.getOwnPropertyDescriptor(proto, 'src') : null;
    if (d && d.set && d.get) {
      Object.defineProperty(proto, 'src', {
        configurable: true,
        enumerable: d.enumerable,
        get: function () { return d.get.call(this); },
        set: function (v) { report(v); d.set.call(this, v); }
      });
    }
  } catch (e1) {}
  try {
    var origSet = Element.prototype.setAttribute;
    Element.prototype.setAttribute = function (n, v) {
      try {
        if ((n === 'src' || n === 'data-src' || n === 'data-video' || n === 'data-m3u8') &&
            typeof v === 'string' && /^https?:\/\//i.test(v) &&
            /\.(m3u8|mp4|webm|mkv|mov)(\?|${'$'})/i.test(v)) {
          report(v);
        }
      } catch (e2) {}
      return origSet.call(this, n, v);
    };
  } catch (e3) {}
  try {
    var obs = new MutationObserver(function (muts) {
      for (var i = 0; i < muts.length; i++) {
        var added = muts[i].addedNodes;
        for (var j = 0; j < added.length; j++) {
          var el = added[j];
          if (!el || el.nodeType !== 1) continue;
          var tag = el.tagName ? el.tagName.toUpperCase() : '';
          if (tag === 'VIDEO' || tag === 'SOURCE' || tag === 'A') {
            var s = el.getAttribute('src') || el.getAttribute('data-src');
            if (s) report(s);
          }
          if (el.querySelectorAll) {
            var vs = el.querySelectorAll('video,source');
            for (var k = 0; k < vs.length; k++) {
              var s2 = vs[k].getAttribute('src') || vs[k].getAttribute('data-src');
              if (s2) report(s2);
            }
          }
        }
      }
    });
    var start = function () {
      try { obs.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['src', 'data-src'] }); } catch (e4) {}
    };
    if (document.documentElement) start();
    else document.addEventListener('DOMContentLoaded', start);
  } catch (e5) {}
})();
""".trimIndent()

    /** Rende il video a schermo intero all'interno della WebView. */
    val FULLSCREEN_JS: String = """
(function () {
  try {
    var v = document.querySelector('video');
    if (!v) return;
    v.setAttribute('data-bb-keep', '1');
    v.setAttribute('playsinline', 'playsinline');
    v.style.position = 'fixed';
    v.style.top = '0';
    v.style.left = '0';
    v.style.width = '100%';
    v.style.height = '100%';
    v.style.margin = '0';
    v.style.zIndex = '2147483000';
    v.style.background = '#000';
    v.style.objectFit = 'contain';
  } catch (e) {}
})();
""".trimIndent()
}
