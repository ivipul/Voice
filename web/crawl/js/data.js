// Data loading and time composition for the Crawl explorer.
// Every timestamp in the data files is book-relative; `g` is the global
// position on the one shared clock (books laid end to end).
window.Crawl = window.Crawl || {};
(function (C) {
  'use strict';
  var pad = function (n) { return (n < 10 ? '0' : '') + n; };

  C.fmt = function (ms) {
    var s = Math.max(0, Math.floor(ms / 1000));
    return pad(Math.floor(s / 3600)) + ':' + pad(Math.floor(s % 3600 / 60)) + ':' + pad(s % 60);
  };

  C.loadJSON = function (url) {
    return fetch(url, { cache: 'no-cache' }).then(function (r) {
      if (!r.ok) throw new Error(url + ' -> ' + r.status);
      return r.json();
    });
  };

  // Offsets of each book on the global clock, plus total length.
  C.clock = function (books) {
    var off = {}, acc = 0;
    books.forEach(function (b) { off[b.n] = acc; acc += b.durationMs; });
    return { off: off, total: acc, books: books };
  };
  C.toGlobal = function (clock, book, ms) { return (clock.off[book] || 0) + ms; };
  C.fromGlobal = function (clock, g) {
    var books = clock.books;
    for (var i = books.length - 1; i >= 0; i--) {
      var b = books[i];
      if (g >= clock.off[b.n]) return { book: b.n, ms: Math.min(g - clock.off[b.n], b.durationMs), title: b.title };
    }
    return { book: books[0].n, ms: 0, title: books[0].title };
  };

  // Adds `g` to looks and card entries and sorts them.
  C.prepare = function (doc, clock) {
    doc.looks = (doc.looks || []).map(function (l) { l.g = C.toGlobal(clock, l.book, l.ms); return l; })
      .sort(function (a, b) { return a.g - b.g; });
    doc.card = (doc.card || []).map(function (e) { e.g = C.toGlobal(clock, e.book, e.ms); return e; })
      .sort(function (a, b) { return a.g - b.g; });
    return doc;
  };

  // Index of the look current at g, or -1 before the first look.
  C.lookIndexAt = function (looks, g) {
    var i = -1;
    for (var k = 0; k < looks.length; k++) { if (looks[k].g <= g) i = k; else break; }
    return i;
  };

  // Latest entry per field at or before g; each carries `prev`, the entry it replaced.
  C.composeAt = function (entries, g) {
    var m = {};
    for (var i = 0; i < entries.length; i++) {
      var e = entries[i];
      if (e.g > g) break;
      e.prev = m[e.field] || null;
      m[e.field] = e;
    }
    return m;
  };

  // Entries revealed inside the window ending at g, newest first.
  C.recent = function (entries, g, windowMs, limit) {
    var out = [];
    for (var i = entries.length - 1; i >= 0; i--) {
      var e = entries[i];
      if (e.g > g) continue;
      if (e.g <= g - windowMs) break;
      out.push(e);
      if (out.length >= limit) break;
    }
    return out;
  };

  C.FRESH_MS = 30 * 60 * 1000;
  C.STATS = ['str', 'dex', 'con', 'int', 'cha'];
  C.GEAR_ORDER = ['head', 'body', 'hands', 'legs', 'feet', 'hand', 'ring', 'neck', 'back', 'weapon'];

  C.fieldLabel = function (field) {
    if (field.indexOf('skill:') === 0) return field.slice(6);
    if (field.indexOf('gear:') === 0) return field.slice(5);
    return { level: 'Level', floor: 'Floor', race: 'Race', 'class': 'Class', str: 'STR', dex: 'DEX', con: 'CON', 'int': 'INT', cha: 'CHA' }[field] || field;
  };
  C.fieldKind = function (field) {
    if (field.indexOf('skill:') === 0) return 'skill';
    if (field.indexOf('gear:') === 0) return 'gear';
    return 'stat';
  };
  C.valueText = function (e) {
    if (e.value === null || e.value === undefined || e.value === '') return 'none';
    if (C.fieldKind(e.field) === 'skill') return 'rank ' + e.value;
    return String(e.value);
  };
})(window.Crawl);
