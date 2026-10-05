// The Crawl, poster variant: one name, one holo card, one clock. The page takes
// the colors and display type of whichever book the clock is in (the same
// editions as the Android app's Poster Bleed player).
(function (C) {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var reduce = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;

  // Palette and chapter type per book, copied from the app's CrawlEdition.
  var SANS = '"Chakra Petch", system-ui, sans-serif';
  var THEMES = {
    1: { bg: '#FDD463', content: '#2B1F18', hl: '#2753A4', onhl: '#FFF4D6', shadow: '#C44C1C', font: '"Instrument Serif", Georgia, serif', weight: 400, upper: false, track: '0em' },
    2: { bg: '#3F5B57', content: '#FBEBDD', hl: '#EB745D', onhl: '#1B2927', shadow: '#26302F', font: '"Big Shoulders Display", Impact, sans-serif', weight: 600, upper: true, track: '.02em' },
    3: { bg: '#FC6C44', content: '#2E2427', hl: '#F4F1C2', onhl: '#2E2427', shadow: '#B85238', font: '"Bagel Fat One", "Arial Rounded MT Bold", sans-serif', weight: 400, upper: false, track: '0em' },
    4: { bg: '#4F2567', content: '#FBEFD9', hl: '#F7BD26', onhl: '#301D3E', shadow: '#301D3E', font: 'Anton, Impact, sans-serif', weight: 400, upper: true, track: '.02em' },
    5: { bg: '#FA83A7', content: '#3A0F1E', hl: '#6A1230', onhl: '#FDE3EC', shadow: '#D85D8A', font: '"Playfair Display", Georgia, serif', weight: 800, upper: false, track: '0em' },
    6: { bg: '#354CB7', content: '#F6E6C6', hl: '#F6E6C6', onhl: '#253271', shadow: '#253271', font: '"Playfair Display", Georgia, serif', weight: 700, upper: false, track: '0em' },
    7: { bg: '#FDECDB', content: '#2E3F3D', hl: '#DE8945', onhl: '#FFF6EA', shadow: '#B8642E', font: '"Bagel Fat One", "Arial Rounded MT Bold", sans-serif', weight: 400, upper: false, track: '0em' },
    8: { bg: '#EDE4CA', content: '#17171C', hl: '#D62828', onhl: '#FFF4EE', shadow: '#C2AB98', font: 'Anton, Impact, sans-serif', weight: 400, upper: true, track: '.02em' }
  };
  function theme(n) { return THEMES[n] || THEMES[1]; }

  var state = { idx: 0, g: 0, lookIdx: -2, book: 0, flipped: false, party: false, playing: false, energy: 0, shown: {}, lastSel: null };
  var chars = [], docs = {}, clock, morph, index, rosterBtns = [];
  var els = {};
  ['roster', 'name', 'cardName', 'cardEp', 'bLv', 'bFl', 'bFlName', 'line1Look', 'line1Time', 'hexes',
    'hero', 'empty', 'skills', 'skillCount', 'loadout',
    'track', 'segs', 'ticks', 'head', 'headLbl', 'bookLbl', 'party', 'partyWhen', 'members', 'scene3d', 'flip', 'hint',
    'btnClose', 'btnPrev', 'btnNext', 'btnPlay', 'playIcon', 'loading'].forEach(function (k) { els[k] = $(k); });

  function cur() { return chars[state.idx]; }
  function doc() { return docs[cur().slug]; }
  function imgFor(look, pref) {
    if (!look || !look.images) return null;
    return pref === 'action' ? (look.images.action || look.images.standing) : (look.images.standing || look.images.action);
  }
  function el(tag, cls, html) { var e = document.createElement(tag); if (cls) e.className = cls; if (html !== undefined) e.innerHTML = html; return e; }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  // ---------- boot ----------
  C.loadJSON('data/index.json').then(function (ix) {
    index = ix; chars = ix.characters; clock = C.clock(ix.books);
    return Promise.all(chars.map(function (c) { return C.loadJSON(c.file).then(function (d) { docs[c.slug] = C.prepare(d, clock); }); }));
  }).then(start).catch(function (err) {
    els.loading.textContent = 'Could not load the data: ' + err.message;
    console.error(err);
  });

  function start() {
    try { morph = new C.Morph(els.hero); } catch (e) { console.warn('WebGL unavailable', e); }
    buildRoster(); buildSegments(); buildHexes(); buildMembers();
    var slug = (location.hash || '').replace('#', '');
    var i = Math.max(0, chars.findIndex(function (c) { return c.slug === slug; }));
    state.idx = i;
    var looks = doc().looks;
    state.g = looks.length > 1 ? looks[1].g + 1000 : (looks.length ? looks[0].g + 1000 : 0);
    // warm the textures of every first look so switching never shows a blank
    chars.forEach(function (c) { var l = docs[c.slug].looks; if (morph && l.length) morph.preload(imgFor(l[0], 'action')); });
    select(i, true);
    wireCard(); wireTimeline(); wireKeys();
    els.btnClose.addEventListener('click', function () { setParty(false); });
    els.btnPrev.addEventListener('click', function () { stepLook(-1); });
    els.btnNext.addEventListener('click', function () { stepLook(1); });
    els.btnPlay.addEventListener('click', togglePlay);
    requestAnimationFrame(loop);
    els.loading.classList.add('done');
  }

  // ---------- theme: the page follows the book the clock is in ----------
  function applyTheme(n) {
    var t = theme(n), s = document.documentElement.style;
    s.setProperty('--p-bg', t.bg); s.setProperty('--p-content', t.content); s.setProperty('--p-hl', t.hl);
    s.setProperty('--p-onhl', t.onhl); s.setProperty('--p-shadow', t.shadow);
    s.setProperty('--name-font', t.font); s.setProperty('--name-weight', t.weight);
    s.setProperty('--name-case', t.upper ? 'uppercase' : 'none'); s.setProperty('--name-track', t.track);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) { meta = document.createElement('meta'); meta.name = 'theme-color'; document.head.appendChild(meta); }
    meta.content = t.bg;
    state.book = n;
  }

  // ---------- list: the crawlers, then Party ----------
  var PARTY_ICON = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/><circle cx="8.5" cy="6" r="2"/><circle cx="15.5" cy="6" r="2"/><circle cx="12" cy="18" r="2"/></svg>';
  function buildRoster() {
    els.roster.innerHTML = ''; rosterBtns = [];
    chars.forEach(function (c, i) {
      var li = el('li'), b = el('button');
      b.type = 'button'; b.setAttribute('aria-label', c.name);
      var src = imgFor(docs[c.slug].looks[0], 'action');
      b.innerHTML = '<span class="med">' + (src ? '<img alt="" src="' + esc(src) + '">' : '') + '</span><span class="lbl">' + esc(c.name) + '</span>';
      b.addEventListener('click', function () { select(i); });
      li.appendChild(b); els.roster.appendChild(li); rosterBtns.push(b);
    });
    els.roster.appendChild(el('li', 'sep'));
    var pl = el('li'), pb = el('button');
    pb.type = 'button'; pb.id = 'btnParty'; pb.setAttribute('aria-label', 'Party view'); pb.setAttribute('aria-pressed', 'false');
    pb.innerHTML = '<span class="med">' + PARTY_ICON + '</span><span class="lbl">Party</span>';
    pb.addEventListener('click', function () { setParty(!state.party); });
    pl.appendChild(pb); els.roster.appendChild(pl);
    els.btnParty = pb;
  }
  function select(i, immediate) {
    if (i < 0) i = chars.length - 1; if (i >= chars.length) i = 0;
    var changed = state.idx !== i || immediate;
    state.idx = i;
    var c = cur();
    document.documentElement.style.setProperty('--accent', c.accent);
    document.documentElement.style.setProperty('--accent2', c.accent2 || c.accent);
    if (morph) morph.setAccent(c.accent);
    rosterBtns.forEach(function (b, k) { if (k === i) b.setAttribute('aria-current', 'true'); else b.removeAttribute('aria-current'); });
    els.name.textContent = c.name; els.cardName.textContent = c.name;
    var longName = c.name.replace(/ .*/, '').length > 6 || c.name.length > 10;
    els.name.classList.toggle('long', longName); els.cardName.classList.toggle('long', longName);
    try { history.replaceState(null, '', '#' + c.slug); } catch (e) { /* file:// */ }
    state.lookIdx = -2; state.shown = {};
    buildTicks();
    update(changed);
  }

  // ---------- timeline ----------
  function buildSegments() {
    els.segs.innerHTML = '';
    clock.books.forEach(function (b) {
      var t = theme(b.n);
      var s = el('div', 'seg', '<span>B' + b.n + '</span>');
      s.style.left = (clock.off[b.n] / clock.total * 100) + '%';
      s.style.width = (b.durationMs / clock.total * 100) + '%';
      s.style.setProperty('--sb', t.bg); s.style.setProperty('--sc', t.content);
      s.dataset.book = b.n;
      els.segs.appendChild(s);
    });
    els.track.setAttribute('aria-valuemax', String(Math.round(clock.total / 1000)));
    layoutSegs();
    addEventListener('resize', layoutSegs);
  }
  // Hide book labels on bands too narrow to hold one (phones); the current book keeps its label.
  function layoutSegs() {
    Array.prototype.forEach.call(els.segs.children, function (s) { s.classList.toggle('tight', s.clientWidth < 30); });
  }
  function buildTicks() {
    els.ticks.innerHTML = '';
    var frag = document.createDocumentFragment();
    if (state.party) {
      chars.forEach(function (c) {
        docs[c.slug].looks.forEach(function (l) {
          var t = el('i', 'tick dot'); t.style.left = (l.g / clock.total * 100) + '%'; t.style.setProperty('--tc', c.accent); frag.appendChild(t);
        });
      });
    } else {
      doc().looks.forEach(function (l) {
        var t = el('i', 'tick me'); t.style.left = (l.g / clock.total * 100) + '%'; frag.appendChild(t);
      });
    }
    els.ticks.appendChild(frag);
  }
  function wireTimeline() {
    var tr = els.track, seeking = false, lastX = 0, lastT = 0;
    function setFromX(x) {
      var r = tr.getBoundingClientRect();
      var f = Math.max(0, Math.min(1, (x - r.left) / r.width));
      setTime(f * clock.total);
    }
    tr.addEventListener('pointerdown', function (e) {
      seeking = true; stopPlay(); try { tr.setPointerCapture(e.pointerId); } catch (_) { }
      lastX = e.clientX; lastT = performance.now(); setFromX(e.clientX);
    });
    tr.addEventListener('pointermove', function (e) {
      if (!seeking) return;
      var now = performance.now(), dt = Math.max(1, now - lastT);
      var speed = Math.abs(e.clientX - lastX) / dt; // px per ms
      state.energy = Math.max(state.energy, Math.min(1, speed / 1.4));
      lastX = e.clientX; lastT = now;
      setFromX(e.clientX);
    });
    function end() {
      if (!seeking) return; seeking = false;
      // magnetise to the nearest tick of the selected crawler
      var r = tr.getBoundingClientRect(), best = null, bestD = 11;
      doc().looks.forEach(function (l) { var d = Math.abs((l.g - state.g) / clock.total * r.width); if (d < bestD) { bestD = d; best = l; } });
      if (best) setTime(best.g + 500);
    }
    tr.addEventListener('pointerup', end); tr.addEventListener('pointercancel', end);
    tr.addEventListener('keydown', function (e) {
      var step = e.shiftKey ? 3600000 : 600000;
      if (e.key === 'ArrowLeft') { setTime(state.g - step); e.preventDefault(); }
      if (e.key === 'ArrowRight') { setTime(state.g + step); e.preventDefault(); }
      if (e.key === 'Home') { setTime(0); e.preventDefault(); }
      if (e.key === 'End') { setTime(clock.total); e.preventDefault(); }
    });
  }
  function setTime(g) {
    state.g = Math.max(0, Math.min(clock.total - 1, g));
    update(false);
  }
  function stepLook(dir) {
    var looks = doc().looks; if (!looks.length) return;
    var i = C.lookIndexAt(looks, state.g);
    var target = dir > 0 ? Math.min(looks.length - 1, i + 1) : Math.max(0, (looks[i] && state.g - looks[i].g > 1500) ? i : i - 1);
    state.energy = Math.max(state.energy, 0.35);
    setTime(looks[target].g + 500);
  }
  function togglePlay() { if (state.playing) stopPlay(); else { state.playing = true; els.btnPlay.setAttribute('aria-pressed', 'true'); els.playIcon.setAttribute('d', 'M6 5h4v14H6zM14 5h4v14h-4z'); } }
  function stopPlay() { if (!state.playing) return; state.playing = false; els.btnPlay.setAttribute('aria-pressed', 'false'); els.playIcon.setAttribute('d', 'M7 4l12 8-12 8z'); }

  // ---------- card ----------
  function buildHexes() {
    els.hexes.innerHTML = '';
    C.STATS.forEach(function (s) {
      var h = el('div', 'hex locked'); h.id = 'hx-' + s;
      h.innerHTML = '<div><span>' + s.toUpperCase() + '</span><strong>–</strong><em></em></div>';
      els.hexes.appendChild(h);
    });
  }
  // "▲2" when a numeric stat rose within the fresh window, "▼1" when it fell.
  function delta(e, g) {
    if (!e || !e.prev || e.g <= g - C.FRESH_MS) return '';
    var d = (+e.value) - (+e.prev.value);
    if (isNaN(d) || d === 0) return '';
    return (d > 0 ? '▲' : '▼') + Math.abs(d);
  }
  function setBadge(badge, entry, g, nameEl, nameText) {
    var b = badge.querySelector('b');
    if (!entry) { badge.classList.add('locked'); badge.classList.remove('fresh'); b.textContent = '–'; if (nameEl) nameEl.textContent = ''; return; }
    badge.classList.remove('locked');
    var key = badge.id, val = String(entry.value);
    if (state.shown[key] !== val) { badge.classList.remove('fresh'); void badge.offsetWidth; if (entry.g > g - C.FRESH_MS) badge.classList.add('fresh'); state.shown[key] = val; }
    b.textContent = val;
    if (nameEl) nameEl.textContent = nameText || '';
  }

  function update(charChanged) {
    var c = cur(), d = doc(), g = state.g, looks = d.looks;
    var li = C.lookIndexAt(looks, g);
    var look = li >= 0 ? looks[li] : null;
    var pos = C.fromGlobal(clock, g);
    if (pos.book !== state.book) applyTheme(pos.book);

    if (li !== state.lookIdx || charChanged) {
      state.lookIdx = li;
      var url = imgFor(look, 'action');
      if (morph) morph.show(url, charChanged && state.lastSel === null);
      els.empty.hidden = !!url;
      els.line1Look.textContent = look ? 'Look ' + pad2(li + 1) + ' / ' + pad2(looks.length) : 'Look – / ' + pad2(looks.length);
      if (morph && look && looks[li + 1]) morph.preload(imgFor(looks[li + 1], 'action'));
      if (morph && look && li > 0) morph.preload(imgFor(looks[li - 1], 'action'));
    }
    els.line1Time.textContent = C.fmt(pos.ms);

    // front of the card
    var comp = C.composeAt(d.card, g);
    setBadge(els.bLv, comp.level, g);
    setBadge(els.bFl, comp.floor, g, els.bFlName, comp.floor && comp.floor.label);
    els.cardEp.textContent = (comp['class'] && comp['class'].value) || (comp.race && comp.race.value) || 'Crawler';
    C.STATS.forEach(function (s) {
      var h = $('hx-' + s), e = comp[s], n = h.querySelector('strong'), m = h.querySelector('em');
      if (!e || e.value === null || e.value === undefined) { h.classList.add('locked'); h.classList.remove('fresh'); n.textContent = '–'; m.textContent = ''; state.shown[s] = null; return; }
      h.classList.remove('locked');
      n.textContent = e.value;
      m.textContent = delta(e, g);
      if (state.shown[s] !== e.value) { h.classList.remove('fresh'); void h.offsetWidth; if (e.g > g - C.FRESH_MS) h.classList.add('fresh'); state.shown[s] = e.value; }
    });

    // back of the card: skills, gear
    var skills = [], gear = {};
    Object.keys(comp).forEach(function (f) {
      var e = comp[f];
      if (f.indexOf('skill:') === 0 && e.value !== null) skills.push(e);
      if (f.indexOf('gear:') === 0) gear[f.slice(5)] = e;
    });
    skills.sort(function (a, b) { return b.g - a.g; });
    var sKey = skills.map(function (e) { return e.field + '=' + e.value; }).join('|');
    if (state.sKey !== sKey) {
      state.sKey = sKey; els.skills.innerHTML = '';
      els.skillCount.textContent = skills.length ? skills.length + ' revealed' : '';
      if (!skills.length) els.skills.appendChild(el('div', 'sk none', 'No skills revealed yet.'));
      skills.slice(0, 5).forEach(function (e) {
        var name = C.fieldLabel(e.field);
        var row = el('div', 'sk' + (e.g > g - C.FRESH_MS ? ' fresh' : ''));
        row.innerHTML = '<div class="ic">' + esc(name.charAt(0).toUpperCase()) + '</div><div><b>' + esc(name) + '</b><small>Rank ' + esc(e.value) + ' · B' + e.book + ' ' + C.fmt(e.ms) + '</small></div>';
        els.skills.appendChild(row);
      });
      if (skills.length > 5) els.skills.appendChild(el('div', 'sk none', '+' + (skills.length - 5) + ' more'));
    }
    var slots = C.GEAR_ORDER.filter(function (s) { return gear[s]; }).concat(Object.keys(gear).filter(function (s) { return C.GEAR_ORDER.indexOf(s) < 0; })).slice(0, 5);
    var gKey = slots.map(function (s) { return s + '=' + gear[s].value; }).join('|');
    if (state.gKey !== gKey) {
      state.gKey = gKey; els.loadout.innerHTML = '';
      if (!slots.length) els.loadout.appendChild(el('div', 'sk none', 'No gear revealed yet.'));
      slots.forEach(function (s) {
        var e = gear[s], on = e.value !== null && e.value !== undefined && e.value !== '';
        els.loadout.appendChild(el('div', 'slot' + (on ? ' on' : ''), '<em>' + esc(s) + '</em>' + (on ? esc(e.value) : 'none')));
      });
    }

    // timeline head + bands
    var f = g / clock.total;
    els.head.style.left = (f * 100) + '%';
    els.headLbl.textContent = C.fmt(pos.ms);
    var tw = els.track.clientWidth || 1;
    els.head.classList.toggle('edge-l', f * tw < 36);
    els.head.classList.toggle('edge-r', (1 - f) * tw < 36);
    els.bookLbl.textContent = 'Book ' + pos.book;
    Array.prototype.forEach.call(els.segs.children, function (s) { s.classList.toggle('on', +s.dataset.book === pos.book); });
    els.track.setAttribute('aria-valuenow', String(Math.round(g / 1000)));
    els.track.setAttribute('aria-valuetext', 'Book ' + pos.book + ', ' + C.fmt(pos.ms));
    els.partyWhen.textContent = 'Book ' + pos.book + ' · ' + C.fmt(pos.ms) + (pos.title ? ' · ' + pos.title : '');

    if (state.party) updateMembers();
    state.lastSel = c.slug;
  }

  // ---------- party ----------
  function figure(cls) {
    var f = el('div', cls || 'fig');
    f.innerHTML = '<img class="in" alt=""><img class="out" alt="">';
    f._src = null;
    f.set = function (src) {
      if (src === f._src) return;
      var a = f.querySelector('img.in'), b = f.querySelector('img.out');
      if (f._src) { b.src = f._src; b.classList.remove('out'); a.classList.add('out'); a.src = src || ''; setTimeout(function () { a.classList.remove('out'); b.classList.add('out'); }, 30); }
      else a.src = src || '';
      f._src = src;
    };
    return f;
  }
  var memberEls = [];
  function buildMembers() {
    els.members.innerHTML = ''; memberEls = [];
    chars.forEach(function (c, i) {
      var b = el('button', 'member'); b.type = 'button';
      b.innerHTML = '<span class="lv">–</span><span class="nm' + (c.name.length > 10 ? ' long' : '') + '">' + esc(c.name) + '</span>';
      var f = figure('fig'); b.insertBefore(f, b.querySelector('.nm'));
      b.addEventListener('click', function () { select(i); setParty(false); });
      els.members.appendChild(b); memberEls.push({ btn: b, fig: f, idx: i });
    });
  }
  function updateMembers() {
    memberEls.forEach(function (m) {
      var c = chars[m.idx], d = docs[c.slug], li = C.lookIndexAt(d.looks, state.g);
      m.fig.set(imgFor(li >= 0 ? d.looks[li] : d.looks[0], 'action'));
      var comp = C.composeAt(d.card, state.g);
      m.btn.querySelector('.lv').textContent = comp.level ? 'LVL ' + comp.level.value : (d.card.length ? 'LVL –' : 'NO STATS');
      if (m.idx === state.idx) m.btn.setAttribute('aria-current', 'true'); else m.btn.removeAttribute('aria-current');
    });
  }
  function setParty(on) {
    state.party = on;
    els.party.classList.toggle('open', on);
    els.party.setAttribute('aria-hidden', on ? 'false' : 'true');
    els.btnParty.setAttribute('aria-pressed', on ? 'true' : 'false');
    if (morph) morph.fade = on ? 0 : 1;
    buildTicks(); if (on) updateMembers();
  }

  // ---------- tilt, flip, swipe ----------
  var tcur = { rx: 0, ry: 0, mx: 50, my: 50 }, ttgt = { rx: 0, ry: 0, mx: 50, my: 50 }, tactive = false, idleT0 = performance.now();
  function wireCard() {
    var sc = els.scene3d, down = null, moved = false;
    function setTarget(x, y) {
      var r = sc.getBoundingClientRect();
      var px = Math.max(0, Math.min(1, (x - r.left) / r.width)), py = Math.max(0, Math.min(1, (y - r.top) / r.height));
      ttgt.ry = (px - .5) * 26; ttgt.rx = (.5 - py) * 20; ttgt.mx = px * 100; ttgt.my = py * 100;
    }
    sc.addEventListener('pointerdown', function (e) {
      tactive = true; down = { x: e.clientX, y: e.clientY, t: performance.now(), type: e.pointerType }; moved = false;
      try { sc.setPointerCapture(e.pointerId); } catch (_) { }
      setTarget(e.clientX, e.clientY);
    });
    sc.addEventListener('pointermove', function (e) {
      if (e.pointerType === 'mouse' || tactive) setTarget(e.clientX, e.clientY);
      if (down && Math.hypot(e.clientX - down.x, e.clientY - down.y) > 8) moved = true;
    });
    function end(e) {
      if (down) {
        var dx = e.clientX - down.x, dy = e.clientY - down.y;
        if (!moved && performance.now() - down.t < 500 && e.type === 'pointerup') doFlip();
        else if (down.type !== 'mouse' && Math.abs(dx) > 70 && Math.abs(dx) > 1.5 * Math.abs(dy)) select(state.idx + (dx < 0 ? 1 : -1));
      }
      tactive = false; down = null;
      if (e.pointerType !== 'mouse') resetTilt();
    }
    sc.addEventListener('pointerup', end); sc.addEventListener('pointercancel', end);
    sc.addEventListener('pointerleave', function (e) { if (e.pointerType === 'mouse' && !tactive) resetTilt(); });
    sc.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); doFlip(); } });
  }
  function resetTilt() { ttgt.rx = 0; ttgt.ry = 0; ttgt.mx = 50; ttgt.my = 50; idleT0 = performance.now(); }
  function doFlip() {
    state.flipped = !state.flipped;
    els.flip.classList.toggle('back', state.flipped);
    els.hint.textContent = state.flipped ? 'Tap to flip back' : 'Tap to flip';
  }

  function wireKeys() {
    document.addEventListener('keydown', function (e) {
      if (e.target && /INPUT|TEXTAREA|SELECT/.test(e.target.tagName)) return;
      if (e.target === els.track && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) return;
      switch (e.key) {
        case 'ArrowLeft': stepLook(-1); e.preventDefault(); break;
        case 'ArrowRight': stepLook(1); e.preventDefault(); break;
        case 'ArrowUp': select(state.idx - 1); e.preventDefault(); break;
        case 'ArrowDown': select(state.idx + 1); e.preventDefault(); break;
        case ' ': if (e.target !== els.scene3d && !(e.target && e.target.tagName === 'BUTTON')) { doFlip(); e.preventDefault(); } break;
        case 'p': case 'P': setParty(!state.party); break;
        case 'Escape': if (state.party) setParty(false); break;
      }
    });
  }

  // ---------- frame loop ----------
  var last = performance.now(), t0 = last;
  function loop(now) {
    var dt = Math.min(0.1, (now - last) / 1000); last = now;
    var time = (now - t0) / 1000;
    state.energy = Math.max(0, state.energy - dt * 1.6);
    if (state.playing) { setTime(state.g + dt * 600000); if (state.g >= clock.total - 2) stopPlay(); }

    // tilt: follow the pointer, otherwise a slow idle sway
    var idle = !tactive && ttgt.mx === 50 && ttgt.my === 50;
    if (idle && !reduce) {
      var s = (now - idleT0) / 1000;
      var k = Math.min(1, s / 2);
      tcur.rx += ((Math.cos(s * .7) * 5 * k) - tcur.rx) * .06; tcur.ry += ((Math.sin(s * .9) * 9 * k) - tcur.ry) * .06;
      tcur.mx += ((50 + Math.sin(s * .9) * 32 * k) - tcur.mx) * .06; tcur.my += ((50 + Math.cos(s * .7) * 22 * k) - tcur.my) * .06;
    } else {
      tcur.rx += (ttgt.rx - tcur.rx) * .14; tcur.ry += (ttgt.ry - tcur.ry) * .14;
      tcur.mx += (ttgt.mx - tcur.mx) * .16; tcur.my += (ttgt.my - tcur.my) * .16;
    }
    var st = els.scene3d.style;
    st.setProperty('--rx', tcur.rx.toFixed(2) + 'deg'); st.setProperty('--ry', tcur.ry.toFixed(2) + 'deg');
    st.setProperty('--rx-n', (tcur.rx / 20).toFixed(3)); st.setProperty('--ry-n', (tcur.ry / 26).toFixed(3));
    st.setProperty('--mx', tcur.mx.toFixed(1)); st.setProperty('--my', tcur.my.toFixed(1));
    st.setProperty('--abs', Math.min(1, Math.hypot(tcur.rx / 20, tcur.ry / 26)).toFixed(3));

    if (morph) { morph.energy = state.energy; morph.render(time, dt); }
    requestAnimationFrame(loop);
  }
})(window.Crawl);
