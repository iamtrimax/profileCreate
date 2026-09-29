import {api, node, action} from './common.js';

let panel, timer, active=false, version=0;
function openBookings() {
  const target=document.getElementById('bookings-list');
  if(target){target.tabIndex=-1;target.focus({preventScroll:true});target.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'center'});}
}
export async function startReminders() {
  if(!panel) {
    panel=node('section',undefined,'card upcoming-reminders');panel.id='upcoming-reminders';panel.setAttribute('aria-labelledby','upcoming-heading');
    const heading=node('div',undefined,'heading'),title=node('div');
    title.append(node('p','ĐỪNG BỎ LỠ CUỘC HẸN','eyebrow'));
    const h2=node('h2','Lịch hẹn sắp tới');h2.id='upcoming-heading';title.append(h2);heading.append(title,action('Quản lý lịch hẹn',openBookings));
    const summary=node('p','Đang tải lịch hẹn…');summary.id='upcoming-summary';summary.setAttribute('role','status');
    const grid=node('div',undefined,'upcoming-grid');grid.id='upcoming-list';
    panel.append(heading,summary,grid);
    document.querySelector('#dashboard > .heading').after(panel);
    window.addEventListener('linkhub:booking-changed',()=>refreshReminders());
    window.addEventListener('pagehide',stopReminders);
    window.addEventListener('pageshow',event=>{if(event.persisted)startReminders();});
  }
  active=true;
  if(!timer)timer=setInterval(()=>refreshReminders(),30000);
  await refreshReminders();
}
export async function refreshReminders() {
  if(!panel||!active)return;
  const request=++version;
  try {
    const data=await api('/me/bookings/upcoming');
    if(!active||request!==version)return;
    const now=new Date(data.serverTime).getTime();
    panel.querySelector('#upcoming-summary').textContent=(data.total?`${data.total} lịch đã xác nhận đang diễn ra hoặc bắt đầu trong 24 giờ tới.`:'Chưa có lịch đã xác nhận trong 24 giờ tới.')+(data.pending?` Có ${data.pending} lịch sắp tới đang chờ bạn xác nhận.`:'');
    panel.classList.toggle('has-urgent',data.items.some(item=>new Date(item.startsAt).getTime()-now<=3600000));
    const grid=panel.querySelector('#upcoming-list');grid.replaceChildren();
    for(const item of data.items) {
      const remaining=new Date(item.startsAt).getTime()-now;
      const countdown=remaining<=0?'Đang diễn ra':remaining<60000?'Bắt đầu trong dưới 1 phút':remaining<3600000?`Còn ${Math.ceil(remaining/60000)} phút`:`Còn ${Math.floor(remaining/3600000)} giờ ${Math.floor(remaining%3600000/60000)} phút`;
      const card=node('article',undefined,'upcoming-item');
      const format=new Intl.DateTimeFormat('vi-VN',{timeZone:item.timezone,day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',timeZoneName:'shortOffset'});
      card.append(node('strong',countdown,'upcoming-countdown'),node('h3',item.serviceTitle),node('p','Khách: '+item.name),node('p',`${format.format(new Date(item.startsAt))} → ${format.format(new Date(item.endsAt))}`),node('small',item.timezone));
      grid.append(card);
    }
    if(data.total>data.items.length)grid.append(node('p',`Và ${data.total-data.items.length} lịch khác — mở Quản lý lịch hẹn để xem.`,'muted'));
  }catch {
    if(active&&request===version)panel.querySelector('#upcoming-summary').textContent='Chưa tải được lịch hẹn. Hệ thống sẽ thử lại sau ít giây.';
  }
}
export function stopReminders(){active=false;version++;clearInterval(timer);timer=null;}
