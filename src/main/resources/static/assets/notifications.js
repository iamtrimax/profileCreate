import {api, node, action, notice} from './common.js';

let panel, badge, connection, reconnectTimer, pollingTimer, page=0, unreadOnly=false;
let refreshBusinessData, lastIds=null, active=false, requestVersion=0;

export async function startNotifications(refresh) {
  refreshBusinessData=refresh;
  if(active)return;
  active=true;
  if(!panel) {
    badge=action('Thông báo',()=>{panel.hidden=!panel.hidden;if(!panel.hidden)reload().catch(e=>notice(e.message,true));});
    badge.setAttribute('aria-controls','notifications-panel');badge.setAttribute('aria-expanded','false');
    badge.addEventListener('click',()=>badge.setAttribute('aria-expanded',String(!panel.hidden)));
    document.querySelector('.header-actions').prepend(badge);
    panel=node('section',undefined,'card notifications-panel');panel.id='notifications-panel';panel.hidden=true;
    panel.innerHTML=`<div class="heading"><h2>Thông báo <span id="notifications-count"></span></h2><div class="actions"><span id="notifications-connection" class="muted" role="status"></span><button type="button" id="notifications-refresh" class="secondary">Làm mới</button><button type="button" id="notifications-read-all" class="secondary">Đọc tất cả</button></div></div>
      <label class="check"><input type="checkbox" id="notifications-unread"> Chỉ hiện chưa đọc</label>
      <div id="notifications-list"></div><div class="actions"><button id="notifications-prev" type="button" class="secondary">← Trước</button><span id="notifications-page"></span><button id="notifications-next" type="button" class="secondary">Sau →</button></div>`;
    const reminders=document.getElementById('upcoming-reminders');
    if(reminders)reminders.after(panel);else document.querySelector('#dashboard > .heading').after(panel);
    panel.querySelector('#notifications-refresh').onclick=()=>reload().catch(e=>notice(e.message,true));
    panel.querySelector('#notifications-unread').onchange=event=>{unreadOnly=event.target.checked;page=0;reload().catch(e=>notice(e.message,true));};
    panel.querySelector('#notifications-read-all').onclick=async()=>{try{await api('/me/notifications/read-all','PATCH');await reload();}catch(e){notice(e.message,true);}};
    panel.querySelector('#notifications-prev').onclick=()=>{page=Math.max(0,page-1);reload().catch(e=>notice(e.message,true));};
    panel.querySelector('#notifications-next').onclick=()=>{page++;reload().catch(e=>notice(e.message,true));};
    window.addEventListener('pagehide',stopNotifications);
    window.addEventListener('pageshow',event=>{if(event.persisted&&!active)startNotifications(refreshBusinessData).catch(()=>{});});
  }
  try {await reload();}catch{setConnection('Đang kết nối lại…');}
  connect();
}
function setConnection(text){panel.querySelector('#notifications-connection').textContent=text;}
function draw(data) {
  badge.textContent=`Thông báo${data.unread?' ('+data.unread+')':''}`;
  panel.querySelector('#notifications-count').textContent=`· ${data.unread} chưa đọc`;
  const list=panel.querySelector('#notifications-list');list.replaceChildren();
  if(!data.items.length)list.append(node('p','Không có thông báo trong trang này.','muted'));
  for(const item of data.items) {
    const row=node('article',undefined,'notification-item'+(item.readAt?'':' unread'));
    row.append(node('h3',item.title),node('small',new Date(item.createdAt).toLocaleString('vi-VN')),node('p',item.body,'preserve'));
    const buttons=node('div',undefined,'actions');
    buttons.append(action('Mở '+(item.target==='inbox'?'hộp thư':item.target==='account'?'tài khoản':'lịch hẹn'),async()=>{
      await api(`/me/notifications/${item.id}/read`,'PATCH');await reload();
      const target=document.getElementById(item.target==='inbox'?'inbox':item.target==='account'?'account':'bookings-list');
      if(target){target.tabIndex=-1;target.focus({preventScroll:true});target.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'center'});}
    }));
    if(!item.readAt)buttons.append(action('Đánh dấu đã đọc',async()=>{await api(`/me/notifications/${item.id}/read`,'PATCH');await reload();}));
    row.append(buttons);list.append(row);
  }
  panel.querySelector('#notifications-page').textContent=`Trang ${page+1}`;
  panel.querySelector('#notifications-prev').disabled=page===0;
  panel.querySelector('#notifications-next').disabled=(page+1)*20>=data.total;
}
async function reload() {
  const version=++requestVersion;
  const data=await api(`/me/notifications?page=${page}&unreadOnly=${unreadOnly}`);
  if(!active||version!==requestVersion)return;
  if(page>0&&!data.items.length){page--;return reload();}
  draw(data);
}
function connect() {
  if(!active)return;
  if(!window.EventSource){setConnection('Cập nhật mỗi 15 giây');pollingTimer=setInterval(()=>Promise.all([reload(),refreshBusinessData?.()]).catch(()=>{}),15000);return;}
  connection=new EventSource('/api/me/notifications/stream');
  connection.onopen=()=>setConnection('Đang nhận thông báo trực tiếp');
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
export function stopNotifications(){active=false;requestVersion++;connection?.close();connection=null;clearTimeout(reconnectTimer);clearInterval(pollingTimer);lastIds=null;}
