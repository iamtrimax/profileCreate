import {api, refreshCsrf, $, node, notice, action, busy, form, money} from './common.js';
import {templates, renderTemplatePicker, renderPreview} from './templates.js';
import {sectionTypes} from './landing.js';
import {loadBookingDashboard, refreshBookings} from './booking.js';
import {startNotifications, stopNotifications} from './notifications.js';
import {startReminders, refreshReminders, stopReminders} from './reminders.js';
import {initAccountSecurity,renderAccount,loadEngagement} from './account-security.js';
initAccountSecurity();
let me;
window.addEventListener('account-session-reset',()=>{me=null;stopNotifications();stopReminders();$('logout').hidden=true;});
let page = 0;
let profileLinks = [];
let profileServices = [];
let profileSections = [];
function updatePreview() {
  if (!me) return;
  const fields = $('profile-form').elements;
  const template = fields.namedItem('template').value || 'CLASSIC';
  renderPreview($('template-preview'), {username: me.username, displayName: fields.displayName.value, bio: fields.bio.value, template, links: profileLinks, services: profileServices, sections: profileSections});
  const label = templates.find(item => item.id === template).name;
  $('template-state').textContent = label + (template === (me.template || 'CLASSIC') ? ' · Đã lưu' : ' · Chưa lưu');
}
$('profile-form').addEventListener('input', updatePreview);
function authMode(register) { $('login-form').hidden = register; $('register-form').hidden = !register; $('forgot-form').hidden=true; }
$('show-login').onclick = () => authMode(false);
$('show-register').onclick = () => authMode(true);
form('login-form', async data => { me = await api('/auth/login', 'POST', data); await refreshCsrf(); await dashboard(); notice('Đã đăng nhập.'); });
form('register-form', async (data, formElement) => { await api('/auth/register', 'POST', data); formElement.reset(); authMode(false); $('login-form').elements.email.value = data.email; notice('Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.'); });
$('logout').onclick = () => busy($('logout'), async () => { await api('/auth/logout', 'POST'); stopNotifications(); stopReminders(); location.reload(); });
async function dashboard() {
  renderAccount(me);
  $('auth').hidden = true; $('dashboard').hidden = false; $('logout').hidden = false;
  $('greeting').textContent = `Xin chào, ${me.displayName}`;
  $('public-link').textContent = `${location.origin}/${me.username}`; $('public-link').href = '/' + me.username;
  const fields = $('profile-form').elements;
  fields.displayName.value = me.displayName; fields.bio.value = me.bio; fields.published.checked = me.published;
  renderTemplatePicker($('template-picker'), me.template || 'CLASSIC');
  updatePreview();
  $('publish-state').textContent = me.published ? '● Đang công khai' : '○ Bản nháp';
  $('public-link').hidden = !me.published;
  $('qr-box').hidden = !me.published;
  if (me.published) { $('qr').src = `/api/public/${me.username}/qr`; $('qr-download').href = $('qr').src; }
  await Promise.all([loadLinks(), loadServices(), loadSections(), loadInbox(), loadAnalytics(), loadBookingDashboard(),startReminders()]);
  await startNotifications(()=>Promise.all([loadInbox(),loadAnalytics(),refreshBookings(),refreshReminders()]));
}
form('profile-form', async data => { me = await api('/me/profile', 'PUT', {displayName: data.displayName, bio: data.bio, published: data.published === 'on', template: data.template}); await dashboard(); notice('Đã lưu profile và mẫu giao diện.'); });
function edit(formId, item) { const fields = $(formId).elements; for (const [key, value] of Object.entries(item)) if (fields.namedItem(key)) fields.namedItem(key).value = value; $(formId).scrollIntoView({behavior: 'smooth', block: 'center'}); }
for (const id of ['link-form', 'service-form', 'section-form']) $(id).addEventListener('reset', () => { $(id).elements.namedItem('id').value = ''; });
async function loadSections() {
  profileSections = await api('/me/sections');
  $('sections-list').replaceChildren();
  if (!profileSections.length) $('sections-list').append(node('p', 'Bắt đầu với phần giới thiệu, một dự án hoặc câu hỏi khách thường hỏi.', 'muted'));
  profileSections.forEach(item => {
    const row = node('div', undefined, 'list-item');
    const type = sectionTypes.find(type => type.id === item.type);
    row.append(node('small', `${type?.title || item.type} · Thứ tự ${item.sortOrder} · ${item.enabled ? 'Đang hiển thị' : 'Đang ẩn'}`), node('strong', item.title), node('p', item.body, 'preserve'));
    const buttons = node('div', undefined, 'actions');
    buttons.append(action('Sửa mục', () => { edit('section-form', item); $('section-form').elements.enabled.checked = item.enabled; }),
      action(item.enabled ? 'Ẩn mục' : 'Hiện mục', async () => { await api(`/me/sections/${item.id}`, 'PUT', {type: item.type, title: item.title, body: item.body, url: item.url, sortOrder: item.sortOrder, enabled: !item.enabled}); await loadSections(); }),
      action('Xóa mục', async () => { if (!confirm('Xóa mục nội dung này?')) return; await api(`/me/sections/${item.id}`, 'DELETE'); if ($('section-form').elements.namedItem('id').value === item.id) $('section-form').reset(); await loadSections(); }, 'danger'));
    row.append(buttons); $('sections-list').append(row);
  });
  updatePreview();
}
form('section-form', async (data, formElement) => {
  await api('/me/sections' + (data.id ? '/' + data.id : ''), data.id ? 'PUT' : 'POST', {
    type: data.type, title: data.title, body: data.body, url: data.url, sortOrder: Number(data.sortOrder), enabled: formElement.elements.enabled.checked
  });
  formElement.reset(); await loadSections(); notice('Đã lưu mục nội dung landing page.');
});
async function loadLinks() {
  const links = await api('/me/links'); $('links-list').replaceChildren();
  profileLinks = links; updatePreview();
  if (!links.length) $('links-list').append(node('p', 'Thêm link đầu tiên để khách tìm hiểu về bạn.', 'muted'));
  links.forEach(item => {
    const row = node('div', undefined, 'list-item'); row.append(node('strong', item.title), node('p', item.url, 'muted'));
    const buttons = node('div', undefined, 'actions');
    buttons.append(action('Sửa', () => edit('link-form', item)), action('Xóa', async () => { if (!confirm('Xóa link này?')) return; await api(`/me/links/${item.id}`, 'DELETE'); await loadLinks(); }, 'danger'));
    row.append(buttons); $('links-list').append(row);
  });
}
form('link-form', async (data, formElement) => { await api('/me/links' + (data.id ? '/' + data.id : ''), data.id ? 'PUT' : 'POST', {title: data.title, url: data.url, sortOrder: Number(data.sortOrder)}); formElement.reset(); await loadLinks(); notice('Đã lưu link.'); });
async function loadServices() {
  const services = await api('/me/services'); $('services-list').replaceChildren();
  profileServices = services; updatePreview();
  if (!services.length) $('services-list').append(node('p', 'Bạn chưa thêm dịch vụ nào.', 'muted'));
  services.forEach(item => {
    const row = node('div', undefined, 'list-item'); row.append(node('strong', item.title), node('p', money(item.price), 'price'), node('p', item.description, 'preserve'));
    const buttons = node('div', undefined, 'actions'); buttons.append(action('Sửa', () => edit('service-form', item)), action('Xóa', async () => { if (!confirm('Xóa dịch vụ này?')) return; await api(`/me/services/${item.id}`, 'DELETE'); await loadServices(); }, 'danger'));
    row.append(buttons); $('services-list').append(row);
  });
}
form('service-form', async (data, formElement) => { await api('/me/services' + (data.id ? '/' + data.id : ''), data.id ? 'PUT' : 'POST', {title: data.title, description: data.description, price: data.price, durationMinutes: Number(data.durationMinutes)}); formElement.reset(); await loadServices(); notice('Đã lưu dịch vụ.'); });
async function loadInbox() {
  const result = await api(`/me/contacts?page=${page}&size=10`); $('inbox').replaceChildren();
  if (!result.items.length) $('inbox').append(node('p', 'Chưa có yêu cầu ở trang này.', 'muted'));
  const labels = {NEW: 'Mới', READ: 'Đã đọc', ARCHIVED: 'Đã lưu trữ'};
  result.items.forEach(item => {
    const row = node('div', undefined, 'list-item');
    row.append(node('strong', item.name), node('small', item.email), node('p', item.message, 'preserve'), node('small', `${new Date(item.createdAt).toLocaleString('vi-VN')} · ${labels[item.status]}`));
    const buttons = node('div', undefined, 'actions');
    for (const [status, label] of Object.entries(labels)) if (status !== item.status) buttons.append(action(label, async () => { await api(`/me/contacts/${item.id}`, 'PATCH', {status}); await Promise.all([loadInbox(), loadAnalytics()]); }));
    row.append(buttons); $('inbox').append(row);
  });
  $('page-number').textContent = `${page + 1} / ${Math.max(1, Math.ceil(result.total / 10))}`;
  $('prev').disabled = page === 0; $('next').disabled = (page + 1) * 10 >= result.total;
}
$('prev').onclick = async () => { page = Math.max(0, page - 1); try { await loadInbox(); } catch (e) { notice(e.message, true); } };
$('next').onclick = async () => { page++; try { await loadInbox(); } catch (e) { notice(e.message, true); } };
$('refresh').onclick = () => busy($('refresh'), () => Promise.all([loadInbox(), loadAnalytics()]));
async function loadAnalytics() {
  const result = await api('/me/analytics'); $('views').textContent = result.totalViews; $('contacts-count').textContent = result.totalContacts; $('unread').textContent = result.newContacts;
  $('daily').replaceChildren(); result.daily.slice().reverse().forEach(day => { const row = node('div', undefined, 'day-row'); row.append(node('span', day.date), node('strong', day.views + ' lượt')); $('daily').append(row); });
  await loadEngagement().catch(() => {
    let panel=$('engagement');if(!panel){panel=node('div');panel.id='engagement';$('daily').before(panel);}
    panel.replaceChildren(node('p','Chưa tải được thống kê mở rộng. Vui lòng thử làm mới sau.','muted'));
  });
}
try {
  await refreshCsrf();
  const response = await fetch('/api/me');
  if (response.ok) { me = await response.json(); await dashboard(); }
  else if (response.status !== 401) notice('Không tải được phiên đăng nhập. Vui lòng thử lại.', true);
} catch (e) { notice(e.message, true); }
