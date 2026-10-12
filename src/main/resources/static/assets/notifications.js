import {api, node} from './common.js';

let panel, badge, connection, reconnectTimer, pollingTimer, page=0, unreadOnly=false;
let refreshBusinessData, lastIds=null, active=false, requestVersion=0, unreadCount=0, lastDraw='';
const paths = {
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4',
  inbox: 'M3 5h18v14H3z M3 5l9 7 9-7',
  bookings: 'M4 6h16v15H4z M8 3v6 M16 3v6 M4 11h16',
  recruitment: 'M4 7h16v14H4z M9 7V3h6v4 M4 12h16',
  account: 'M12 3l8 3v6c0 5-8 9-8 9s-8-4-8-9V6z M8 12l3 3 5-6',
  close: 'M6 6l12 12 M18 6L6 18',
  check: 'M5 12l4 4L19 6'
};
function icon(name) {
  const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
  svg.setAttribute('viewBox','0 0 24 24'); svg.setAttribute('aria-hidden','true');
  const path=document.createElementNS(svg.namespaceURI,'path'); path.setAttribute('d',paths[name]||paths.bell);
  svg.append(path); return svg;
}
function showError(error) {
  const message=panel.querySelector('#notifications-error');
  message.textContent=error?.message || 'Chưa tải được thông báo. Bạn hãy thử làm mới.';
  message.hidden=false;
}
function control(text, handler, className='secondary') {
  const button=node('button',text,className); button.type='button';
  button.addEventListener('click',async()=>{
    button.disabled=true;
    try { await handler(); } catch(error) { showError(error); }
    finally { button.disabled=button.id==='notifications-read-all' && unreadCount===0; }
  });
  return button;
}
function closePanel() { if(panel?.open)panel.close(); }
function openPanel() {
  if(!active)return;
  panel.showModal(); document.body.classList.add('notifications-open');
  badge.setAttribute('aria-expanded','true');
  reload().catch(showError);
}
export async function startNotifications(refresh) {
  refreshBusinessData=refresh;
  if(active)return;
  active=true;
  if(!panel) {
    badge=node('button',undefined,'secondary notification-trigger'); badge.type='button';
    badge.append(icon('bell'),node('span','Thông báo'),node('span','0','notification-badge'));
    badge.lastElementChild.hidden=true;
    badge.setAttribute('aria-controls','notifications-panel'); badge.setAttribute('aria-expanded','false');
    badge.setAttribute('aria-haspopup','dialog');
    badge.addEventListener('click',openPanel);
    document.querySelector('.header-actions').prepend(badge);
    panel=node('dialog',undefined,'notifications-panel'); panel.id='notifications-panel';
    panel.setAttribute('aria-labelledby','notifications-title');
    // Only fixed application markup is inserted as HTML; notification content uses textContent.
    panel.innerHTML=`<div class="notifications-header"><div><p class="eyebrow">KẾT NỐI CỦA BẠN</p><h2 id="notifications-title">Thông báo</h2><p id="notifications-count" role="status" aria-live="polite" aria-atomic="true">Yêu cầu, lịch hẹn và tài khoản.</p></div><button type="button" id="notifications-close" class="secondary notification-close" aria-label="Đóng thông báo" autofocus></button></div>
      <div class="notifications-toolbar"><div class="notification-filters" role="group" aria-label="Lọc thông báo"><button type="button" id="notifications-all" class="secondary" aria-pressed="true">Tất cả</button><button type="button" id="notifications-unread" class="secondary" aria-pressed="false">Chưa đọc</button></div><div id="notifications-tools" class="notifications-tools"></div></div>
      <p id="notifications-error" class="notifications-error" role="alert" hidden></p>
      <div id="notifications-list" aria-busy="true"><div class="notification-empty"><p role="status">Đang tải thông báo…</p></div></div>
      <div class="notifications-footer"><span id="notifications-connection" class="muted" role="status">Đang kết nối…</span><nav id="notifications-pagination" aria-label="Phân trang thông báo"><button id="notifications-prev" type="button" class="secondary" disabled>← Trước</button><span id="notifications-page" class="muted" role="status"></span><button id="notifications-next" type="button" class="secondary" disabled>Sau →</button></nav></div>`;
    document.body.append(panel);
    const close=panel.querySelector('#notifications-close'); close.append(icon('close')); close.onclick=closePanel;
    panel.addEventListener('close',()=>{document.body.classList.remove('notifications-open');badge.setAttribute('aria-expanded','false');});
    panel.addEventListener('click',event=>{
      if(event.target!==panel)return;
      const bounds=panel.getBoundingClientRect();
      if(event.clientX<bounds.left||event.clientX>bounds.right||event.clientY<bounds.top||event.clientY>bounds.bottom)closePanel();
    });
    const refreshButton=control('Làm mới',()=>reload()); refreshButton.id='notifications-refresh';
    const readAll=control('Đọc tất cả',async()=>{await api('/me/notifications/read-all','PATCH');await reload();});
    readAll.id='notifications-read-all'; readAll.disabled=true; readAll.prepend(icon('check'));
    panel.querySelector('#notifications-tools').append(refreshButton,readAll);
    for(const [id,filter] of [['notifications-all',false],['notifications-unread',true]]) {
      panel.querySelector('#'+id).onclick=()=>{
        if(unreadOnly===filter)return;
        unreadOnly=filter;page=0;lastDraw='';
        panel.querySelector('#notifications-list').replaceChildren(node('p','Đang tải thông báo…','notification-empty muted'));
        panel.querySelector('#notifications-all').setAttribute('aria-pressed',String(!filter));
        panel.querySelector('#notifications-unread').setAttribute('aria-pressed',String(filter));
        reload().catch(showError);
      };
    }
    panel.querySelector('#notifications-prev').onclick=()=>{page=Math.max(0,page-1);reload().catch(showError);};
    panel.querySelector('#notifications-next').onclick=()=>{page++;reload().catch(showError);};
    window.addEventListener('pagehide',stopNotifications);
    window.addEventListener('pageshow',event=>{if(event.persisted&&!active)startNotifications(refreshBusinessData).catch(()=>{});});
    window.addEventListener('account-session-reset',stopNotifications);
  }
  badge.hidden=false;
  try {await reload();}catch(error){showError(error);setConnection('Đang kết nối lại…');}
  connect();
}
function setConnection(text){panel.querySelector('#notifications-connection').textContent=text;}
function draw(data) {
  unreadCount=data.unread;
  const counter=badge.querySelector('.notification-badge');
  counter.textContent=data.unread>99?'99+':String(data.unread); counter.hidden=!data.unread;
  badge.setAttribute('aria-label',data.unread?`Thông báo, ${data.unread} chưa đọc`:'Thông báo, không có thông báo chưa đọc');
  const count=panel.querySelector('#notifications-count');
  const summary=data.unread?`${data.unread} thông báo chưa đọc`:'Bạn đã đọc hết thông báo';
  if(count.textContent!==summary)count.textContent=summary;
  panel.querySelector('#notifications-read-all').disabled=!data.unread;
  panel.querySelector('#notifications-page').textContent=`${page+1} / ${Math.max(1,Math.ceil(data.total/20))}`;
  panel.querySelector('#notifications-prev').disabled=page===0;
  panel.querySelector('#notifications-next').disabled=(page+1)*20>=data.total;
  panel.querySelector('#notifications-pagination').hidden=data.total<=20 && page===0;
  const signature=JSON.stringify([page,unreadOnly,data.items]);
  if(lastDraw===signature)return;
  lastDraw=signature;
  const list=panel.querySelector('#notifications-list');
  const focused=document.activeElement;
  const focusRow=list.contains(focused)?focused.closest('.notification-item'):null;
  const focusId=focusRow?.dataset.id;
  const focusIndex=focusRow?Array.from(list.children).indexOf(focusRow):-1;
  list.replaceChildren();
  if(!data.items.length) {
    const empty=node('div',undefined,'notification-empty');
    empty.append(icon(unreadOnly?'check':'bell'),node('h3',unreadOnly?'Bạn đã xem hết rồi!':'Chưa có thông báo'),node('p',unreadOnly?'Những cập nhật mới sẽ xuất hiện ở đây.':'Khi có yêu cầu, lịch hẹn hoặc cập nhật tài khoản, bạn sẽ thấy tại đây.','muted'));
    list.append(empty);
  }
  for(const item of data.items) {
    const category=item.target==='inbox'?'inbox':item.target==='account'?'account':item.target==='recruitment'?'recruitment':'bookings';
    const label={inbox:'Hộp thư',account:'Tài khoản',bookings:'Lịch hẹn',recruitment:'Tuyển dụng'}[category];
    const row=node('article',undefined,'notification-item'+(item.readAt?'':' unread')); row.dataset.id=item.id;
    const symbol=node('div',undefined,'notification-symbol'); symbol.append(icon(category));
    const content=node('div',undefined,'notification-content');
    const meta=node('div',undefined,'notification-meta');
    meta.append(node('span',label,'notification-category'));
    const time=node('time',new Date(item.createdAt).toLocaleString('vi-VN',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'})); time.dateTime=item.createdAt;
    meta.append(time); if(!item.readAt)meta.append(node('span','Chưa đọc','notification-unread-label'));
    content.append(meta,node('h3',item.title),node('p',item.body,'preserve'));
    const buttons=node('div',undefined,'notification-actions');
    buttons.append(control('Mở '+label.toLowerCase()+' →',async()=>{
      await api(`/me/notifications/${item.id}/read`,'PATCH');
      // Navigation remains available even if a subsequent background refresh fails.
      closePanel();
      window.dispatchEvent(new CustomEvent('linkhub:open-workspace',{detail:category}));
      const target=document.getElementById(category==='inbox'?'inbox':category==='account'?'account':category==='recruitment'?'recruitment-matches':'bookings-list');
      if(target){target.tabIndex=-1;target.focus({preventScroll:true});target.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'center'});}
      reload().catch(()=>setConnection('Chưa cập nhật được. Hãy thử làm mới.'));
    },'secondary notification-open'));
    if(!item.readAt)buttons.append(control('Đánh dấu đã đọc',async()=>{await api(`/me/notifications/${item.id}/read`,'PATCH');await reload();},'secondary notification-mark'));
    content.append(buttons); row.append(symbol,content); list.append(row);
  }
  // Refreshes must not strand keyboard focus on a removed action button.
  if(panel.open && focusIndex>=0) {
    const rows=Array.from(list.querySelectorAll('.notification-item'));
    const replacement=rows.find(row=>row.dataset.id===focusId)||rows[Math.min(focusIndex,rows.length-1)];
    (replacement?.querySelector('button')||panel.querySelector(unreadOnly?'#notifications-unread':'#notifications-all')).focus({preventScroll:true});
  }
}
async function reload() {
  const version=++requestVersion;
  const list=panel.querySelector('#notifications-list'); list.setAttribute('aria-busy','true');
  try {
    const data=await api(`/me/notifications?page=${page}&unreadOnly=${unreadOnly}`);
    if(!active||version!==requestVersion)return;
    if(page>0&&!data.items.length){page--;return reload();}
    panel.querySelector('#notifications-error').hidden=true;
    draw(data);
  } catch(error) {
    if(!active || version!==requestVersion)return;
    if(!lastDraw)list.replaceChildren(node('p','Chưa tải được danh sách. Bấm Làm mới để thử lại.','notification-empty muted'));
    throw error;
  } finally { if(version===requestVersion)list.setAttribute('aria-busy','false'); }
}
function connect() {
  if(!active)return;
  if(!window.EventSource){setConnection('Cập nhật mỗi 15 giây');pollingTimer=setInterval(()=>Promise.all([reload(),refreshBusinessData?.()]).catch(()=>{}),15000);return;}
  connection=new EventSource('/api/me/notifications/stream');
  connection.onopen=()=>setConnection('Đang cập nhật trực tiếp');
  connection.addEventListener('notifications',event=>{
    if(!active)return;
    const data=JSON.parse(event.data),ids=data.total+':'+data.items.map(item=>item.id).join(',');
    if(lastIds!==ids)Promise.resolve(refreshBusinessData?.()).catch(()=>{});
    lastIds=ids;
    // REST reload respects the user's current page/filter, including old unread items.
    reload().catch(()=>setConnection('Đang kết nối lại…'));
  });
  connection.onerror=()=>{
    connection?.close();connection=null;setConnection('Đang kết nối lại…');
    clearTimeout(reconnectTimer);
    reconnectTimer=setTimeout(async()=>{
      try {
        // Reauthenticate on reconnect; do not keep an expired session's stream alive.
        const response=await fetch('/api/me',{credentials:'same-origin'});
        if(response.status===401){stopNotifications();setConnection('Phiên đã hết hạn. Vui lòng đăng nhập lại.');return;}
      }catch{/* The next connection will retry after an outage. */}
      connect();
    },5000);
  };
}
export function stopNotifications(){
  active=false;requestVersion++;connection?.close();connection=null;
  clearTimeout(reconnectTimer);clearInterval(pollingTimer);lastIds=null;
  closePanel();if(badge)badge.hidden=true;
}
