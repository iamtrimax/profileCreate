import {node, money} from './common.js';
import {renderSections} from './landing.js';
import {initBlockReveal} from './block-reveal.js';

export const templates = [
  {id: 'CLASSIC', name: 'Classic', description: 'Xanh dịu, thẻ bo tròn. Gần gũi và dễ kết nối.'},
  {id: 'MINIMAL', name: 'Minimal', description: 'Trắng tinh giản, đường nét gọn. Tập trung vào nội dung.'},
  {id: 'STUDIO', name: 'Studio', description: 'Tông đất ấm, bố cục portfolio. Dành cho dấu ấn riêng.'},
  {id: 'MIDNIGHT', name: 'Midnight', description: 'Nền tối, điểm nhấn tím. Nổi bật và hiện đại.'},
  {id: 'BUSINESS', name: 'Business', description: 'Landing page dịch vụ: giới thiệu, lợi ích, quy trình và FAQ.', landing: true},
  {id: 'PORTFOLIO', name: 'Portfolio', description: 'Tiêu đề lớn, lưới dự án. Trưng bày năng lực và sản phẩm.', landing: true},
  {id: 'CREATOR', name: 'Creator', description: 'Tông hồng tím, thẻ nội dung. Kết nối cộng đồng và hợp tác.', landing: true}
];

export function applyTemplate(element, value) {
  const template = templates.find(item => item.id === value) || templates[0];
  element.dataset.template = template.id;
  element.dataset.layout = template.landing ? 'landing' : 'profile';
  return template;
}

export function renderTemplatePicker(container, selected) {
  container.replaceChildren();
  templates.forEach(template => {
    const label = node('label', undefined, 'template-option');
    const input = node('input');
    input.type = 'radio'; input.name = 'template'; input.value = template.id;
    input.checked = template.id === selected;
    const thumbnail = node('span', undefined, 'template-thumbnail profile-surface');
    applyTemplate(thumbnail, template.id);
    thumbnail.setAttribute('aria-hidden', 'true');
    thumbnail.append(node('span', 'L', 'thumbnail-avatar'), node('span', undefined, 'thumbnail-line'), node('span', undefined, 'thumbnail-link'), node('span', undefined, 'thumbnail-link'));
    const title = node('span', template.name, 'template-title');
    title.append(node('span', '✓', 'template-check'));
    label.append(input, thumbnail, title, node('span', template.description, 'template-description'));
    if (template.landing) thumbnail.append(node('span', 'LANDING PAGE', 'thumbnail-badge'));
    container.append(label);
  });
}

// This preview is local only: it never publishes a draft or records a visit.
export function renderPreview(container, profile) {
  applyTemplate(container, profile.template);
  container.replaceChildren();
  const layout = node('div', undefined, 'profile-layout');
  const hero = node('section', undefined, 'card profile-hero');
  const name = profile.displayName.trim() || 'Tên của bạn';
  hero.append(node('div', Array.from(name)[0].toUpperCase(), 'avatar'), node('p', '@' + profile.username, 'eyebrow'), node('h3', name, 'profile-name'), node('p', profile.bio, 'preserve'));
  const socials = node('div', undefined, 'socials');
  profile.links.forEach(link => socials.append(node('span', link.title + ' ↗', 'preview-link')));
  hero.append(socials);
  if (templates.find(item => item.id === profile.template)?.landing) hero.append(node('span', 'Trao đổi nhu cầu →', 'preview-cta'));
  const services = node('section', undefined, 'card profile-services');
  services.append(node('h3', 'Dịch vụ'));
  if (!profile.services.length) services.append(node('p', 'Liên hệ để trao đổi về nhu cầu của bạn.', 'muted'));
  profile.services.forEach(service => {
    const row = node('div', undefined, 'list-item');
    row.append(node('strong', service.title), node('p', service.description, 'preserve'), node('p', money(service.price), 'price'));
    services.append(row);
  });
  const contact = node('section', undefined, 'card profile-contact');
  contact.append(node('h3', 'Gửi yêu cầu'), node('p', 'Khách có thể gửi lời nhắn từ trang profile công khai.', 'muted'), node('span', 'Gửi yêu cầu →', 'preview-cta'));
  const intro = node('div', undefined, 'landing-content');
  const details = node('div', undefined, 'landing-content');
  renderSections(intro, profile.sections || [], 'intro', true);
  renderSections(details, profile.sections || [], 'details', true);
  layout.append(hero, intro, services, details, contact);
  container.append(layout);
  initBlockReveal(container, {root: container, replayButton: document.getElementById('replay-preview')});
}
