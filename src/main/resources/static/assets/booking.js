import {api, node, action, form, notice} from './common.js';

const statusNames = {PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', CANCELLED: 'Đã hủy', COMPLETED: 'Hoàn tất'};
const timeLabel = (value, timezone) => new Intl.DateTimeFormat('vi-VN', {timeZone: timezone, year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',timeZoneName:'shortOffset'}).format(new Date(value));
const minutes = value => { const [h,m] = value.split(':').map(Number); return h * 60 + m; };
const clock = value => String(Math.floor(value / 60)).padStart(2,'0') + ':' + String(value % 60).padStart(2,'0');
let dashboard;
let bookingPage = 0;

export async function loadBookingDashboard() {
  if (!dashboard) {
    dashboard = node('section', undefined, 'card booking-dashboard');
    // Static markup only. All customer-entered content below uses textContent.
    dashboard.innerHTML = `<h2>Đặt lịch & lịch rảnh</h2>
      <p class="muted">Giờ rảnh theo múi giờ bên dưới. Mỗi lịch chờ xác nhận đã giữ chỗ. Thay đổi lịch rảnh không hủy các lịch đã nhận.</p>
      <div class="booking-management-grid"><form id="availability-form"><label>Múi giờ IANA<input name="timezone" required maxlength="64" placeholder="Asia/Ho_Chi_Minh"></label>
      <h3>Lịch rảnh hằng tuần</h3><div id="availability-rules"></div><button type="button" id="add-rule" class="secondary">+ Khung giờ</button>
      <h3>Ngày nghỉ / giờ ngoại lệ</h3><p class="muted">Ngoại lệ thay thế toàn bộ giờ rảnh của ngày đó. Giờ kết thúc 24:00 là cuối ngày.</p><div id="availability-exceptions"></div><button type="button" id="add-exception" class="secondary">+ Ngày ngoại lệ</button>
      <p><button type="submit">Lưu lịch rảnh</button></p></form>
      <div class="booking-inbox"><div class="heading"><h3>Lịch hẹn đã nhận</h3><button type="button" id="refresh-bookings" class="secondary">Làm mới lịch hẹn</button></div>
      <div id="bookings-list" aria-live="polite"></div><div class="actions"><button type="button" id="bookings-prev" class="secondary">← Trước</button><span id="bookings-page"></span><button type="button" id="bookings-next" class="secondary">Sau →</button></div></div></div>`;
    document.getElementById('dashboard').append(dashboard);
    dashboard.querySelector('#add-rule').onclick = () => addRule({dayOfWeek:1,startMinute:540,endMinute:1020});
    dashboard.querySelector('#add-exception').onclick = () => addException({date:'',closed:true});
    dashboard.querySelector('#refresh-bookings').onclick = () => loadBookings().catch(e=>notice(e.message,true));
    dashboard.querySelector('#bookings-prev').onclick = () => {bookingPage=Math.max(0,bookingPage-1);loadBookings().catch(e=>notice(e.message,true));};
    dashboard.querySelector('#bookings-next').onclick = () => {bookingPage++;loadBookings().catch(e=>notice(e.message,true));};
    form('availability-form', async data => {
      const rules=Array.from(dashboard.querySelectorAll('.availability-rule')).map(row=>({dayOfWeek:Number(row.querySelector('select').value),startMinute:minutes(row.querySelector('[data-start]').value),endMinute:minutes(row.querySelector('[data-end]').value)}));
      const exceptions=Array.from(dashboard.querySelectorAll('.availability-exception')).map(row=>({date:row.querySelector('[type=date]').value,closed:row.querySelector('[type=checkbox]').checked,startMinute:row.querySelector('[type=checkbox]').checked?null:minutes(row.querySelector('[data-start]').value),endMinute:row.querySelector('[type=checkbox]').checked?null:minutes(row.querySelector('[data-end]').value)}));
      await api('/me/availability','PUT',{timezone:data.timezone,rules,exceptions});notice('Đã lưu lịch rảnh.');
    });
  }
  const schedule=await api('/me/availability');
  dashboard.querySelector('[name=timezone]').value=schedule.timezone;
  dashboard.querySelector('#availability-rules').replaceChildren();
  dashboard.querySelector('#availability-exceptions').replaceChildren();
  schedule.rules.forEach(addRule);schedule.exceptions.forEach(addException);
  await loadBookings();
}
function timeFields(row,start=540,end=1020) {
  for(const [key,title,value] of [['start','Từ',start],['end','Đến',end]]) {
    const label=node('label',title), input=node('input');input.type='text';input.inputMode='numeric';input.placeholder='09:00';input.required=true;
    input.pattern=key==='end'?'([01][0-9]|2[0-3]):[0-5][0-9]|24:00':'([01][0-9]|2[0-3]):[0-5][0-9]';input.value=clock(value);input.dataset[key]='';label.append(input);row.append(label);
  }
  row.append(action('Xóa khung',()=>row.remove(),'danger'));
}
function addRule(rule) {
  const row=node('div',undefined,'availability-rule booking-row'),label=node('label','Ngày'),select=node('select');
  ['Thứ hai','Thứ ba','Thứ tư','Thứ năm','Thứ sáu','Thứ bảy','Chủ nhật'].forEach((day,i)=>{const option=node('option',day);option.value=i+1;select.append(option);});select.value=rule.dayOfWeek;
  label.append(select);row.append(label);timeFields(row,rule.startMinute,rule.endMinute);dashboard.querySelector('#availability-rules').append(row);
}
function addException(exception) {
  const row=node('div',undefined,'availability-exception booking-row'),label=node('label','Ngày'),date=node('input');date.type='date';date.required=true;date.value=exception.date;label.append(date);row.append(label);
  const checkLabel=node('label','Nghỉ cả ngày','check'),check=node('input');check.type='checkbox';check.checked=exception.closed;checkLabel.prepend(check);row.append(checkLabel);
  timeFields(row,exception.startMinute??540,exception.endMinute??1020);
  const update=()=>row.querySelectorAll('[data-start],[data-end]').forEach(input=>{input.disabled=check.checked;});check.onchange=update;update();dashboard.querySelector('#availability-exceptions').append(row);
}
async function loadBookings() {
  const bookings=await api('/me/bookings?page='+bookingPage),list=dashboard.querySelector('#bookings-list');list.replaceChildren();
  if(!bookings.length)list.append(node('p','Chưa có lịch hẹn trong trang này.','muted'));
  bookings.forEach(booking=>{
    const row=node('article',undefined,'list-item');row.append(node('h3',booking.serviceTitle+' · '+statusNames[booking.status]),node('p',`${timeLabel(booking.startsAt,booking.timezone)} → ${timeLabel(booking.endsAt,booking.timezone)} (${booking.timezone})`),node('p',booking.name+' · '+booking.email),node('p',booking.message,'preserve'));
    const actions=node('div',undefined,'actions');
    for(const state of booking.status==='PENDING'?['CONFIRMED','CANCELLED']:booking.status==='CONFIRMED'?['CANCELLED',...(new Date(booking.endsAt)<=new Date()?['COMPLETED']:[])]:[]) {
      actions.append(action({CONFIRMED:'Xác nhận',CANCELLED:'Hủy lịch',COMPLETED:'Hoàn tất'}[state],async()=>{
        if(state==='CANCELLED'&&!confirm('Hủy lịch hẹn này?'))return;
        await api('/me/bookings/'+booking.id,'PATCH',{status:state});await loadBookings();window.dispatchEvent(new Event('linkhub:booking-changed'));
      }));
    }
    row.append(actions);list.append(row);
  });
  dashboard.querySelector('#bookings-page').textContent='Trang '+(bookingPage+1);
  dashboard.querySelector('#bookings-prev').disabled=bookingPage===0;dashboard.querySelector('#bookings-next').disabled=bookings.length<20;
}
export async function refreshBookings(){if(dashboard)await loadBookings();}

export function initPublicBooking(username, services) {
  if(!services.length)return;
  if (document.body.dataset.template === 'STUDIO') {
    const layout = document.querySelector('.profile-layout');
    const overview = node('div', undefined, 'studio-overview');
    const content = node('div', undefined, 'studio-overview-content');
    const hero = layout.querySelector('.profile-hero');
    layout.prepend(overview);
    overview.append(hero, content);
    for (const id of ['landing-intro', 'services-section', 'landing-details']) content.append(document.getElementById(id));
    layout.classList.add('has-booking');
  }
  const section=node('section',undefined,'card profile-booking landing-section');section.id='booking-section';
  section.innerHTML=`<h2>Đặt lịch hẹn</h2><p class="muted">Chọn dịch vụ và ngày để xem giờ còn trống. Lịch sẽ được giữ chỗ ngay khi gửi và chờ chủ profile xác nhận.</p>
    <form id="booking-form"><label>Dịch vụ<select name="serviceId" required></select></label><label>Ngày hẹn<input name="date" type="date" required></label>
    <p id="booking-zone" class="muted" role="status"></p><label>Giờ còn trống<select name="startsAt" required disabled><option value="">Chọn ngày để xem lịch</option></select></label>
    <label>Họ tên<input name="name" required maxlength="100" autocomplete="name"></label><label>Email<input name="email" type="email" required maxlength="254" autocomplete="email"></label>
    <label>Nội dung trao đổi<textarea name="message" maxlength="2000" rows="3"></textarea></label><button type="submit" disabled>Gửi đặt lịch</button></form><p id="booking-result" role="status" aria-live="polite"></p>`;
  const contact=document.getElementById('contact-section');contact.before(section);
  const bookingLink=node('a','Đặt lịch');bookingLink.href='#booking-section';
  const navigation=document.getElementById('landing-nav');navigation.insertBefore(bookingLink,navigation.querySelector('a[href="#contact-section"]'));
  const fields=section.querySelector('form').elements, result=section.querySelector('#booking-result'), zone=section.querySelector('#booking-zone'), submit=section.querySelector('[type=submit]');
  services.forEach(service=>{const option=node('option',`${service.title} · ${service.durationMinutes} phút`);option.value=service.id;fields.serviceId.append(option);});
  let version=0, sending=false;
  async function loadSlots() {
    const request=++version;fields.startsAt.replaceChildren();fields.startsAt.disabled=true;submit.disabled=true;
    if(!fields.date.value)return;
    zone.textContent='Đang tải giờ trống…';
    try {
      const data=await api(`/public/${username}/slots?serviceId=${encodeURIComponent(fields.serviceId.value)}&date=${fields.date.value}`);
      if(request!==version)return;
      zone.textContent=`Múi giờ: ${data.timezone}. `+(data.slots.length?'':'Ngày này không còn giờ trống.');
      const prompt=node('option','Chọn giờ hẹn');prompt.value='';fields.startsAt.append(prompt);
      data.slots.forEach(slot=>{const option=node('option',`${timeLabel(slot.startsAt,data.timezone)} → ${timeLabel(slot.endsAt,data.timezone)}`);option.value=slot.startsAt;fields.startsAt.append(option);});
      fields.startsAt.disabled=!data.slots.length;submit.disabled=sending||!data.slots.length;
    }catch(e){if(request===version)zone.textContent=e.message;}
  }
  fields.date.onchange=loadSlots;fields.serviceId.onchange=loadSlots;
  section.querySelector('form').addEventListener('submit',async event=>{
    event.preventDefault();
    if(sending)return;
    sending=true;submit.disabled=true;
    const data=Object.fromEntries(new FormData(event.target));
    try {
      const receipt=await api(`/public/${username}/bookings`,'POST',{serviceId:data.serviceId,startsAt:data.startsAt,name:data.name,email:data.email,message:data.message});
      result.textContent=`Đã giữ chỗ · Chờ xác nhận. ${timeLabel(receipt.startsAt,receipt.timezone)} (${receipt.timezone}). Mã lịch: ${receipt.id}`;
      await loadSlots();
    }catch(e){result.textContent=e.message;await loadSlots();}
    finally {sending=false;submit.disabled=fields.startsAt.disabled;}
  });
}
