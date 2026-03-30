const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch();
  const page = await browser.newPage();
  
  // Keep track of console logs and errors
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));
  page.on('pageerror', err => console.log('PAGE ERROR:', err.message));
  page.on('requestfailed', request => console.log('REQUEST FAILED:', request.url(), request.failure().errorText));

  await page.setViewport({ width: 1280, height: 800 });
  await page.goto('http://localhost:5173', { waitUntil: 'networkidle2' });
  await page.screenshot({ path: 'pc_home.png' });
  
  // Go to /workspace
  await page.goto('http://localhost:5173/workspace', { waitUntil: 'networkidle2' });
  await page.screenshot({ path: 'pc_workspace.png' });

  // Mobile viewport
  await page.setViewport({ width: 375, height: 812, isMobile: true });
  await page.goto('http://localhost:5173/mobile', { waitUntil: 'networkidle2' });
  await page.screenshot({ path: 'mobile_home.png' });

  await page.goto('http://localhost:5173/mobile/identification', { waitUntil: 'networkidle2' });
  await page.screenshot({ path: 'mobile_identification.png' });

  await browser.close();
})();
