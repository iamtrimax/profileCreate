import {api, $, node, notice, form, money} from './common.js';
import {applyTemplate} from './templates.js';
import {renderSections, renderLandingNav} from './landing.js';
import {initLandingEffects} from './landing-effects.js';
import {initBlockReveal} from './block-reveal.js';
import {initPublicBooking} from './booking.js';
const username = encodeURIComponent(location.pathname.split('/')[1]);
form('contact-form', async (data, formElement) => { await api(`/public/${username}/contacts`, 'POST', data); formElement.reset(); notice('Đã gửi yêu cầu. Cảm ơn bạn đã kết nối!'); });
try {
  const profile = await api(`/public/${username}`);
  applyTemplate(document.body, profile.template);
  renderSections($('landing-intro'), profile.sections || [], 'intro');
  renderSections($('landing-details'), profile.sections || [], 'details');
  renderLandingNav($('landing-nav'), profile.sections || []);
  document.title = profile.displayName + ' · LinkHub VN';
  $('name').textContent = profile.displayName; $('bio').textContent = profile.bio; $('username').textContent = '@' + profile.username; $('avatar').textContent = Array.from(profile.displayName)[0]?.toUpperCase() || 'L';
  profile.links.forEach(link => { const a = node('a', link.title + ' ↗'); a.href = link.url; a.target = '_blank'; a.rel = 'noopener noreferrer'; a.addEventListener('click',()=>api(`/public/${username}/clicks/${link.id}`,'POST').catch(()=>{})); $('socials').append(a); });
  if (!profile.services.length) $('services').append(node('p', 'Liên hệ để trao đổi về nhu cầu của bạn.', 'muted'));
  profile.services.forEach(service => { const row = node('div', undefined, 'list-item'); row.append(node('h3', service.title), node('p', service.description, 'preserve'), node('p', money(service.price), 'price')); $('services').append(row); });
  $('qr').src = `/api/public/${username}/qr`;
  initPublicBooking(username, profile.services);
  initLandingEffects();
  const replay = node('button', 'Xem lại hiệu ứng', 'secondary');
  replay.type = 'button';
  document.querySelector('.header-actions').append(replay);
  initBlockReveal(document.body, {replayButton: replay});
  // A page view is recorded separately; API reads and QR downloads do not inflate analytics.
  api(`/public/${username}/visits`, 'POST',{referrer:document.referrer.slice(0,2048)}).catch(() => {});
} catch (e) { $('name').textContent = 'Profile không khả dụng'; $('contact-form').hidden = true; $('qr').hidden = true; notice(e.message, true); }
