// Headless smoke test: serves web/crawl, loads it at desktop and phone sizes,
// scrubs the timeline, switches crawler, opens the party view, and saves
// screenshots. Fails on console errors or failed requests.
// Usage: node tools/smoke.mjs [outDir]   (from web/crawl)
import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const root = process.cwd();
const out = process.argv[2] || join(root, '..', '..', 'build', 'crawl-smoke');
await mkdir(out, { recursive: true });
const types = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.json': 'application/json', '.png': 'image/png', '.webp': 'image/webp', '.svg': 'image/svg+xml' };
const server = createServer(async (req, res) => {
  const p = normalize(decodeURIComponent(req.url.split('?')[0])).replace(/^\/+/, '') || 'index.html';
  try { const b = await readFile(join(root, p)); res.writeHead(200, { 'content-type': types[extname(p)] || 'application/octet-stream' }); res.end(b); }
  catch { res.writeHead(404); res.end('nope'); }
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const base = `http://127.0.0.1:${server.address().port}/`;

const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
const problems = [];
async function run(name, viewport, steps) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: 1, hasTouch: viewport.width < 800 });
  page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') problems.push(`[${name}] console.${m.type()}: ${m.text()}`); });
  page.on('pageerror', e => problems.push(`[${name}] pageerror: ${e.message}`));
  page.on('requestfailed', r => { if (!r.url().includes('fonts.g')) problems.push(`[${name}] requestfailed: ${r.url()} ${r.failure()?.errorText}`); });
  await page.goto(base + 'index.html#carl', { waitUntil: 'networkidle' });
  await page.waitForSelector('.loading.done', { timeout: 15000 });
  await page.waitForTimeout(900);
  await steps(page);
  await page.close();
}

await run('desktop', { width: 1440, height: 900 }, async (page) => {
  await page.screenshot({ path: join(out, 'desktop-1.png') });
  const track = await page.locator('#track').boundingBox();
  // scrub from book 1 into book 2 and back, fast
  await page.mouse.move(track.x + track.width * 0.01, track.y + 24);
  await page.mouse.down();
  for (let i = 1; i <= 12; i++) { await page.mouse.move(track.x + track.width * (0.01 + 0.012 * i), track.y + 24); await page.waitForTimeout(40); }
  await page.screenshot({ path: join(out, 'desktop-2-midscrub.png') });
  await page.mouse.up();
  await page.waitForTimeout(800);
  await page.screenshot({ path: join(out, 'desktop-3-after.png') });
  const state1 = await page.evaluate(() => ({ eyebrow: document.getElementById('eyebrow').textContent, when: document.getElementById('when').textContent, lv: document.querySelector('#bLv b').textContent, log: document.getElementById('log').children.length }));
  console.log('after scrub', state1);
  await page.click('#roster li:nth-child(2) button');
  await page.waitForTimeout(900);
  await page.screenshot({ path: join(out, 'desktop-4-donut.png') });
  console.log('donut', await page.evaluate(() => ({ name: document.getElementById('name').textContent, desc: document.getElementById('desc').textContent, accent: getComputedStyle(document.documentElement).getPropertyValue('--accent') })));
  await page.click('#scene3d');
  await page.waitForTimeout(900);
  await page.screenshot({ path: join(out, 'desktop-5-back.png') });
  await page.click('#scene3d');
  await page.keyboard.press('p');
  await page.waitForTimeout(700);
  await page.screenshot({ path: join(out, 'desktop-6-party.png') });
  await page.keyboard.press('Escape');
  await page.keyboard.press('ArrowRight');
  await page.waitForTimeout(300);
  await page.screenshot({ path: join(out, 'desktop-7-midmorph.png') });
  await page.waitForTimeout(700);
  await page.screenshot({ path: join(out, 'desktop-8-next.png') });
  console.log('scroll', await page.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth })));
});

await run('phone', { width: 390, height: 844 }, async (page) => {
  await page.screenshot({ path: join(out, 'phone-1.png') });
  await page.click('#roster li:nth-child(6) button');
  await page.waitForTimeout(900);
  await page.screenshot({ path: join(out, 'phone-2-mordecai.png') });
  await page.keyboard.press('p');
  await page.waitForTimeout(600);
  await page.screenshot({ path: join(out, 'phone-3-party.png') });
});

// A tall phone on a look with a long description: the card must stay clear of the text.
await run('tallphone', { width: 430, height: 790 }, async (page) => {
  for (let i = 0; i < 3; i++) await page.keyboard.press('ArrowRight');
  await page.waitForTimeout(900);
  const clamped = await page.evaluate(() => document.getElementById('title').classList.contains('clamped'));
  const geo = await page.evaluate(() => {
    const r = (id) => document.getElementById(id).getBoundingClientRect();
    return { whenBottom: r('when').bottom, cardTop: r('flip').top, cardH: r('flip').height };
  });
  console.log('tallphone', { clamped, ...geo });
  await page.screenshot({ path: join(out, 'phone-4-longdesc.png') });
  if (geo.cardTop < geo.whenBottom) problems.push('[tallphone] card overlaps the title block');
  await page.click('#desc');
  await page.waitForTimeout(300);
  await page.screenshot({ path: join(out, 'phone-5-descfull.png') });
  const shown = await page.evaluate(() => !document.getElementById('descFull').hidden);
  if (clamped && !shown) problems.push('[tallphone] tapping the clamped description did not open it');
  console.log('scroll', await page.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth, bodyH: document.body.scrollHeight, h: innerHeight })));
});

await browser.close(); server.close();
if (problems.length) { console.log('PROBLEMS:\n' + problems.join('\n')); process.exitCode = 1; } else console.log('smoke ok, screenshots in', out);
