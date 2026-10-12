// Run: node --experimental-vm-modules src/test/js/jd-format-tests.mjs
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const assets=new URL('../../main/resources/static/assets/',import.meta.url);
const source=await readFile(new URL('../resources/jd-pdf-input.txt',import.meta.url),'utf8');
const expected=(await readFile(new URL('../resources/jd-pdf-output.md',import.meta.url),'utf8')).trim();
const code=await readFile(new URL('jd-formatter.js',assets),'utf8');
const checkModule=new vm.SourceTextModule(code,{context:vm.createContext({})});
await checkModule.link(()=>{throw new Error('Unexpected formatter import');});await checkModule.evaluate();
const {formatJd}=checkModule.namespace;
assert.equal(formatJd(source),expected,'PDF layout must match the backend fixture');
assert.equal(formatJd(expected),expected,'Authored Markdown stays intact');
assert.equal(formatJd('CÔNG TY\nĐỊA ĐIỂM\nSeoul\nYêu cầu\nReact'),
  '**CÔNG TY**\n\n**ĐỊA ĐIỂM** Seoul\n\n\n## Yêu cầu\n\n- React');

function node(tag,text,cls) {
  return {tagName:tag.toUpperCase(),className:cls,children:[],attrs:{},hidden:false,details:{open:false},
    get textContent(){return (text??'')+this.children.map(x=>x.textContent??'').join('');},
    set textContent(value){text=value;this.children=[];},
    append(...children){this.children.push(...children);},replaceChildren(...children){this.children=children;},
    setAttribute(k,v){this.attrs[k]=v;},closest(){return this.details;}};
}
const ids=new Map(),get=id=>{if(!ids.has(id))ids.set(id,node('div'));return ids.get(id);};
get('recruitment-jd').value=source;
let request=async()=>{throw new Error('Groq unavailable');};
const helpers={api:(...args)=>request(...args),$:get,node,action(){},form(){},busy(){},notice(){},money:String};
const context=vm.createContext({document:{createTextNode:text=>node('#text',text)},URL,setTimeout,clearTimeout});
const common=new vm.SyntheticModule(Object.keys(helpers),function(){for(const [k,v] of Object.entries(helpers))this.setExport(k,v);},{context});
const md=new vm.SourceTextModule(await readFile(new URL('markdown.js',assets),'utf8'),{context});
const formatter=new vm.SourceTextModule(code,{context});
const ui=new vm.SourceTextModule((await readFile(new URL('recruitment.js',assets),'utf8'))+
  '\nexport {updateJdMarkdown,parseJd,invalidateJd,jobCopy};',{context});
await md.link(()=>common);
await formatter.link(()=>{throw new Error('Unexpected formatter import');});
await ui.link(path=>({'./common.js':common,'./markdown.js':md,'./jd-formatter.js':formatter})[path]);
await ui.evaluate();
const descendants=element=>[element,...element.children.flatMap(descendants)];
const preview=()=>descendants(get('recruitment-jd-markdown'));
ui.namespace.updateJdMarkdown();
assert.equal(get('recruitment-jd').value,source);
assert.equal(get('recruitment-jd-markdown').details.open,true);
assert.equal(preview().find(x=>x.tagName==='H5').textContent,'BẢN MÔ TẢ CÔNG VIỆC Frontend Developer');
assert.equal(preview().filter(x=>x.tagName==='H6').length,4);
assert.equal(preview().filter(x=>x.tagName==='STRONG').length,6);
assert.equal(preview().filter(x=>x.tagName==='UL').length,4);
await ui.namespace.parseJd();
assert.match(get('recruitment-jd-status').textContent,/Groq unavailable/);
assert.equal(preview().filter(x=>x.tagName==='H6').length,4,'LLM failure must not remove formatting');

const data={role:'Frontend',company:'Quarry',location:'Seoul',workMode:'ANY',minExperience:2,
  salaryMin:0,salaryMax:999999999999,requiredSkills:['React'],preferredSkills:[],description:source};
request=async()=>({data,missingFields:[],source:'groq',warnings:[]});
await ui.namespace.parseJd();
assert.equal(preview().filter(x=>x.tagName==='H6').length,4,'Older API without formattedDescription uses local formatting');
assert.equal(get('recruitment-jd').value,source);
request=async()=>({data,formattedDescription:'# Old cached layout',missingFields:[],source:'groq',warnings:[]});
await ui.namespace.parseJd();
assert.equal(preview().filter(x=>x.tagName==='STRONG').length,6,'Cached extraction must not downgrade preview');
const posted=ui.namespace.jobCopy({...data,title:'Frontend'});
assert.equal(descendants(posted).filter(x=>x.tagName==='H6').length,4);

let resolve;
request=()=>new Promise(done=>{resolve=done;});
const pending=ui.namespace.parseJd();
get('recruitment-jd').value='Company New\nRequirements\nVue';
ui.namespace.invalidateJd();ui.namespace.updateJdMarkdown();
resolve({data,formattedDescription:expected,missingFields:[],warnings:[]});await pending;
assert.match(get('recruitment-jd-markdown').textContent,/Vue/);
assert.doesNotMatch(get('recruitment-jd-markdown').textContent,/Quarry/,'Stale AI response must not overwrite a newer draft');
get('recruitment-jd').value='';ui.namespace.updateJdMarkdown();
assert.equal(get('recruitment-jd-markdown').hidden,true);
export const result='PASS: shared PDF fixture, real Markdown DOM, immediate preview, AI failure, legacy API, posted JD, stale response, raw preservation, empty input';
