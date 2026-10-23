import {locale} from './i18n.js';
import {api, $, node, action, form, busy, notice, money, rawNode} from './common.js';
import {markdown} from './markdown.js';
import {formatJd} from './jd-formatter.js';
const base='/me/recruitment';
const modes={ANY:'Linh hoạt',ONSITE:'Tại văn phòng',HYBRID:'Kết hợp',REMOTE:'Từ xa'};
const statuses={PENDING:'Chờ ứng viên đồng ý',ACCEPTED:'Đã match',DECLINED:'Đã từ chối'};
const interviewStatuses={PENDING:'Chờ xác nhận',CONFIRMED:'Đã xác nhận',DECLINED:'Đã từ chối',CANCELLED:'Đã hủy'};
let viewer, initialized=false, feedPage=0, selectedMatch=null, matchesSignature='', threadVersion=0;
let activeView='opportunities', matchFilter='ALL';
let messageSnapshot=[], messageLoaded=false, interviewSignature='', forceMessageScroll=false;
function switchView(view) {
  activeView=view;
  document.querySelectorAll('[data-recruitment-section]').forEach(section=>section.hidden=section.dataset.recruitmentSection!==view);
  document.querySelectorAll('[data-recruitment-view]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.recruitmentView===view)));
  $('recruitment-conversation').hidden=view!=='matches'||!selectedMatch;
  $('recruitment-connections').hidden=view!=='matches'||Boolean(selectedMatch);
}
function filterMatches() {
  const rows=[...$('recruitment-matches').querySelectorAll('[data-match-status]')];
  rows.forEach(row=>row.hidden=matchFilter!=='ALL'&&row.dataset.matchStatus!==matchFilter);
  $('recruitment-filter-empty')?.remove();
  if(rows.length&&!rows.some(row=>!row.hidden))$('recruitment-matches').append(node('p','Chưa có kết nối ở trạng thái này.','recruitment-empty'));
  const empty=$('recruitment-matches').querySelector('.recruitment-empty');if(empty)empty.id='recruitment-filter-empty';
}
const splitSkills=text=>text.split(',').map(value=>value.trim()).filter(Boolean);
const salaryText=item=>item.salaryMin===0&&item.salaryMax===999999999999?'Lương thỏa thuận':money(item.salaryMin)+' – '+money(item.salaryMax)+' / tháng';
let jdVersion=0,jdTimer,parsedDescription='',extractionId=null,jdInFlight=false,jdQueued=false;
function invalidateJd() {
  clearTimeout(jdTimer);jdVersion++;parsedDescription='';extractionId=null;jdQueued=false;
  $('recruitment-jd-save').disabled=true;$('recruitment-jd-preview').hidden=true;$('recruitment-jd-preview').setAttribute('aria-busy','false');
}
async function parseJd() {
  updateJdMarkdown();
  if(jdInFlight){jdQueued=true;return;}
  const description=$('recruitment-jd').value,version=++jdVersion;
  if(!description.trim()){$('recruitment-jd-status').textContent='Dán nội dung JD để bắt đầu.';return;}
  jdInFlight=true;
  $('recruitment-jd-status').textContent='Đang phân tích JD…';
  $('recruitment-jd-preview').setAttribute('aria-busy','true');
  try {
    const result=await api(base+'/jobs/parse-jd','POST',{description});
    if(version!==jdVersion)return;
    // The preview is local, so cached/older extraction responses cannot downgrade its layout.
    updateJdMarkdown();
    const job=result.data,preview=$('recruitment-jd-preview');preview.replaceChildren(node('h4','Thông tin đã trích xuất'));
    const fields=node('dl',undefined,'recruitment-extracted');
    for(const [label,value] of [['Vị trí',job.role],['Công ty',job.company],['Địa điểm',job.location],['Hình thức',modes[job.workMode]],['Kinh nghiệm tối thiểu',job.minExperience+' năm'],['Lương',salaryText(job)]])fields.append(node('dt',label),(['Vị trí','Công ty','Địa điểm'].includes(label)&&value?rawNode:node)('dd',value??'Chưa xác định'));
    preview.append(fields,node('h5','Kỹ năng bắt buộc'),tags(job.requiredSkills),node('h5','Kỹ năng ưu tiên'),job.preferredSkills.length?tags(job.preferredSkills):node('p','Không được nêu trong JD.','muted'));
    preview.hidden=false;
    for(const warning of result.warnings??[])preview.append(node('p',warning,'muted'));
    if(result.missingFields.length){preview.append(node('p','Bổ sung vào nội dung JD: '+result.missingFields.join('; '),'recruitment-jd-warning'));$('recruitment-jd-status').textContent='JD còn thiếu thông tin. Sửa nội dung để hệ thống phân tích lại.';}
    else {parsedDescription=description;extractionId=result.extractionId??null;$('recruitment-jd-save').disabled=false;$('recruitment-jd-status').textContent=result.source==='groq'?'Đã phân tích JD bằng AI. Kiểm tra thông tin trước khi đăng.':'Đã phân tích JD. Bạn có thể đăng bài.';}
  }catch(error){if(version===jdVersion){$('recruitment-jd-status').textContent=error.message+(error.retryable===false?'':' Bấm Phân tích lại để thử lại.');}}
  finally {jdInFlight=false;if(version===jdVersion)$('recruitment-jd-preview').setAttribute('aria-busy','false');if(jdQueued){jdQueued=false;clearTimeout(jdTimer);jdTimer=setTimeout(parseJd,500);}}
}
function fill(formElement,item) {
  for(const [key,value] of Object.entries(item)) {
    const field=formElement.elements.namedItem(key);if(!field)continue;
    if(field.type==='checkbox')field.checked=value;else field.value=Array.isArray(value)?value.join(', '):value??'';
  }
  if(formElement.id==='recruitment-job-form')updateJdMarkdown();
}
function updateJdMarkdown() {
  const value=$('recruitment-jd').value,preview=$('recruitment-jd-markdown');
  preview.replaceChildren(markdown(formatJd(value)));preview.hidden=!value.trim();
  if(value.trim())preview.closest('details').open=true;
}
function tags(skills) {const group=node('div',undefined,'recruitment-tags');skills.forEach(skill=>group.append(rawNode('span',skill)));return group;}
function jobCopy(job) {
  const row=node('article',undefined,'recruitment-job');
  row.append(rawNode('h4',job.title),rawNode('strong',job.company),node('p',job.role+' · '+modes[job.workMode]+' · '+job.location,'muted'),node('p',salaryText(job),'recruitment-salary'),node('p','Kinh nghiệm từ '+job.minExperience+' năm'),node('small','Kỹ năng bắt buộc'),tags(job.requiredSkills));
  const details=node('details',undefined,'recruitment-description');details.append(node('summary','Mô tả công việc'),markdown(formatJd(job.description??job.formattedDescription)));row.append(details);
  if(job.preferredSkills.length)row.append(node('small','Ưu tiên'),tags(job.preferredSkills));return row;
}
function candidateCopy(candidate,job,assessment) {
  const card=node('article',undefined,'recruitment-candidate');
  const header=node('div',undefined,'candidate-header'),identity=node('div',undefined,'candidate-identity');
  const initials=candidate.displayName.trim().split(/\s+/).slice(-2).map(part=>Array.from(part)[0]).join('').toLocaleUpperCase(locale());
  const avatar=node('span',initials||'UV','candidate-avatar');avatar.setAttribute('aria-hidden','true');
  const heading=rawNode(job?'h4':'h5',candidate.displayName,'candidate-name');heading.tabIndex=-1;
  identity.append(heading,rawNode('p',candidate.desiredRole,'candidate-role'));
  header.append(avatar,identity);if(job)header.append(node('span',assessment?assessment.score+'/100 · Phù hợp':'Hồ sơ ứng viên','pill candidate-badge'));card.append(header);
  const facts=node('dl',undefined,'candidate-facts');
  for(const [label,value] of [['Kinh nghiệm',candidate.experienceYears+' năm'],['Địa điểm',candidate.location],['Hình thức',modes[candidate.workMode]],['Lương mong muốn',salaryText(candidate)]]) {
    const fact=node('div');fact.append(node('dt',label),(label==='Địa điểm'?rawNode:node)('dd',value));facts.append(fact);
  }
  const body=node('div',undefined,'candidate-body'),profile=node('section',undefined,'candidate-profile');
  profile.append(node(job?'h5':'h6','Giới thiệu kinh nghiệm'));
  const summary=candidate.summary?.trim()||'Ứng viên chưa thêm phần giới thiệu.';
  if(summary.length>450){const details=node('details',undefined,'candidate-summary');details.append(node('summary','Đọc giới thiệu kinh nghiệm'),rawNode('p',summary,'preserve'));profile.append(details);}
  else profile.append((candidate.summary?.trim()?rawNode:node)('p',summary,'preserve'));
  profile.append(node(job?'h5':'h6','Kỹ năng'),tags(candidate.skills));
  if(job) {
    const required=new Set(job.requiredSkills.map(skill=>skill.trim().toLowerCase()));
    [...profile.querySelectorAll('.recruitment-tags span')].forEach(tag=>{if(required.has(tag.textContent.trim().toLowerCase())){tag.classList.add('candidate-skill-required');tag.append(node('small',' · Bắt buộc'));}});
  }
  body.append(profile);card.append(facts,body);return card;
}
async function loadJobs() {
  const jobs=await api(base+'/jobs');$('recruitment-jobs').replaceChildren();
  $('recruitment-job-count').textContent=jobs.length+' bài đăng';
  if(!jobs.length)$('recruitment-jobs').append(node('p','Chưa có bài tuyển dụng. Điền JD để bắt đầu khám phá ứng viên.','muted'));
  for(const job of jobs) {
    const row=jobCopy(job),buttons=node('div',undefined,'actions'),suggestionHost=node('div',undefined,'suggestion-host');
    row.prepend(node('span',job.open?'Đang tuyển':'Đã đóng','pill'));
    buttons.append(action('Sửa JD',async()=>{switchView('hiring');invalidateJd();fill($('recruitment-job-form'),job);$('job-form-title').textContent='Sửa bài tuyển dụng';$('recruitment-jd-save').textContent='Lưu thay đổi';$('recruitment-job-form').scrollIntoView({block:'center'});$('recruitment-jd').focus({preventScroll:true});await parseJd();}),action(job.open?'Đóng bài':'Mở lại',async()=>{await api(base+'/jobs/'+job.id,'PATCH',{open:!job.open});await Promise.all([loadJobs(),loadFeed(),refreshRecruitment()]);}));
    if(job.open)buttons.append(action('Match · Tìm ứng viên',()=>discover(job,suggestionHost),''));
    row.append(buttons,suggestionHost);$('recruitment-jobs').append(row);
  }
}
async function discover(job,host,previous) {
  if(host.dataset.loading==='true')return;
  host.dataset.loading='true';
  const mJob=host.closest('.recruitment-job');if(mJob){const grid=mJob.closest('#recruitment-jobs');if(grid)[...grid.querySelectorAll(':scope>.recruitment-job')].forEach(j=>{if(j===mJob){j.classList.add('is-matching');j.classList.remove('is-dimmed');}else{j.classList.add('is-dimmed');j.classList.remove('is-matching');const sh=j.querySelector('.suggestion-host');if(sh)sh.replaceChildren();}});mJob.scrollIntoView({block:'nearest',behavior:'smooth'});}
  const previousButtons=[...host.querySelectorAll('button')];previousButtons.forEach(button=>button.disabled=true);
  let status=host.querySelector('.candidate-status');
  if(!status){status=node('p',undefined,'candidate-status');status.setAttribute('role','status');host.prepend(status);}
  status.textContent='Đang tìm ứng viên phù hợp…';
  host.setAttribute('aria-busy','true');
  try {
    const result=await api(base+'/jobs/'+job.id+(previous?'/next/'+previous:'/match'),'POST');host.replaceChildren(status);
    if(!result.suggestion){status.textContent='Không còn ứng viên phù hợp chưa xem.';const empty=node('div',undefined,'candidate-empty'),heading=node('h4','Bạn đã xem hết ứng viên phù hợp');heading.tabIndex=-1;empty.append(heading,node('p','Thử lại khi có hồ sơ mới hoặc điều chỉnh JD để mở rộng tiêu chí.','muted'),action('Tìm lại ứng viên',()=>discover(job,host)));host.append(empty);heading.focus({preventScroll:true});return;}
    const suggestion=result.suggestion,card=candidateCopy(suggestion.candidate,job,suggestion.assessment),reasons=node('ul',undefined,'candidate-reasons');
    status.textContent='Đã tìm thấy ứng viên có điểm phù hợp cao cho '+job.title+'.';
    const fit=node('section',undefined,'candidate-fit');fit.append(node('h5','Vì sao phù hợp'),node('p','Đối chiếu với '+job.title,'muted'));
    const assessment=suggestion.assessment;
    if(assessment) {
      const meter=node('meter');meter.min=0;meter.max=100;meter.value=assessment.score;
      meter.setAttribute('aria-label','Điểm phù hợp '+assessment.score+' trên 100');
      fit.append(node('p',assessment.score+'/100 · '+(assessment.source==='rules+groq'?'Điểm tiêu chí kết hợp đánh giá AI':'Điểm theo tiêu chí hồ sơ'),'candidate-score'),meter);
      const details=node('details'),breakdown=node('dl',undefined,'recruitment-extracted');
      details.append(node('summary','Xem điểm theo tiêu chí'));
      const labels={role:'Vị trí /25',requiredSkills:'Kỹ năng bắt buộc /40',experience:'Kinh nghiệm /10',workMode:'Chế độ làm việc /10',location:'Địa điểm /5',salary:'Lương /5',preferredSkills:'Kỹ năng ưu tiên /5'};
      for(const [key,value] of Object.entries(assessment.components))breakdown.append(node('dt',labels[key]??key),node('dd',String(value)));
      details.append(breakdown,node('p','Điểm tiêu chí: '+assessment.ruleScore+'/100'+(assessment.llmScore==null?'':'; AI: '+assessment.llmScore+'/100'),'muted'));fit.append(details);
    }
    suggestion.reasons.forEach(reason=>reasons.append(node('li',reason)));fit.append(reasons);
    if(assessment?.gaps?.length){const gaps=node('ul',undefined,'candidate-gaps');assessment.gaps.forEach(gap=>gaps.append(node('li',gap)));fit.append(node('h5','Cần trao đổi thêm'),gaps);}
    fit.append(node('p','Điểm phản ánh thông tin đã khai báo, không phải xác nhận năng lực.','muted'));card.querySelector('.candidate-body').append(fit);
    const footer=node('footer',undefined,'candidate-footer'),buttons=node('div',undefined,'actions');
    buttons.append(action('Gửi lời mời kết nối',async()=>{
      if(host.dataset.loading==='true')return;host.dataset.loading='true';buttons.querySelectorAll('button').forEach(button=>button.disabled=true);
      status.textContent='Đang gửi lời mời…';
      try {
        await api(base+'/matches/'+suggestion.matchId+'/invite','POST');
        status.textContent='Đã gửi lời mời tới '+suggestion.candidate.displayName+'.';
        const sent=node('div',undefined,'candidate-empty'),heading=node('h4','Lời mời đã được gửi');heading.tabIndex=-1;sent.append(heading,node('p','Theo dõi phản hồi trong mục Match. Bạn có thể tiếp tục khám phá ứng viên.','muted'),action('Xem lời mời',()=>{switchView('matches');$('recruitment-connections').scrollIntoView({block:'start'});}),action('Tìm ứng viên tiếp theo',()=>discover(job,host),''));host.replaceChildren(status,sent);heading.focus({preventScroll:true});
        await refreshRecruitment();notice('Đã gửi lời mời tới ứng viên.');
      }catch(error){status.textContent=error.message;throw error;}
      finally{host.dataset.loading='false';buttons.querySelectorAll('button').forEach(button=>button.disabled=false);}
    },''),action('Xem ứng viên tiếp theo',()=>discover(job,host,suggestion.matchId)));
    footer.append(node('p','Thông tin liên hệ được chia sẻ khi ứng viên đồng ý lời mời.','candidate-privacy'),buttons);
    card.append(footer);host.append(card);card.querySelector('.candidate-name').focus({preventScroll:true});
  }catch(error){status.textContent=error.message;throw error;}
  finally{host.dataset.loading='false';host.setAttribute('aria-busy','false');previousButtons.forEach(button=>button.disabled=false);}
}
async function loadFeed() {
  const jobs=await api(base+'/feed?page='+feedPage);$('recruitment-feed').replaceChildren();
  if(!jobs.length)$('recruitment-feed').append(node('p','Chưa có cơ hội đang tuyển ở trang này.','muted'));
  for(const job of jobs)$('recruitment-feed').append(jobCopy(job));
  $('recruitment-feed-page').textContent='Trang '+(feedPage+1);$('recruitment-feed-prev').disabled=feedPage===0;$('recruitment-feed-next').disabled=jobs.length<20;
}
async function loadMatches() {
  const matches=await api(base+'/matches'),signature=JSON.stringify(matches);if(signature===matchesSignature)return;matchesSignature=signature;
  $('recruitment-matches').replaceChildren();
  $('recruitment-match-count').textContent=matches.length;
  if(!matches.length)$('recruitment-matches').append(node('p','Lời mời và các kết nối mới sẽ xuất hiện tại đây.','muted'));
  for(const match of matches) {
    const recruiter=match.job.ownerId===viewer.id,row=node('article',undefined,'recruitment-match');
    row.dataset.matchStatus=match.status;
    row.append(node('span',statuses[match.status],'pill'),rawNode('h4',match.job.title+' · '+match.job.company),node('p',recruiter?'Ứng viên: '+match.candidate.displayName:'Người tuyển dụng: '+match.job.recruiterName));
    if(recruiter)row.append(candidateCopy(match.candidate));
    else {const details=node('details');details.append(node('summary','Xem JD và mức lương'),jobCopy(match.job));row.append(details);}
    const buttons=node('div',undefined,'actions');
    if(!recruiter&&match.status==='PENDING') {
      buttons.append(action('Đồng ý match',async()=>{await api(base+'/matches/'+match.id,'PATCH',{status:'ACCEPTED'});await refreshRecruitment();notice('Đã match. Hai bên có thể liên hệ và đặt lịch.');},''),action('Từ chối',async()=>{await api(base+'/matches/'+match.id,'PATCH',{status:'DECLINED'});await refreshRecruitment();}));
    }
    if(match.status==='ACCEPTED') {
      const email=rawNode('a',match.contactEmail);email.href='mailto:'+match.contactEmail;row.append(email);
      buttons.append(action('Trò chuyện & lịch phỏng vấn',()=>openConversation(match),''));
    }
    row.append(buttons);$('recruitment-matches').append(row);
  }
  filterMatches();
}
async function openConversation(match) {
  selectedMatch=match;threadVersion++;messageSnapshot=[];messageLoaded=false;interviewSignature='';forceMessageScroll=true;switchView('matches');
  const recruiter=match.job.ownerId===viewer.id,name=recruiter?match.candidate.displayName:match.job.recruiterName;
  $('recruitment-conversation-title').textContent=name;
  $('recruitment-conversation-context').textContent=match.job.title+' · '+match.job.company;
  $('recruitment-chat-avatar').textContent=name.trim().split(/\s+/).slice(-2).map(part=>Array.from(part)[0]).join('').toLocaleUpperCase(locale());
  $('recruitment-chat-email').textContent=match.contactEmail??'';
  $('recruitment-chat-email').href=match.contactEmail?'mailto:'+match.contactEmail:'';
  $('recruitment-interview-form').hidden=!recruiter;
  $('recruitment-interview-compose').hidden=!recruiter;$('recruitment-interview-compose').open=false;
  $('recruitment-schedule-panel').open=!window.matchMedia('(max-width: 960px)').matches;
  $('recruitment-message-form').reset();$('recruitment-interview-form').reset();
  $('recruitment-messages').replaceChildren(node('p','Đang tải trò chuyện…','muted'));
  $('recruitment-conversation').scrollIntoView({block:'start'});
  $('recruitment-conversation-title').focus({preventScroll:true});
  try {await loadThread();}catch(error){if(selectedMatch?.id===match.id)$('recruitment-messages').replaceChildren(node('p',error.message,'muted'));throw error;}
}
async function loadThread() {
  if(!selectedMatch)return;const match=selectedMatch,version=++threadVersion;
  const [messages,interviews]=await Promise.all([api(base+'/matches/'+match.id+'/messages'),api(base+'/matches/'+match.id+'/interviews')]);
  if(!selectedMatch||selectedMatch.id!==match.id||version!==threadVersion)return;
  const list=$('recruitment-messages'),changed=JSON.stringify(messages)!==JSON.stringify(messageSnapshot);
  if(changed||!messageLoaded) {
    const stick=forceMessageScroll||messageSnapshot.length===0||list.scrollHeight-list.scrollTop-list.clientHeight<64;
    const previousScroll=list.scrollTop,appendOnly=messageSnapshot.length>0&&messages.length>=messageSnapshot.length&&JSON.stringify(messages.slice(0,messageSnapshot.length))===JSON.stringify(messageSnapshot);
    if(!appendOnly)list.replaceChildren();
    if(!messages.length)list.append(node('p','Bắt đầu trao đổi về công việc và lịch phỏng vấn.','muted'));
    let lastDay=appendOnly?new Date(messageSnapshot.at(-1).createdAt).toLocaleDateString(locale()):null;
    for(const message of messages.slice(appendOnly?messageSnapshot.length:0)) {
      const date=new Date(message.createdAt),day=date.toLocaleDateString(locale());
      if(day!==lastDay){list.append(node('p',day,'chat-day'));lastDay=day;}
      const mine=message.senderId===viewer.id,row=node('article',undefined,'recruitment-message'+(mine?' mine':''));
      const meta=node('div',undefined,'chat-message-meta'),time=node('time',date.toLocaleTimeString(locale(),{hour:'2-digit',minute:'2-digit'}));time.dateTime=message.createdAt;
      meta.append(mine?node('strong','Bạn'):rawNode('strong',message.senderName),time);
      row.append(meta,rawNode('p',message.body,'preserve'));list.append(row);
    }
    messageSnapshot=messages;messageLoaded=true;list.scrollTop=stick?list.scrollHeight:previousScroll;forceMessageScroll=false;
  }
  const signature=JSON.stringify(interviews);if(signature===interviewSignature)return;interviewSignature=signature;
  const host=$('recruitment-interviews');host.replaceChildren();$('recruitment-interview-count').textContent=String(interviews.length);
  if(!interviews.length)host.append(node('p','Chưa có lịch phỏng vấn.','chat-help muted'));
  for(const interview of interviews) {
    const row=node('article',undefined,'chat-interview'),start=new Date(interview.startsAt),end=new Date(interview.endsAt);
    const time=node('time',start.toLocaleDateString(locale())+' · '+start.toLocaleTimeString(locale(),{hour:'2-digit',minute:'2-digit'})+' – '+end.toLocaleTimeString(locale(),{hour:'2-digit',minute:'2-digit'}));time.dateTime=interview.startsAt;
    row.append(node('span',interviewStatuses[interview.status],'pill'),time);
    if(interview.meetingDetails.length>120){const details=node('details');details.append(node('summary','Địa điểm / link cuộc họp'),rawNode('p',interview.meetingDetails,'preserve'));row.append(details);}
    else row.append(rawNode('p',interview.meetingDetails,'preserve'));
    const buttons=node('div',undefined,'actions');const change=async status=>{await api(base+'/matches/'+match.id+'/interviews/'+interview.id,'PATCH',{status});await loadThread();};
    if(interview.status==='PENDING'&&match.candidate.ownerId===viewer.id)buttons.append(action('Xác nhận',()=>change('CONFIRMED'),''),action('Từ chối',()=>change('DECLINED')));
    if(['PENDING','CONFIRMED'].includes(interview.status))buttons.append(action('Hủy lịch',()=>change('CANCELLED'),'secondary'));
    if(buttons.childElementCount)row.append(buttons);host.append(row);
  }
}
function init() {
  if(initialized)return;initialized=true;
  document.querySelectorAll('[data-recruitment-view]').forEach(button=>button.addEventListener('click',()=>switchView(button.dataset.recruitmentView)));
  document.querySelectorAll('[data-match-filter]').forEach(button=>button.addEventListener('click',()=>{matchFilter=button.dataset.matchFilter;document.querySelectorAll('[data-match-filter]').forEach(item=>item.setAttribute('aria-pressed',String(item===button)));filterMatches();}));
  switchView(activeView);
  form('candidate-form',async(data,element)=>{await api(base+'/candidate','PUT',{seeking:element.elements.seeking.checked,desiredRole:data.desiredRole,summary:data.summary,experienceYears:Number(data.experienceYears),location:data.location,workMode:data.workMode,salaryMin:Number(data.salaryMin),salaryMax:Number(data.salaryMax),skills:splitSkills(data.skills)});notice('Đã lưu hồ sơ tìm việc.');});
  $('recruitment-job-form').addEventListener('submit',async event=>{
    event.preventDefault();const element=event.target,data=Object.fromEntries(new FormData(element));
    await busy($('recruitment-jd-save'),async()=>{if(!parsedDescription||parsedDescription!==data.description)throw new Error('Vui lòng chờ JD được phân tích trước khi đăng.');await api(base+'/jobs'+(data.id?'/'+data.id:'')+'/from-jd',data.id?'PUT':'POST',{description:data.description,extractionId});element.reset();await Promise.all([loadJobs(),loadFeed()]);notice('Đã lưu bài tuyển dụng từ JD.');});
    $('recruitment-jd-save').disabled=!parsedDescription||parsedDescription!==element.elements.description.value;
  });
  $('recruitment-jd').addEventListener('input',()=>{updateJdMarkdown();invalidateJd();$('recruitment-jd-status').textContent='Chờ phân tích nội dung JD…';jdTimer=setTimeout(parseJd,1500);});
  $('recruitment-jd-retry').addEventListener('click',()=>{invalidateJd();parseJd();});
  $('recruitment-job-form').addEventListener('reset',()=>{invalidateJd();$('recruitment-jd-markdown').replaceChildren();$('recruitment-jd-markdown').hidden=true;$('recruitment-job-form').elements.id.value='';$('job-form-title').textContent='Đăng bài tuyển dụng';$('recruitment-jd-save').textContent='Đăng bài tuyển dụng';$('recruitment-jd-status').textContent='Thông tin được tự động phân tích khi bạn dán hoặc sửa JD.';});
  $('recruitment-refresh').onclick=()=>busy($('recruitment-refresh'),()=>Promise.all([loadJobs(),loadFeed(),refreshRecruitment()]));
  $('recruitment-feed-prev').onclick=()=>busy($('recruitment-feed-prev'),async()=>{feedPage=Math.max(0,feedPage-1);await loadFeed();});
  $('recruitment-feed-next').onclick=()=>busy($('recruitment-feed-next'),async()=>{feedPage++;await loadFeed();});
  $('recruitment-close-chat').onclick=()=>{selectedMatch=null;threadVersion++;switchView('matches');$('recruitment-connections').scrollIntoView({block:'start'});const heading=$('recruitment-connections').querySelector('h3');heading.tabIndex=-1;heading.focus({preventScroll:true});};
  form('recruitment-message-form',async(data,element)=>{if(!selectedMatch)return;await api(base+'/matches/'+selectedMatch.id+'/messages','POST',{body:data.body});element.reset();forceMessageScroll=true;await loadThread();});
  form('recruitment-interview-form',async(data,element)=>{if(!selectedMatch)return;await api(base+'/matches/'+selectedMatch.id+'/interviews','POST',{startsAt:new Date(data.startsAt).toISOString(),durationMinutes:Number(data.durationMinutes),meetingDetails:data.meetingDetails});element.reset();$('recruitment-interview-compose').open=false;$('recruitment-schedule-panel').open=true;await loadThread();notice('Đã đề xuất lịch phỏng vấn.');});
}
export async function loadRecruitment(me) {
  if(viewer?.id!==me.id){matchesSignature='';selectedMatch=null;feedPage=0;$('recruitment-conversation').hidden=true;$('candidate-form').reset();$('recruitment-job-form').reset();}
  viewer=me;init();const candidate=await api(base+'/candidate');if(candidate)fill($('candidate-form'),candidate);
  await Promise.all([loadJobs(),loadFeed(),refreshRecruitment()]);
}
export async function refreshRecruitment() {if(!viewer)return;await Promise.all([loadMatches(),loadThread()]);}
