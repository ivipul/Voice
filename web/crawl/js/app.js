// The Crawl: app state, DOM, timeline, card tilt/flip, party view.
(function (C) {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var reduce = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;

  var state = { idx: 0, g: 0, lookIdx: -2, flipped: false, party: false, playing: false, energy: 0, shown: {}, lastSel: null };
  var chars = [], docs = {}, clock, bg, morph, index;
  var els = {};
  ['roster', 'depth', 'eyebrow', 'name', 'desc', 'when', 'cardName', 'cardEp', 'bLv', 'bFl', 'bFlName', 'line1Look', 'line1Time', 'hexes',
    'figure', 'hero', 'empty', 'skills', 'skillCount', 'loadout', 'calloutT', 'calloutB', 'log', 'sampleChip', 'lookSeq', 'lookText', 'lookFile',
    'track', 'segs', 'ticks', 'head', 'headLbl', 'bookLbl', 'total', 'party', 'partyWhen', 'members', 'scene3d', 'tilt', 'flip', 'hint',
    'btnParty', 'btnClose', 'btnPrev', 'btnNext', 'btnPlay', 'playIcon', 'loading', 'scene'].forEach(function (k) { els[k] = $(k); });

  function cur() { return chars[state.idx]; }
  function doc() { return docs[cur().slug]; }
  function imgFor(look, pref) {
    if (!look || !look.images) return null;
    return pref === 'action' ? (look.images.action || look.images.standing) : (look.images.standing || look.images.action);
  }
  function el(tag, cls, html) { var e = document.createElement(tag); if (cls) e.className = cls; if (html !== undefined) e.innerHTML = html; return e; }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function stamp(g) { var p = C.fromGlobal(clock, g); return 'B' + p.book + ' ' + C.fmt(p.ms); }

  // ---------- boot ----------
  C.loadJSON('data/index.json').then(function (ix) {
    index = ix; chars = ix.characters; clock = C.clock(ix.books);
    return Promise.all(chars.map(function (c) { return C.loadJSON(c.file).then(function (d) { docs[c.slug] = C.prepare(d, clock); }); }));
  }).then(start).catch(function (err) {
    els.loading.textContent = 'Could not load the data: ' + err.message;
    console.error(err);
  });

  function start() {
    try { bg = new C.Backdrop($('bg')); morph = new C.Morph(els.hero); } catch (e) { console.warn('WebGL unavailable', e); }
    buildRoster(); buildSegments(); buildHexes(); buildDepth(); buildMembers();
    els.total.textContent = (clock.total / 3600000).toFixed(1) + ' h of audiobook';
    if (index.sample) els.sampleChip.hidden = false;
    var slug = (location.hash || '').replace('#', '');
    var i = Math.max(0, chars.findIndex(function (c) { return c.slug === slug; }));
    state.idx = i;
    var looks = doc().looks;
    state.g = looks.length > 1 ? looks[1].g + 1000 : (looks.length ? looks[0].g + 1000 : 0);
    // warm the textures of every first look so switching never shows a blank
    chars.forEach(function (c) { var l = docs[c.slug].looks; if (morph && l.length) morph.preload(imgFor(l[0], 'action')); });
    select(i, null, true);
    wireCard(); wireTimeline(); wireKeys();
    els.btnParty.addEventListener('click', function () { setParty(!state.party); });
    els.btnClose.addEventListener('click', function () { setParty(false); });
    els.btnPrev.addEventListener('click', function () { stepLook(-1); });
    els.btnNext.addEventListener('click', function () { stepLook(1); });
    els.btnPlay.addEventListener('click', togglePlay);
    requestAnimationFrame(loop);
    els.loading.classList.add('done');
  }

  // ---------- roster ----------
  function buildRoster() {
    els.roster.innerHTML = '';
    chars.forEach(function (c, i) {
      var li = el('li');
      var b = el('button');
      b.type = 'button'; b.style.setProperty('--char', c.accent);
      b.setAttribute('aria-label', c.name);
      var first = docs[c.slug].looks[0];
      var src = imgFor(first, 'standing');
      b.innerHTML = '<span class="med">' + (src ? '<img alt="" src="' + esc(src) + '">' : '') + '</span><span class="lbl">' + esc(c.name) + '</span>';
      b.addEventListener('click', function () { select(i, b); });
      li.appendChild(b); els.roster.appendChild(li);
    });
  }
  function select(i, originEl, immediate) {
    if (i < 0) i = chars.length - 1; if (i >= chars.length) i = 0;
    var changed = state.idx !== i || immediate;
    state.idx = i;
    var c = cur();
    document.documentElement.style.setProperty('--accent', c.accent);
    document.documentElement.style.setProperty('--accent2', c.accent2 || c.accent);
    if (bg) {
      var o = [0.08, 0.5];
      if (originEl) { var r = originEl.getBoundingClientRect(); o = [(r.left + r.width / 2) / innerWidth, 1 - (r.top + r.height / 2) / innerHeight]; }
      if (immediate) { bg.setAccent(c.accent, o); bg.switchT = 1; } else bg.setAccent(c.accent, o);
    }
    if (morph) morph.setAccent(c.accent);
    Array.prototype.forEach.call(els.roster.querySelectorAll('button'), function (b, k) { if (k === i) b.setAttribute('aria-current', 'true'); else b.removeAttribute('aria-current'); });
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
      var s = el('div', 'seg', '<span>B' + b.n + '</span>');
      s.style.left = (clock.off[b.n] / clock.total * 100) + '%';
      s.style.width = (b.durationMs / clock.total * 100) + '%';
      s.dataset.book = b.n;
      els.segs.appendChild(s);
    });
    els.track.setAttribute('aria-valuemax', String(Math.round(clock.total / 1000)));
    layoutSegs();
    addEventListener('resize', layoutSegs);
  }
  // Hide book labels on segments too narrow to hold one (phones); the current book keeps its label.
  function layoutSegs() {
    Array.prototype.forEach.call(els.segs.children, function (s) { s.classList.toggle('tight', s.clientWidth < 34); });
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

    if (li !== state.lookIdx || charChanged) {
      state.lookIdx = li;
      var url = imgFor(look, 'action');
      if (morph) morph.show(url, charChanged && state.lastSel === null);
      els.empty.hidden = !!url;
      els.desc.textContent = look ? look.description : (looks.length ? 'Before the first recorded look.' : 'No looks bundled for this crawler yet.');
      els.eyebrow.textContent = look ? 'Crawler · look ' + pad2(li + 1) + ' / ' + pad2(looks.length) : 'Crawler · ' + looks.length + ' looks';
      els.line1Look.textContent = look ? 'Look ' + pad2(li + 1) + ' / ' + pad2(looks.length) : 'Look – / ' + pad2(looks.length);
      els.calloutT.textContent = look ? 'Look ' + pad2(li + 1) + ' · B' + look.book + ' ' + look.t : 'No look yet';
      els.calloutB.textContent = look ? look.description : '';
      els.lookSeq.textContent = look ? 'B' + look.book + ' · ' + look.t : '';
      els.lookText.textContent = look ? look.description : (looks.length ? 'Nothing recorded before ' + looks[0].t + ' of book ' + looks[0].book + '.' : 'No looks bundled yet.');
      els.lookFile.textContent = url ? url.replace(/^looks\//, '') : '';
      if (morph && look && looks[li + 1]) morph.preload(imgFor(looks[li + 1], 'action'));
      if (morph && look && li > 0) morph.preload(imgFor(looks[li - 1], 'action'));
    }
    els.when.textContent = 'Book ' + pos.book + ' · ' + C.fmt(pos.ms) + (pos.title ? ' · ' + pos.title : '');
    els.line1Time.textContent = C.fmt(pos.ms);

    // card data
    var comp = C.composeAt(d.card, g);
    var level = comp.level, floor = comp.floor;
    setBadge(els.bLv, level, g);
    setBadge(els.bFl, floor, g, els.bFlName, floor && floor.label);
    var ep = (comp['class'] && comp['class'].value) || (comp.race && comp.race.value) || 'Crawler';
    els.cardEp.textContent = ep;
    C.STATS.forEach(function (s) {
      var h = $('hx-' + s), e = comp[s], n = h.querySelector('strong'), m = h.querySelector('em');
      if (!e || e.value === null || e.value === undefined) { h.classList.add('locked'); h.classList.remove('fresh'); n.textContent = '–'; m.textContent = ''; state.shown[s] = null; return; }
      h.classList.remove('locked');
      n.textContent = e.value;
      m.textContent = delta(e, g);
      if (state.shown[s] !== e.value) { h.classList.remove('fresh'); void h.offsetWidth; if (e.g > g - C.FRESH_MS) h.classList.add('fresh'); state.shown[s] = e.value; }
    });

    // back: skills, gear
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

    // HUD log
    var rec = C.recent(d.card, g, 45 * 60000, 7);
    var lKey = rec.map(function (e) { return e.field + e.g; }).join('|') + (li);
    if (state.lKey !== lKey) {
      state.lKey = lKey; els.log.innerHTML = '';
      if (look && look.g > g - 45 * 60000) els.log.appendChild(el('li', 'fresh', '<span class="v">New look · ' + pad2(li + 1) + ' of ' + pad2(looks.length) + '</span><span class="t">' + esc(stamp(look.g)) + '</span>'));
      rec.forEach(function (e) {
        var kind = C.fieldKind(e.field), label = C.fieldLabel(e.field);
        var text = kind === 'stat' ? label + ' ' + C.valueText(e) : (kind === 'skill' ? 'Skill · ' + label + ' ' + C.valueText(e) : 'Gear · ' + label + ': ' + C.valueText(e));
        els.log.appendChild(el('li', e.g > g - C.FRESH_MS ? 'fresh' : '', '<span class="v">' + esc(text) + '</span><span class="t">' + esc(stamp(e.g)) + '</span>'));
      });
      if (!els.log.children.length) els.log.appendChild(el('li', 'none', 'Nothing revealed in the last 45 minutes of listening.'));
    }

    // timeline head + segments
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
    els.partyWhen.textContent = 'Book ' + pos.book + ' · ' + C.fmt(pos.ms) + ' · one clock for everyone';

    updateDepth(); if (state.party) updateMembers();
    state.lastSel = c.slug;
  }
  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  // ---------- depth party (desktop) ----------
  // Slots keep clear of the title (top left), the card (centre) and the HUD (top right).
  var SLOTS = [{ x: 15, b: 12, h: 30 }, { x: 31, b: 20, h: 24 }, { x: 41, b: 8, h: 19 }, { x: 70, b: 4, h: 15 }, { x: 88, b: 7, h: 13 }];
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
  var depthFigs = [];
  function buildDepth() {
    els.depth.innerHTML = ''; depthFigs = [];
    chars.forEach(function (c, i) {
      var b = el('button'); b.type = 'button'; b.setAttribute('aria-label', 'Switch to ' + c.name);
      var f = figure('fig'); b.appendChild(f);
      b.appendChild(el('span', 'pin', esc(c.name)));
      b.addEventListener('click', function () { select(i, b); });
      els.depth.appendChild(b); depthFigs.push({ btn: b, fig: f, idx: i });
    });
  }
  function updateDepth() {
    var k = 0;
    depthFigs.forEach(function (df) {
      var c = chars[df.idx];
      if (df.idx === state.idx) { df.btn.style.display = 'none'; return; }
      var s = SLOTS[k++ % SLOTS.length];
      df.btn.style.display = '';
      df.btn.style.left = s.x + '%'; df.btn.style.bottom = s.b + '%'; df.btn.style.height = s.h + '%';
      var looks = docs[c.slug].looks, li = C.lookIndexAt(looks, state.g);
      df.fig.set(imgFor(li >= 0 ? looks[li] : looks[0], 'standing'));
    });
  }

  // ---------- party view ----------
  var memberEls = [];
  function buildMembers() {
    els.members.innerHTML = ''; memberEls = [];
    chars.forEach(function (c, i) {
      var b = el('button', 'member'); b.type = 'button'; b.style.setProperty('--char', c.accent);
      b.innerHTML = '<span class="lv">–</span><span class="nm' + (c.name.length > 10 ? ' long' : '') + '">' + esc(c.name) + '</span>';
      var f = figure('fig'); b.insertBefore(f, b.querySelector('.nm'));
      b.addEventListener('click', function () { select(i, b); setParty(false); });
      els.members.appendChild(b); memberEls.push({ btn: b, fig: f, idx: i });
    });
  }
  function updateMembers() {
    memberEls.forEach(function (m) {
      var c = chars[m.idx], d = docs[c.slug], li = C.lookIndexAt(d.looks, state.g);
      m.fig.set(imgFor(li >= 0 ? d.looks[li] : d.looks[0], 'standing'));
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
        else if (down.type !== 'mouse' && Math.abs(dx) > 70 && Math.abs(dx) > 1.5 * Math.abs(dy)) select(state.idx + (dx < 0 ? 1 : -1), sc);
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
    els.hint.textContent = state.flipped ? 'Tap to flip back · skills and gear so far' : 'Tap the card to flip · drag to tilt';
  }
  function wireKeys() {
    document.addEventListener('keydown', function (e) {
      if (e.target && /INPUT|TEXTAREA|SELECT/.test(e.target.tagName)) return;
      if (e.target === els.track && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) return;
      switch (e.key) {
        case 'ArrowLeft': stepLook(-1); e.preventDefault(); break;
        case 'ArrowRight': stepLook(1); e.preventDefault(); break;
        case 'ArrowUp': select(state.idx - 1, els.roster.children[(state.idx + chars.length - 1) % chars.length].firstChild); e.preventDefault(); break;
        case 'ArrowDown': select(state.idx + 1, els.roster.children[(state.idx + 1) % chars.length].firstChild); e.preventDefault(); break;
        case ' ': if (e.target !== els.scene3d) { doFlip(); e.preventDefault(); } break;
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

    if (bg) { bg.energy = state.energy; bg.render(time, dt); }
    if (morph) { morph.energy = state.energy; morph.render(time, dt); }
    requestAnimationFrame(loop);
  }
})(window.Crawl);
