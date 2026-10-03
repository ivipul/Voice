// Renders flat placeholder silhouettes (transparent PNG) so the explorer can be
// developed and demoed before the real look cutouts are bundled from the Mac.
// Usage: node tools/render_placeholders.mjs   (from web/crawl)
import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const INK = '#ECE9E2';
const W = 600, H = 1000;

const human = {
  stand: `<circle cx="300" cy="150" r="78"/>
    <path d="M202 258h196a54 54 0 0 1 54 54v246H148V312a54 54 0 0 1 54-54z"/>
    <rect x="120" y="266" width="74" height="310" rx="37" transform="rotate(7 157 421)"/>
    <rect x="406" y="266" width="74" height="310" rx="37" transform="rotate(-7 443 421)"/>
    <rect x="208" y="540" width="84" height="400" rx="42"/>
    <rect x="308" y="540" width="84" height="400" rx="42"/>`,
  action: `<circle cx="360" cy="160" r="78"/>
    <path d="M262 266h196a54 54 0 0 1 54 54v240H208V320a54 54 0 0 1 54-54z" transform="rotate(-10 360 420)"/>
    <rect x="432" y="90" width="74" height="310" rx="37" transform="rotate(-62 469 245)"/>
    <rect x="180" y="300" width="74" height="310" rx="37" transform="rotate(28 217 455)"/>
    <rect x="190" y="540" width="84" height="400" rx="42" transform="rotate(26 232 580)"/>
    <rect x="380" y="536" width="84" height="400" rx="42" transform="rotate(-16 422 576)"/>`,
  armsUp: `<circle cx="300" cy="170" r="78"/>
    <path d="M202 278h196a54 54 0 0 1 54 54v236H148V332a54 54 0 0 1 54-54z"/>
    <rect x="128" y="40" width="74" height="300" rx="37" transform="rotate(22 165 190)"/>
    <rect x="398" y="40" width="74" height="300" rx="37" transform="rotate(-22 435 190)"/>
    <rect x="208" y="550" width="84" height="390" rx="42"/>
    <rect x="308" y="550" width="84" height="390" rx="42"/>`,
  crouch: `<circle cx="310" cy="300" r="78"/>
    <path d="M212 408h196a54 54 0 0 1 54 54v190H158V462a54 54 0 0 1 54-54z" transform="rotate(-8 310 530)"/>
    <rect x="130" y="420" width="74" height="260" rx="37" transform="rotate(30 167 550)"/>
    <rect x="430" y="400" width="74" height="260" rx="37" transform="rotate(-40 467 530)"/>
    <rect x="150" y="700" width="260" height="84" rx="42" transform="rotate(-18 280 742)"/>
    <rect x="330" y="690" width="84" height="250" rx="42" transform="rotate(10 372 815)"/>`,
  walk: `<circle cx="300" cy="150" r="78"/>
    <path d="M202 258h196a54 54 0 0 1 54 54v246H148V312a54 54 0 0 1 54-54z" transform="rotate(4 300 420)"/>
    <rect x="130" y="270" width="74" height="300" rx="37" transform="rotate(-24 167 420)"/>
    <rect x="400" y="270" width="74" height="300" rx="37" transform="rotate(26 437 420)"/>
    <rect x="200" y="540" width="84" height="400" rx="42" transform="rotate(16 242 580)"/>
    <rect x="316" y="540" width="84" height="400" rx="42" transform="rotate(-14 358 580)"/>`,
};
const cat = {
  sit: `<ellipse cx="300" cy="700" rx="200" ry="170"/>
    <circle cx="300" cy="440" r="130"/>
    <path d="M196 356L156 210l118 110z"/><path d="M404 356l40-146-118 110z"/>
    <path d="M480 740c110-20 140 90 60 150" fill="none" stroke="${INK}" stroke-width="52" stroke-linecap="round"/>`,
  pounce: `<ellipse cx="320" cy="560" rx="230" ry="130" transform="rotate(-24 320 560)"/>
    <circle cx="470" cy="330" r="120"/>
    <path d="M376 250L350 100l110 110z"/><path d="M560 250l48-150-124 110z"/>
    <rect x="420" y="430" width="70" height="300" rx="35" transform="rotate(-30 455 580)"/>
    <rect x="510" y="410" width="70" height="280" rx="35" transform="rotate(-50 545 550)"/>
    <path d="M110 640c-100 40-80 190 20 220" fill="none" stroke="${INK}" stroke-width="52" stroke-linecap="round"/>`,
  loaf: `<ellipse cx="300" cy="760" rx="250" ry="150"/>
    <circle cx="300" cy="520" r="130"/>
    <path d="M196 436L156 290l118 110z"/><path d="M404 436l40-146-118 110z"/>
    <path d="M540 800c90 0 90 90 10 100" fill="none" stroke="${INK}" stroke-width="48" stroke-linecap="round"/>`,
};
const raptor = {
  stand: `<ellipse cx="320" cy="620" rx="190" ry="115"/>
    <path d="M150 600C40 620-20 720 0 820l70 8c20-100 100-150 170-158z"/>
    <path d="M450 540c60-60 130-80 220-40l-36 56c-60-14-110 16-150 50z"/>
    <circle cx="500" cy="520" r="54"/>
    <rect x="270" y="690" width="54" height="230" rx="27" transform="rotate(12 297 805)"/>
    <rect x="380" y="690" width="54" height="230" rx="27" transform="rotate(-10 407 805)"/>`,
  run: `<ellipse cx="330" cy="560" rx="210" ry="110" transform="rotate(-10 330 560)"/>
    <path d="M140 560C30 540-10 620 0 740l70-10c0-80 50-120 120-130z"/>
    <path d="M480 470c70-70 150-80 240-20l-40 56c-70-20-120 10-160 50z"/>
    <circle cx="540" cy="460" r="54"/>
    <rect x="240" y="630" width="54" height="250" rx="27" transform="rotate(40 267 755)"/>
    <rect x="400" y="640" width="54" height="250" rx="27" transform="rotate(-30 427 765)"/>`,
};
const head = {
  front: `<circle cx="300" cy="560" r="230"/>
    <path d="M70 590C50 300 550 300 530 590c-50-130-120-180-230-180S120 460 70 590z"/>
    <rect x="256" y="780" width="88" height="90" rx="22"/>`,
  tilt: `<g transform="rotate(-14 300 560)"><circle cx="300" cy="560" r="230"/>
    <path d="M70 590C50 300 550 300 530 590c-50-130-120-180-230-180S120 460 70 590z"/>
    <rect x="256" y="780" width="88" height="90" rx="22"/></g>`,
};
const ratkin = {
  stand: `<circle cx="300" cy="160" r="78"/><circle cx="226" cy="98" r="40"/><circle cx="374" cy="98" r="40"/>
    <path d="M202 268h196a54 54 0 0 1 54 54v240H148V322a54 54 0 0 1 54-54z"/>
    <rect x="120" y="276" width="74" height="310" rx="37" transform="rotate(7 157 431)"/>
    <rect x="406" y="276" width="74" height="310" rx="37" transform="rotate(-7 443 431)"/>
    <rect x="208" y="550" width="84" height="390" rx="42"/>
    <rect x="308" y="550" width="84" height="390" rx="42"/>
    <path d="M460 700c120 20 130 170 20 240" fill="none" stroke="${INK}" stroke-width="40" stroke-linecap="round"/>`,
  bugaboo: `<ellipse cx="300" cy="520" rx="250" ry="300"/>
    <circle cx="220" cy="400" r="48" fill="#0A0A0D"/><circle cx="380" cy="400" r="48" fill="#0A0A0D"/>
    <rect x="200" y="780" width="60" height="200" rx="30"/><rect x="340" y="780" width="60" height="200" rx="30"/>`,
};

// slug -> ordered list of [poseName, svgBody]; looks cycle through these.
const SETS = {
  'carl': Object.entries(human),
  'katya-grim': [human.stand, human.walk, human.armsUp, human.action].map((s, i) => [['stand', 'walk', 'armsUp', 'action'][i], s]),
  'princess-donut': Object.entries(cat),
  'mongo': Object.entries(raptor),
  'samantha': Object.entries(head),
  'mordecai': Object.entries(ratkin),
};

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
for (const [slug, poses] of Object.entries(SETS)) {
  const dir = join('looks', slug);
  mkdirSync(dir, { recursive: true });
  for (const [name, body] of poses) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}"><g fill="${INK}">${body}</g></svg>`;
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
    const buf = await page.screenshot({ omitBackground: true, clip: { x: 0, y: 0, width: W, height: H } });
    writeFileSync(join(dir, `placeholder-${name}.png`), buf);
    console.log('wrote', join(dir, `placeholder-${name}.png`), buf.length);
  }
}
await browser.close();
