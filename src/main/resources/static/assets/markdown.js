// Supported Markdown is rendered as DOM nodes, never as user-supplied HTML.
import {rawNode as node} from './common.js';

function safeLink(value) {
  if(/[\u0000-\u0020\\]/.test(value))return null;
  if(/^\/(?!\/)/.test(value)||value.startsWith('#'))return value;
  try {const url=new URL(value);return ['https:','http:','mailto:'].includes(url.protocol)?url.href:null;}catch{return null;}
}
function inline(parent,text,depth=0) {
  if(depth>8){parent.append(document.createTextNode(text));return;}
  const pattern=/(`+)([^`]+)\1|\[([^\]\n]+)\]\(([^\s)]+)\)|(\*\*|__)(.+?)\5|(~~)(.+?)\7|(\*|_)([^\n]+?)\9|\\([\\`*{}\[\]()#+\-.!_>])/g;
  let offset=0,match;
  while((match=pattern.exec(text))) {
    parent.append(document.createTextNode(text.slice(offset,match.index)));
    let element;
    if(match[1])element=node('code',match[2]);
    else if(match[3]){const href=safeLink(match[4]);element=node(href?'a':'span');if(href){element.href=href;element.rel='noopener noreferrer';}inline(element,match[3],depth+1);}
    else if(match[5]){element=node('strong');inline(element,match[6],depth+1);}
    else if(match[7]){element=node('del');inline(element,match[8],depth+1);}
    else if(match[9]){element=node('em');inline(element,match[10],depth+1);}
    else element=document.createTextNode(match[11]);
    parent.append(element);offset=pattern.lastIndex;
  }
  parent.append(document.createTextNode(text.slice(offset)));
}
export function markdown(text) {
  const root=node('div',undefined,'jd-markdown'),lines=String(text??'').replace(/\r\n?/g,'\n').split('\n');
  let paragraph=[],list=null,fence=null,code=[];
  const flush=()=>{if(paragraph.length){const p=node('p');inline(p,paragraph.join('\n'));root.append(p);paragraph=[];}list=null;};
  const codeBlock=()=>{const pre=node('pre');pre.append(node('code',code.join('\n')));root.append(pre);code=[];};
  for(const line of lines) {
    if(fence){if(new RegExp('^\\s{0,3}'+fence.char+'{'+fence.length+',}\\s*$').test(line)){codeBlock();fence=null;}else code.push(line);continue;}
    const open=line.match(/^\s{0,3}(`{3,}|~{3,})[^`]*$/);
    if(open){flush();fence={char:open[1][0],length:open[1].length};continue;}
    if(!line.trim()){flush();continue;}
    const heading=line.match(/^\s{0,3}(#{1,6})\s+(.+?)(?:\s+#+)?$/);
    if(heading){flush();const h=node(heading[1].length===1?'h5':'h6');inline(h,heading[2]);root.append(h);continue;}
    if(/^\s{0,3}(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$/.test(line)){flush();root.append(node('hr'));continue;}
    const item=line.match(/^\s*(?:([-+*])|(\d+)[.)])\s+(.+)$/);
    if(item){if(paragraph.length)flush();const type=item[1]?'ul':'ol';if(!list||list.tagName.toLowerCase()!==type){list=node(type);if(type==='ol')list.start=Number(item[2]);root.append(list);}const li=node('li');inline(li,item[3]);list.append(li);continue;}
    const quote=line.match(/^\s{0,3}>\s?(.*)$/);
    if(quote){flush();const block=node('blockquote');inline(block,quote[1]);root.append(block);continue;}
    if(list)flush();paragraph.push(line);
  }
  if(fence)codeBlock();flush();return root;
}
