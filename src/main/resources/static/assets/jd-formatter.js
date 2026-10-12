// Presentation only; keep the textarea and extraction payload unchanged.
// Keep these rules aligned with JdFormatter.java for preview and posted jobs.
const key=text=>text.normalize('NFD').replace(/\p{M}/gu,'').replace(/[đĐ]/g,'d').toLowerCase().trim().replace(/[:：]+$/,'').trim();
const sectionGroups=[
  ['mo ta cong viec','noi dung cong viec','trach nhiem','job description','responsibilities'],
  ['yeu cau','yeu cau tuyen dung','yeu cau cong viec','yeu cau ung vien','requirements','qualifications'],
  ['uu tien','ky nang uu tien','diem cong','nice to have','nice-to-have','preferred qualifications'],
  ['quyen loi','quyen loi va che do','phuc loi','che do dai ngo','benefits'],
  ['cach ung tuyen','huong dan ung tuyen','thong tin ung tuyen','how to apply'],
  ['ve cong ty','gioi thieu cong ty','about us','about the company']
];
const section=text=>sectionGroups.findIndex(group=>group.includes(key(text)));
const labels=new Set(['cong ty','company','vi tri tuyen dung','vi tri','position','dia chi cong ty','dia chi lam viec','dia diem','location','nganh nghe','trang thai','hinh thuc','luong','salary','thanh toan luong']);
const label=text=>labels.has(key(text));
const metadata=text=>text.replace(/^(Công ty|Company|Vị trí tuyển dụng|Vị trí|Position|Địa chỉ công ty|Địa chỉ làm việc|Địa điểm|Location|Ngành nghề|Trạng thái|Hình thức|Lương|Salary|Thanh toán lương)([:：]?)(\s+)(.+)$/iu,'**$1$2** $4');
const uppercase=text=>/^\p{Lu}/u.test(text);
export function formatJd(source) {
  const text=String(source??'').replace(/\r\n?/g,'\n').trim();
  if(!text||/^\s{0,3}(?:#{1,6}\s|```|~~~)/m.test(text))return text;
  const lines=text.split('\n');let out='',paragraph=[],bullets=false,started=false;
  const flush=()=>{if(paragraph.length){out+=(bullets?'- ':'')+paragraph.join(' ')+(bullets?'\n':'\n\n');paragraph=[];}};
  for(let i=0;i<lines.length;i++) {
    let line=lines[i].trim();if(!line){flush();continue;}
    const k=key(line);
    if(!started&&!out&&(k==='ban mo ta cong viec'||k==='job description'||k.startsWith('ban mo ta cong viec ')||k.startsWith('job description '))) {
      if((k==='ban mo ta cong viec'||k==='job description')&&i+1<lines.length) {
        const next=lines[i+1].trim();
        if(next&&!label(next)&&section(next)<0&&metadata(next)===next){line+=' '+next;i++;}
      }
      out+='# '+line+'\n\n';continue;
    }
    const heading=section(line);
    if(heading>=0){flush();out+='\n## '+line.replace(/[:：]+$/,'')+'\n\n';bullets=heading!==5;started=true;continue;}
    if(!started) {
      if(label(line)) {
        const value=[];
        while(i+1<lines.length) {
          const next=lines[i+1].trim();
          if(!next||label(next)||section(next)>=0||metadata(next)!==next||(value.length&&uppercase(next)))break;
          value.push(next);i++;
        }
        out+='**'+line+'**'+(value.length?' '+value.join(' '):'')+'\n\n';continue;
      }
      out+=metadata(line)+'\n\n';continue;
    }
    if(/^(?:[-*•]|[0-9]+[.)])\s+/.test(line)){flush();line=line.replace(/^(?:[-*•]|[0-9]+[.)])\s+/,'');}
    else if(paragraph.length&&uppercase(line))flush();
    paragraph.push(line);if(/[.!?;。]$/.test(line))flush();
  }
  flush();return out.trim();
}
