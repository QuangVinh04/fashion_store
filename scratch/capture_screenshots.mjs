import { createServer } from '../apps/storefront/node_modules/vite/dist/node/index.js';
import { existsSync, mkdirSync } from 'node:fs';
import { Browser } from '../.agents/skills/design-taste-frontend/scripts/lib/cdp.mjs';

const outDir = 'd:/fashion_store/docs/screenshots';
if (!existsSync(outDir)) mkdirSync(outDir, { recursive: true });

console.log('1. Starting Vite storefront server on port 3000...');
const server = await createServer({
  configFile: 'd:/fashion_store/apps/storefront/vite.config.ts',
  root: 'd:/fashion_store/apps/storefront',
  server: { port: 3000, host: '0.0.0.0' },
});
await server.listen();
console.log('Vite server listening on port 3000');

console.log('2. Launching headless browser...');
const browser = await Browser.launch({ width: 1440, height: 900 });
const page = await browser.newPage({ width: 1440, height: 900 });

try {
  console.log('3. Navigating to products list through BFF (http://localhost:8083/products)...');
  await page.goto('http://localhost:8083/products', { timeoutMs: 20000, settleMs: 2500 });
  await page.screenshot({ path: `${outDir}/products-list.jpg`, quality: 80 });
  console.log('Saved products-list.jpg');

  console.log('4. Navigating to product detail through BFF (http://localhost:8083/products/prod-36)...');
  await page.goto('http://localhost:8083/products/prod-36', { timeoutMs: 20000, settleMs: 2500 });
  await page.screenshot({ path: `${outDir}/product-detail.jpg`, quality: 80 });
  console.log('Saved product-detail.jpg');

  console.log('5. Navigating to product reviews through BFF (http://localhost:8083/products/prod-36/reviews)...');
  await page.goto('http://localhost:8083/products/prod-36/reviews', { timeoutMs: 20000, settleMs: 2500 });
  await page.screenshot({ path: `${outDir}/product-reviews.jpg`, quality: 80 });
  console.log('Saved product-reviews.jpg');

} catch (err) {
  console.error('Error during capture:', err);
} finally {
  console.log('Closing browser and server...');
  await page.close();
  await browser.close();
  await server.close();
  console.log('Done!');
}
