/** Render paginated actual git history with the repository's Korean font. */
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const {chromium}=await import(process.env.DEVMOZIP_PLAYWRIGHT_MODULE||'playwright');
const root=new URL('../',import.meta.url);
const base=new URL('docs/assets/readme/git-history/',root);
const manifest=JSON.parse(await readFile(new URL('manifest.json',base),'utf8'));
const font=await readFile(new URL('frontend/public/fonts/PretendardVariable.woff2',root));
const browser=await chromium.launch({args:['--no-sandbox']});
try{
 const page=await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1});
 for(const {name} of manifest.pages){
  const svg=await readFile(new URL(name+'.svg',base),'utf8');
  await page.setContent(`<style>@font-face{font-family:Pretendard;src:url(data:font/woff2;base64,${font.toString('base64')})}body{margin:0}svg{display:block}</style>${svg}`);
  await page.evaluate(()=>document.fonts.ready);
  await page.locator('svg').screenshot({path:fileURLToPath(new URL(name+'.png',base)),timeout:15000});
  console.log(name+'.png');
 }
}finally{await browser.close();}
