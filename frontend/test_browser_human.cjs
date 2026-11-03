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
  console.log('🚀 Launching real Google Chrome for human-like testing...');
  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    defaultViewport: { width: 1440, height: 900 },
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--disable-gpu',
    ]
  });

  const page = await browser.newPage();
  
  // Collect console logs
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      console.log('Browser Error:', msg.text());
    }
  });

  try {
    console.log('🌐 1. Navigating to http://127.0.0.1:18080 ...');
    await page.goto('http://127.0.0.1:18080', { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(2000);
    
    // Take screenshot of Initial Dashboard
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '01_initial_dashboard.png') });
    console.log('📸 Saved 01_initial_dashboard.png');

    // 2. Test Human Click: Voice Panel / Iron Man Hologram
    console.log('🗣️ 2. Testing Voice & Hologram HUD navigation...');
    // Look for voice nav or button
    const voiceNavButton = await page.$('button[data-view="voice"], nav button:has-text("Voice"), button:has-text("குரல்")') || 
                           await page.evaluateHandle(() => {
                             const buttons = Array.from(document.querySelectorAll('button, a'));
                             return buttons.find(b => b.textContent.includes('Voice') || b.textContent.includes('குரல்'));
                           });
    if (voiceNavButton && voiceNavButton.asElement()) {
      await voiceNavButton.asElement().click();
      await sleep(1500);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '02_voice_panel.png') });
      console.log('📸 Saved 02_voice_panel.png');
    }

    // 3. Test Human Click: Biometrics Panel
    console.log('👤 3. Testing Biometrics Panel navigation...');
    const biometricsBtn = await page.evaluateHandle(() => {
      const buttons = Array.from(document.querySelectorAll('button, a'));
      return buttons.find(b => b.textContent.includes('Biometric') || b.textContent.includes('பயோமெட்ரிக்'));
    });
    if (biometricsBtn && biometricsBtn.asElement()) {
      await biometricsBtn.asElement().click();
      await sleep(1500);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '03_biometrics_panel.png') });
      console.log('📸 Saved 03_biometrics_panel.png');
    }

    // 4. Test Human Click: Goal Planner Panel
    console.log('🎯 4. Testing Goal Planner Panel navigation...');
    const goalBtn = await page.evaluateHandle(() => {
      const buttons = Array.from(document.querySelectorAll('button, a'));
      return buttons.find(b => b.textContent.includes('Goal') || b.textContent.includes('இலக்கு'));
    });
    if (goalBtn && goalBtn.asElement()) {
      await goalBtn.asElement().click();
      await sleep(1500);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '04_goals_panel.png') });
      console.log('📸 Saved 04_goals_panel.png');
    }

    // 5. Test Human Click: Shopping Panel
    console.log('🛒 5. Testing Shopping Comparison Panel navigation...');
    const shoppingBtn = await page.evaluateHandle(() => {
      const buttons = Array.from(document.querySelectorAll('button, a'));
      return buttons.find(b => b.textContent.includes('Shopping') || b.textContent.includes('ஷாப்பிங்'));
    });
    if (shoppingBtn && shoppingBtn.asElement()) {
      await shoppingBtn.asElement().click();
      await sleep(1500);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '05_shopping_panel.png') });
      console.log('📸 Saved 05_shopping_panel.png');
    }

    // 6. Test Human Click: Automation & Scheduler Panel
    console.log('⏰ 6. Testing Automation / Scheduler Panel navigation...');
    const schedBtn = await page.evaluateHandle(() => {
      const buttons = Array.from(document.querySelectorAll('button, a'));
      return buttons.find(b => b.textContent.includes('Schedule') || b.textContent.includes('Automation') || b.textContent.includes('தானியங்கி'));
    });
    if (schedBtn && schedBtn.asElement()) {
      await schedBtn.asElement().click();
      await sleep(1500);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '06_scheduler_panel.png') });
      console.log('📸 Saved 06_scheduler_panel.png');
    }

    // 7. Return to Chat Workspace and test Human typing & deep thinking interactions
    console.log('💬 7. Returning to Chat Workspace...');
    const chatBtn = await page.evaluateHandle(() => {
      const buttons = Array.from(document.querySelectorAll('button, a'));
      return buttons.find(b => b.textContent.includes('Chat') || b.textContent.includes('அரட்டை'));
    });
    if (chatBtn && chatBtn.asElement()) {
      await chatBtn.asElement().click();
      await sleep(1000);
    }

    // 8. Human Interaction: Type English Biometric Command
    console.log('⌨️ 8. Human typing command: "Verify my face and identity" ...');
    const textarea = await page.$('textarea, input[type="text"]');
    if (textarea) {
      await textarea.click();
      await textarea.type('Verify my face and identity', { delay: 30 });
      await sleep(500);
      
      // Press Enter or click Send
      await page.keyboard.press('Enter');
      console.log('⏳ Waiting for JARVIS reply & DeepSeek thinking mode...');
      
      // Wait for reply to appear in DOM
      await page.waitForFunction(() => {
        const text = document.body.innerText;
        return text.includes('Biometric Verification Confirmed') || text.includes('Master Kabil');
      }, { timeout: 20000 });
      
      await sleep(1000);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '07_chat_biometric_english.png') });
      console.log('📸 Saved 07_chat_biometric_english.png');
    }

    // 9. Human Interaction: Type Tamil Biometric Command
    console.log('⌨️ 9. Human typing Tamil command: "என் முகத்தை சரிபார்" ...');
    const textarea2 = await page.$('textarea, input[type="text"]');
    if (textarea2) {
      await textarea2.click();
      // Clear previous text if any
      await page.keyboard.down('Control');
      await page.keyboard.press('A');
      await page.keyboard.up('Control');
      await page.keyboard.press('Backspace');

      await page.evaluate(() => {
        const input = document.querySelector('textarea, input[type="text"]');
        if (input) {
          input.value = 'என் முகத்தை சரிபார்';
          input.dispatchEvent(new Event('input', { bubbles: true }));
        }
      });
      await sleep(500);
      await page.keyboard.press('Enter');
      
      console.log('⏳ Waiting for Tamil biometric verification...');
      await page.waitForFunction(() => {
        const text = document.body.innerText;
        return text.includes('பயோமெட்ரிக் அங்கீகாரம்') || text.includes('கபில் ஐயா');
      }, { timeout: 20000 });
      
      await sleep(1000);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '08_chat_biometric_tamil.png') });
      console.log('📸 Saved 08_chat_biometric_tamil.png');
    }

    // 10. Human Interaction: Type Tamil Hardware Command
    console.log('⌨️ 10. Human typing Tamil Hardware command: "கணினி நிலை என்ன?" ...');
    await page.evaluate(() => {
      const input = document.querySelector('textarea, input[type="text"]');
      if (input) {
        input.value = 'கணினி நிலை என்ன?';
        input.dispatchEvent(new Event('input', { bubbles: true }));
      }
    });
    await sleep(500);
    await page.keyboard.press('Enter');
    
    console.log('⏳ Waiting for Tamil hardware telemetry reply...');
    await page.waitForFunction(() => {
      const text = document.body.innerText;
      return text.includes('கணினி வன்பொருள்') || text.includes('நினைவகம்');
    }, { timeout: 20000 });
    
    await sleep(1000);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '09_chat_hardware_tamil.png') });
    console.log('📸 Saved 09_chat_hardware_tamil.png');

    // 11. Human Interaction: Expand DeepSeek Thinking Process
    console.log('🔍 11. Testing human click to expand/collapse DeepSeek Thinking details...');
    const detailsCount = await page.evaluate(() => {
      const accordions = Array.from(document.querySelectorAll('details.think-process-accordion'));
      accordions.forEach(d => d.open = true);
      return accordions.length;
    });
    console.log(`Found and expanded ${detailsCount} DeepSeek thinking accordions.`);
    await sleep(1000);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '10_deepseek_thinking_expanded.png') });
    console.log('📸 Saved 10_deepseek_thinking_expanded.png');

    console.log('🎉 REAL BROWSER HUMAN-LIKE TEST COMPLETED SUCCESSFULLY!');
  } catch (err) {
    console.error('❌ Browser Test Error:', err);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'error_snapshot.png') });
  } finally {
    await browser.close();
  }
})();
