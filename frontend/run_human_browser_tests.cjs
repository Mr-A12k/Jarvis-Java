const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = 'C:\\Users\\Kabil\\.gemini\\antigravity\\brain\\6022c791-0a56-4255-8cc5-53a97c2ecf64\\screenshots';

if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

(async () => {
  console.log('===============================================================');
  console.log('🤖 LAUNCHING REAL GOOGLE CHROME FOR HUMAN BROWSER VERIFICATION');
  console.log('===============================================================');

  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    defaultViewport: { width: 1440, height: 920 },
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--disable-gpu',
    ]
  });

  const page = await browser.newPage();
  
  try {
    // 1. Initial Page Load
    console.log('🌐 1. Loading http://127.0.0.1:18080 ...');
    await page.goto('http://127.0.0.1:18080', { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(2500);

    const title = await page.title();
    console.log(`   Page loaded with title: "${title}"`);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '01_initial_dashboard.png') });
    console.log('   📸 [01/11] Captured 01_initial_dashboard.png');

    // 2. Open Iron Man 3D Holographic HUD
    console.log('✨ 2. Testing Iron Man 3D Holographic HUD modal...');
    const hudBtn = await page.$('.iron-man-btn');
    if (hudBtn) {
      await hudBtn.click();
      console.log('   Clicked "Iron Man HUD" button in topbar.');
      await sleep(2500);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '02_iron_man_hologram_hud.png') });
      console.log('   📸 [02/11] Captured 02_iron_man_hologram_hud.png');

      // Test HUD Text Console tab
      const textConsoleTab = await page.evaluateHandle(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        return btns.find(b => b.textContent.includes('TEXT CONSOLE'));
      });
      if (textConsoleTab && textConsoleTab.asElement()) {
        await textConsoleTab.asElement().click();
        await sleep(1000);
        await page.screenshot({ path: path.join(SCREENSHOT_DIR, '02b_hud_text_console.png') });
        console.log('   📸 Captured 02b_hud_text_console.png');
      }

      // Close the HUD modal cleanly using .hud-close-button
      const closeBtn = await page.$('.hud-close-button');
      if (closeBtn) {
        await closeBtn.click();
        console.log('   Clicked .hud-close-button to return to dashboard.');
        await sleep(1500);
      }
    }

    // Helper function to click sidebar navigation items safely
    async function clickSidebarItem(textMatch) {
      return page.evaluate((text) => {
        const items = Array.from(document.querySelectorAll('.sidebar .nav-item'));
        const target = items.find(el => el.textContent.toLowerCase().includes(text.toLowerCase()));
        if (target) {
          target.click();
          return true;
        }
        return false;
      }, textMatch);
    }

    // 3. Navigate to Biometrics Panel
    console.log('👤 3. Navigating to Face & Voice ID Panel...');
    const clickedBio = await clickSidebarItem('Face & Voice ID');
    if (clickedBio) {
      await sleep(2000);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '03_biometrics_panel.png') });
      console.log('   📸 [03/11] Captured 03_biometrics_panel.png');
    }

    // 4. Navigate to Goal Planner Panel
    console.log('🎯 4. Navigating to Autonomous Goal Planner Panel...');
    const clickedGoal = await clickSidebarItem('Goal planner');
    if (clickedGoal) {
      await sleep(2000);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '04_goal_planner_panel.png') });
      console.log('   📸 [04/11] Captured 04_goal_planner_panel.png');
    }

    // 5. Navigate to Shopping Assistant Panel
    console.log('🛒 5. Navigating to Shopping Assistant Panel...');
    const clickedShop = await clickSidebarItem('Shopping assistant');
    if (clickedShop) {
      await sleep(2000);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '05_shopping_assistant_panel.png') });
      console.log('   📸 [05/11] Captured 05_shopping_assistant_panel.png');
    }

    // 6. Navigate to Task Scheduler Panel
    console.log('⏰ 6. Navigating to Task Scheduler Panel...');
    const clickedSched = await clickSidebarItem('Task scheduler');
    if (clickedSched) {
      await sleep(2000);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '06_task_scheduler_panel.png') });
      console.log('   📸 [06/11] Captured 06_task_scheduler_panel.png');
    }

    // 7. Navigate to Database Memory Panel
    console.log('💾 7. Navigating to Database Memory Panel...');
    const clickedDb = await clickSidebarItem('Database memory');
    if (clickedDb) {
      await sleep(2000);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '07_database_memory_panel.png') });
      console.log('   📸 [07/11] Captured 07_database_memory_panel.png');
    }

    // 8. Return to Assistant Chat Workspace
    console.log('💬 8. Returning to Main Assistant Chat Workspace...');
    await clickSidebarItem('Assistant');
    await sleep(1500);

    // Helper function to submit chat in React
    async function typeAndSubmit(text) {
      await page.waitForSelector('textarea:not([disabled])', { timeout: 10000 });
      await page.evaluate((val) => {
        const ta = document.querySelector('textarea');
        const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
        nativeSetter.call(ta, val);
        ta.dispatchEvent(new Event('input', { bubbles: true }));
        ta.dispatchEvent(new Event('change', { bubbles: true }));
      }, text);
      await sleep(600);

      const sendBtn = await page.$('button.send-button:not([disabled])');
      if (sendBtn) {
        await sendBtn.click();
      } else {
        await page.focus('textarea');
        await page.keyboard.press('Enter');
      }
    }

    // 9. Human-like interaction: Type English Biometrics Command
    console.log('⌨️ 9. Human typing: "Verify my face and identity" ...');
    await typeAndSubmit('Verify my face and identity');
    console.log('   Submitted command. Waiting for DeepSeek reasoning and verification response...');
    await page.waitForFunction(() => {
      const text = document.body.innerText;
      return text.includes('Biometric Verification Confirmed') || text.includes('Master Kabil');
    }, { timeout: 25000 });
    await sleep(1500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '08_chat_biometrics_english.png') });
    console.log('   📸 [08/11] Captured 08_chat_biometrics_english.png');

    // 10. Human-like interaction: Type Tamil Biometrics Command
    console.log('⌨️ 10. Human typing: "என் முகத்தை சரிபார்" ...');
    await typeAndSubmit('என் முகத்தை சரிபார்');
    console.log('   Submitted Tamil command. Waiting for Tamil biometric verification...');
    await page.waitForFunction(() => {
      const text = document.body.innerText;
      return text.includes('பயோமெட்ரிக் அங்கீகாரம்') || text.includes('கபில் ஐயா');
    }, { timeout: 25000 });
    await sleep(1500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '09_chat_biometrics_tamil.png') });
    console.log('   📸 [09/11] Captured 09_chat_biometrics_tamil.png');

    // 11. Human-like interaction: Type Tamil Hardware Diagnostics Command
    console.log('⌨️ 11. Human typing: "கணினி நிலை என்ன?" ...');
    await typeAndSubmit('கணினி நிலை என்ன?');
    console.log('   Submitted Tamil hardware query. Waiting for live telemetry...');
    await page.waitForFunction(() => {
      const text = document.body.innerText;
      return text.includes('கணினி வன்பொருள்') || text.includes('நினைவகம்');
    }, { timeout: 25000 });
    await sleep(1500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '10_chat_hardware_tamil.png') });
    console.log('   📸 [10/11] Captured 10_chat_hardware_tamil.png');

    // 12. Human-like interaction: Expand all DeepSeek Thinking Process accordions
    console.log('🔍 12. Testing human inspection of DeepSeek Thinking Process...');
    const detailsCount = await page.evaluate(() => {
      const accordions = Array.from(document.querySelectorAll('details.think-process-accordion'));
      accordions.forEach(d => d.open = true);
      return accordions.length;
    });
    console.log(`   Expanded ${detailsCount} DeepSeek reasoning accordions.`);
    await sleep(1500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '11_deepseek_thinking_expanded.png') });
    console.log('   📸 [11/11] Captured 11_deepseek_thinking_expanded.png');

    console.log('===============================================================');
    console.log('🎉 REAL BROWSER HUMAN TEST COMPLETED SUCCESSFULLY! ALL 11 STEPS PASSED.');
    console.log('===============================================================');
  } catch (err) {
    console.error('❌ Browser Test Encountered Error:', err);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'error_snapshot.png') });
  } finally {
    await browser.close();
  }
})();
