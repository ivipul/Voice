// WebGL layers for the Crawl explorer: a full-screen dungeon backdrop (fog,
// embers, stone grain, vignette) and the look morph (ink-bleed displacement
// dissolve between two transparent cutouts).
window.Crawl = window.Crawl || {};
(function (C) {
  'use strict';

  var NOISE = [
    'float hash21(vec2 p){ p=fract(p*vec2(123.34,456.21)); p+=dot(p,p+45.32); return fract(p.x*p.y); }',
    'float vnoise(vec2 p){ vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);',
    '  float a=hash21(i), b=hash21(i+vec2(1.0,0.0)), c=hash21(i+vec2(0.0,1.0)), d=hash21(i+vec2(1.0,1.0));',
    '  return mix(mix(a,b,f.x),mix(c,d,f.x),f.y); }',
    'float fbm(vec2 p){ float v=0.0, a=0.5; for(int i=0;i<5;i++){ v+=a*vnoise(p); p=p*2.03+vec2(1.7,9.2); a*=0.5; } return v; }'
  ].join('\n');

  var VERT = [
    'attribute vec2 a_pos; varying vec2 v_uv;',
    'void main(){ v_uv = a_pos*0.5+0.5; gl_Position = vec4(a_pos,0.0,1.0); }'
  ].join('\n');

  var BG_FRAG = [
    'precision highp float; varying vec2 v_uv;',
    'uniform vec2 u_res; uniform float u_time; uniform vec3 u_accent; uniform vec3 u_prev;',
    'uniform float u_switch; uniform vec2 u_origin; uniform float u_energy;',
    NOISE,
    'float embers(vec2 p, float t, float scale, float speed, float seed){',
    '  vec2 q=p*scale; q.y-=t*speed*scale; vec2 cell=floor(q), fr=fract(q);',
    '  float h=hash21(cell+seed); float on=step(0.64,h);',
    '  vec2 pos=vec2(0.2+0.6*hash21(cell+seed+1.7), 0.2+0.6*hash21(cell+seed+3.1));',
    '  pos.x+=0.12*sin(t*(0.7+h)+h*6.28);',
    '  float d=length(fr-pos); float r=0.02+0.03*h;',
    '  float glow=smoothstep(r*3.2,0.0,d)*0.35+smoothstep(r,0.0,d);',
    '  float tw=0.55+0.45*sin(t*(2.0+h*4.0)+h*20.0);',
    '  return glow*tw*on; }',
    'void main(){',
    '  vec2 uv=v_uv; float ar=u_res.x/u_res.y; vec2 p=vec2(uv.x*ar, uv.y);',
    '  vec2 o=vec2(u_origin.x*ar, u_origin.y);',
    '  float rad=u_switch*(ar+1.3); float w=smoothstep(rad-0.3, rad, distance(p,o));',
    '  vec3 acc=mix(u_accent,u_prev,w);',
    '  float f=fbm(p*1.6+vec2(0.0,-u_time*0.04));',
    '  float floorGlow=pow(clamp(1.0-uv.y*1.15,0.0,1.0),2.0)*(0.45+0.55*f);',
    '  float ceil=pow(clamp(uv.y-0.55,0.0,1.0),2.0)*0.3*f;',
    '  vec3 col=vec3(0.039,0.039,0.051);',
    '  col+=acc*floorGlow*0.46 + acc*ceil*0.3;',
    '  float g=fbm(p*18.0); col*=0.86+0.28*g;',
    '  float em=0.0;',
    '  em+=embers(p,u_time,7.0,0.05+0.12*u_energy,1.0);',
    '  em+=embers(p,u_time*1.3,12.0,0.08+0.18*u_energy,7.0)*0.6;',
    '  em+=embers(p,u_time*0.8,4.5,0.03+0.09*u_energy,13.0)*1.2;',
    '  col+=mix(acc,vec3(1.0,0.92,0.82),0.35)*em*(0.9+0.6*u_energy);',
    '  float vig=1.0-0.55*pow(length((uv-0.5)*vec2(1.0,1.2)),2.2); col*=vig;',
    '  col*=0.965+0.035*sin(gl_FragCoord.y*1.5707963);',
    '  col+=(hash21(gl_FragCoord.xy+fract(u_time)*vec2(13.0,71.0))-0.5)*0.045;',
    '  gl_FragColor=vec4(col,1.0); }'
  ].join('\n');

  var MORPH_FRAG = [
    'precision highp float; varying vec2 v_uv;',
    'uniform sampler2D u_a; uniform sampler2D u_b; uniform vec4 u_fitA; uniform vec4 u_fitB;',
    'uniform float u_t; uniform float u_energy; uniform float u_time; uniform float u_fade; uniform vec3 u_accent;',
    NOISE,
    'vec4 samp(sampler2D s, vec2 uv){ vec4 c=texture2D(s,uv);',
    '  float inside=step(0.0,uv.x)*step(uv.x,1.0)*step(0.0,uv.y)*step(uv.y,1.0); return c*inside; }',
    'void main(){',
    '  float t=u_t; float env=sin(3.14159*t);',
    '  float str=(0.05+0.26*u_energy)*env;',
    '  vec2 n=vec2(fbm(v_uv*3.0+u_time*0.2), fbm(v_uv*3.0+vec2(17.3,5.1)-u_time*0.17))-0.5;',
    '  vec2 lift=vec2(0.0, 0.07*env);',
    '  vec2 uvA=v_uv*u_fitA.xy+u_fitA.zw + n*str*t*1.4 + lift*t;',
    '  vec2 uvB=v_uv*u_fitB.xy+u_fitB.zw - n*str*(1.0-t)*1.4;',
    '  vec4 a=samp(u_a,uvA), b=samp(u_b,uvB);',
    '  float th=fbm(v_uv*5.0+vec2(3.1,8.7));',
    '  float w=0.16+0.22*u_energy;',
    '  float k=1.0-smoothstep(t-w,t+w,th);',
    '  vec4 c=mix(a,b,k);',
    '  float front=clamp(1.0-abs(th-t)/w,0.0,1.0)*env;',
    '  float cover=max(a.a,b.a); float edge=front*cover;',
    '  c.rgb=mix(c.rgb,u_accent*cover,edge*0.8);',
    '  c.rgb+=u_accent*edge*0.35*cover;',
    '  c.a=max(c.a, edge*0.6*cover);',
    '  gl_FragColor=c*u_fade; }'
  ].join('\n');

  function compile(gl, type, src) {
    var sh = gl.createShader(type);
    gl.shaderSource(sh, src); gl.compileShader(sh);
    if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) throw new Error('shader: ' + gl.getShaderInfoLog(sh));
    return sh;
  }
  function program(gl, frag) {
    var p = gl.createProgram();
    gl.attachShader(p, compile(gl, gl.VERTEX_SHADER, VERT));
    gl.attachShader(p, compile(gl, gl.FRAGMENT_SHADER, frag));
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error('link: ' + gl.getProgramInfoLog(p));
    var buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    var loc = gl.getAttribLocation(p, 'a_pos');
    gl.enableVertexAttribArray(loc);
    gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
    var u = {};
    var n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
    for (var i = 0; i < n; i++) { var info = gl.getActiveUniform(p, i); u[info.name] = gl.getUniformLocation(p, info.name); }
    return { p: p, u: u };
  }
  function hex(c) {
    var m = /^#?([0-9a-f]{6})$/i.exec(c.trim());
    if (!m) return [1, 0.42, 0.12];
    var v = parseInt(m[1], 16);
    return [((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255];
  }
  function fitCanvas(canvas, maxDpr) {
    var dpr = Math.min(window.devicePixelRatio || 1, maxDpr || 2);
    var w = Math.max(1, Math.round(canvas.clientWidth * dpr)), h = Math.max(1, Math.round(canvas.clientHeight * dpr));
    if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; return true; }
    return false;
  }

  // ---- backdrop ----
  C.Backdrop = function (canvas) {
    var gl = canvas.getContext('webgl', { antialias: false, alpha: false, premultipliedAlpha: false });
    if (!gl) throw new Error('webgl');
    this.gl = gl; this.canvas = canvas;
    this.prog = program(gl, BG_FRAG);
    this.accent = hex('#FF6A1F'); this.prev = this.accent.slice();
    this.switchT = 1; this.origin = [0.1, 0.5]; this.energy = 0;
  };
  C.Backdrop.prototype.setAccent = function (c, originUv) {
    this.prev = this.accent.slice(); this.accent = hex(c); this.switchT = 0;
    if (originUv) this.origin = originUv;
  };
  C.Backdrop.prototype.render = function (time, dt) {
    var gl = this.gl, u = this.prog.u;
    fitCanvas(this.canvas, 1.5);
    this.switchT = Math.min(1, this.switchT + dt / 0.75);
    gl.viewport(0, 0, this.canvas.width, this.canvas.height);
    gl.useProgram(this.prog.p);
    gl.uniform2f(u.u_res, this.canvas.width, this.canvas.height);
    gl.uniform1f(u.u_time, time);
    gl.uniform3fv(u.u_accent, this.accent);
    gl.uniform3fv(u.u_prev, this.prev);
    gl.uniform1f(u.u_switch, this.switchT);
    gl.uniform2fv(u.u_origin, this.origin);
    gl.uniform1f(u.u_energy, this.energy);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  };

  // ---- textures ----
  var texCache = {};
  function blankTexture(gl) {
    var t = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, t);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array([0, 0, 0, 0]));
    return { tex: t, aspect: 0.6, ready: true, blank: true };
  }
  function loadTexture(gl, url) {
    var key = url;
    if (texCache[key]) return texCache[key];
    var entry = blankTexture(gl); entry.ready = false; entry.blank = false; entry.url = url;
    texCache[key] = entry;
    var img = new Image();
    img.onload = function () {
      gl.bindTexture(gl.TEXTURE_2D, entry.tex);
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
      gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, true);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      entry.aspect = img.naturalWidth / img.naturalHeight; entry.ready = true;
      if (entry.onready) entry.onready();
    };
    img.onerror = function () { entry.failed = true; entry.ready = true; entry.blank = true; };
    img.src = url;
    return entry;
  }
  // uv -> texture uv for a bottom-anchored "contain" fit.
  function fit(canvasAspect, texAspect) {
    if (texAspect <= canvasAspect) { var sx = canvasAspect / texAspect; return [sx, 1, 0.5 - 0.5 * sx, 0]; }
    return [1, texAspect / canvasAspect, 0, 0];
  }

  // ---- morph ----
  C.Morph = function (canvas) {
    var gl = canvas.getContext('webgl', { antialias: false, alpha: true, premultipliedAlpha: true });
    if (!gl) throw new Error('webgl');
    this.gl = gl; this.canvas = canvas;
    this.prog = program(gl, MORPH_FRAG);
    gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    this.blank = blankTexture(gl);
    this.a = this.blank; this.b = this.blank;
    this.t = 1; this.dur = 0.65; this.energy = 0; this.fade = 1;
    this.accent = hex('#FF6A1F');
  };
  C.Morph.prototype.preload = function (url) { if (url) loadTexture(this.gl, url); };
  // Start a morph from whatever is showing to `url`.
  C.Morph.prototype.show = function (url, immediate) {
    var next = url ? loadTexture(this.gl, url) : this.blank;
    if (next === this.b && this.t >= 1) return;
    this.a = this.t >= 0.5 ? this.b : this.a;
    this.b = next;
    this.t = immediate ? 1 : 0;
    this.dur = this.energy > 0.5 ? 0.34 : 0.65;
  };
  C.Morph.prototype.setAccent = function (c) { this.accent = hex(c); };
  C.Morph.prototype.render = function (time, dt) {
    var gl = this.gl, u = this.prog.u;
    fitCanvas(this.canvas, 2);
    if (this.t < 1) this.t = Math.min(1, this.t + dt / this.dur);
    var ca = this.canvas.width / this.canvas.height;
    gl.viewport(0, 0, this.canvas.width, this.canvas.height);
    gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
    gl.useProgram(this.prog.p);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, this.a.tex); gl.uniform1i(u.u_a, 0);
    gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, this.b.tex); gl.uniform1i(u.u_b, 1);
    gl.uniform4fv(u.u_fitA, fit(ca, this.a.aspect || 0.6));
    gl.uniform4fv(u.u_fitB, fit(ca, this.b.aspect || 0.6));
    gl.uniform1f(u.u_t, this.t);
    gl.uniform1f(u.u_energy, this.energy);
    gl.uniform1f(u.u_time, time);
    gl.uniform1f(u.u_fade, this.fade);
    gl.uniform3fv(u.u_accent, this.accent);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  };
  C.Morph.prototype.showingBlank = function () { return this.b === this.blank || this.b.failed; };
})(window.Crawl);
