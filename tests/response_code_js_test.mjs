// Optional Node check for the actual standalone HTML script. No browser or network.
import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const html = fs.readFileSync(path.join(root, 'tools/response-code.html'), 'utf8');
assert.match(html, /connect-src 'none'/);
assert(!/<script[^>]+src=/i.test(html));
assert(!/\b(fetch|XMLHttpRequest|WebSocket|localStorage|sessionStorage|sendBeacon)\b/.test(html));
const code = /<script>([\s\S]*?)<\/script>/.exec(html)[1];
const fields = Object.fromEntries(['calculator','challenge','mode','result','status'].map(key => [key, {value:'',textContent:''}]));
let submit;
fields.calculator.addEventListener = (name, callback) => { assert.equal(name,'submit'); submit = callback; };
const context = vm.createContext({document:{getElementById:key=>fields[key]}});
vm.runInContext(code, context);
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME,'bin/java') : 'java';
const reference = execFileSync(java,[path.join(root,'tests/response_code/JavaRandomOracle.java')],{maxBuffer:8000000});
assert.equal(reference.length,4000000);
let differences=0;
for(let seed=0;seed<1000000;seed++) {
  const text=String(seed), javaValue=context.calculateResponse(text,'java');
  assert.equal(Number(javaValue),reference.readUInt32BE(seed*4));
  const helper=context.calculateResponse(text,'helper');
  differences += javaValue!==helper;
}
assert.equal(differences,224);
fields.challenge.value='123456';fields.mode.value='java';let prevented=false;
submit({preventDefault:()=>{prevented=true;}});
assert(prevented);assert.equal(fields.result.textContent,'382410');assert.equal(fields.status.textContent,'');
fields.challenge.value='invalid';submit({preventDefault(){}});
assert.equal(fields.result.textContent,'');assert(fields.status.textContent.length>0);
assert.equal(context.calculateResponse('005189','helper'),'443980');
console.log('PASS: standalone HTML math matches JDK for all synthetic seeds; rare helper differences, submit/error display and no-network/no-storage constraints verified.');
