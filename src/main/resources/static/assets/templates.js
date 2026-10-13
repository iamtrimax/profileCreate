import {node, money, rawNode} from './common.js';
import {renderSections} from './landing.js';
import {initBlockReveal} from './block-reveal.js';

export const templates = [
  {id: 'CLASSIC', name: 'Classic', category: 'personal', layout: 'stack', description: 'Hồ sơ cân đối, thẻ liên kết và dịch vụ nhẹ nhàng.'},
  {id: 'MINIMAL', name: 'Minimal', category: 'personal', layout: 'list', description: 'Một cột thanh thoát, liên kết dạng danh sách.'},
  {id: 'STUDIO', name: 'Studio', category: 'creative', layout: 'split', description: 'Giới thiệu bên trái, nội dung bên phải. Sắc đất ấm.'},
  {id: 'MIDNIGHT', name: 'Midnight', category: 'personal', layout: 'spotlight', description: 'Hồ sơ nổi bật trên nền đêm, dịch vụ chia cột.'},
  {id: 'BUSINESS', name: 'Business', category: 'business', layout: 'banner', description: 'Tiêu đề ngang, lợi ích và các gói dịch vụ rõ ràng.', landing: true},
  {id: 'PORTFOLIO', name: 'Portfolio', category: 'creative', layout: 'gallery', description: 'Tên lớn kiểu triển lãm, lưới dự án thoáng rộng.', landing: true},
  {id: 'CREATOR', name: 'Creator', category: 'creative', layout: 'cards', description: 'Thẻ liên kết hai cột, không gian cho cộng đồng.', landing: true},
  {id: 'EDITORIAL', name: 'Editorial', category: 'creative', layout: 'editorial', description: 'Bố cục tạp chí, chữ serif và các đường phân cách.', landing: true, fresh: true},
  {id: 'BENTO', name: 'Bento', category: 'creative', layout: 'bento', description: 'Các khối nội dung ghép lưới, gọn và linh hoạt.', landing: true, fresh: true},
  {id: 'CONSULTANT', name: 'Consultant', category: 'business', layout: 'rail', description: 'Thanh giới thiệu bên cạnh nội dung chuyên môn.', landing: true, fresh: true},
  {id: 'BOUTIQUE', name: 'Boutique', category: 'business', layout: 'menu', description: 'Phong cách thanh lịch, dịch vụ như một thực đơn.', landing: true, fresh: true},
  {id: 'BRUTALIST', name: 'Brutalist', category: 'creative', layout: 'poster', description: 'Chữ đậm, viền vuông và bố cục dạng áp phích.', landing: true, fresh: true},
  {id: 'TERMINAL', name: 'Terminal', category: 'personal', layout: 'terminal', description: 'Hồ sơ lập trình viên, chữ monospace và hàng đánh số.', fresh: true},
  {id: 'RESUME', name: 'Résumé', category: 'business', layout: 'resume', description: 'Hồ sơ nghề nghiệp với các mục theo dòng thời gian.', landing: true, fresh: true},
  {id: 'SPOTLIGHT', name: 'Spotlight', category: 'personal', layout: 'focus', description: 'Tên và lời giới thiệu làm tâm điểm, liên kết toàn chiều rộng.', fresh: true}
];
const categories = [['all', 'Tất cả'], ['personal', 'Cá nhân'], ['creative', 'Sáng tạo'], ['business', 'Kinh doanh']];

export function applyTemplate(element, value) {
  const template = templates.find(item => item.id === value) || templates[0];
  element.dataset.template = template.id;
  element.dataset.layout = template.landing ? 'landing' : 'profile';
  element.dataset.composition = template.layout;
  return template;
}

export function renderTemplatePicker(container, selected) {
  container.replaceChildren();
  let toolbar = document.getElementById('template-toolbar');
  if (!toolbar) {
    toolbar = node('div', undefined, 'template-toolbar'); toolbar.id = 'template-toolbar';
    container.before(toolbar);
  }
  toolbar.replaceChildren();
  const filters = node('div', undefined, 'template-filters');
  filters.setAttribute('role', 'group'); filters.setAttribute('aria-label', 'Lọc mẫu giao diện');
  const count = node('span', undefined, 'muted template-count'); count.setAttribute('role', 'status');
  let active = 'all';
  const update = () => {
    let visible = 0;
    container.querySelectorAll('.template-option').forEach(option => {
      option.hidden = active !== 'all' && option.dataset.category !== active;
      if (!option.hidden) visible++;
    });
    filters.querySelectorAll('button').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.category === active)));
    count.textContent = visible + ' mẫu';
  };
  categories.forEach(([id, title]) => {
    const button = node('button', title, 'secondary'); button.type = 'button'; button.dataset.category = id;
    button.addEventListener('click', () => { active = id; update(); }); filters.append(button);
  });
  const previewLink = node('a', 'Xem trước ↓', 'template-preview-link'); previewLink.href = '#template-preview';
  toolbar.append(filters, count, previewLink);
  templates.forEach(template => {
    const label = node('label', undefined, 'template-option'); label.dataset.category = template.category;
    const input = node('input');
    input.type = 'radio'; input.name = 'template'; input.value = template.id;
    input.checked = template.id === selected;
    input.setAttribute('aria-label', template.name + '. ' + template.description);
    const thumbnail = node('span', undefined, 'template-thumbnail profile-surface');
    applyTemplate(thumbnail, template.id); thumbnail.setAttribute('aria-hidden', 'true');
    const hero = node('span', undefined, 'thumbnail-hero');
    hero.append(node('span', 'L', 'thumbnail-avatar'), node('span', undefined, 'thumbnail-line'), node('span', undefined, 'thumbnail-line short'));
    const content = node('span', undefined, 'thumbnail-content');
    for (let i = 0; i < 4; i++) content.append(node('span', undefined, 'thumbnail-link'));
    thumbnail.append(hero, content);
    const title = node('span', template.name, 'template-title');
    if (template.fresh) title.append(node('span', 'Mới', 'template-new'));
    title.append(node('span', '✓', 'template-check'));
    label.append(input, thumbnail, title, node('span', template.description, 'template-description'));
    container.append(label);
  });
  update();
}

// This preview is local only: it never publishes a draft or records a visit.
export function renderPreview(container, profile) {
  applyTemplate(container, profile.template);
  container.replaceChildren();
  const layout = node('div', undefined, 'profile-layout');
  const hero = node('section', undefined, 'card profile-hero');
  const name = profile.displayName.trim() || 'Tên của bạn';
  hero.append(rawNode('div', Array.from(name)[0].toUpperCase(), 'avatar'), rawNode('p', '@' + profile.username, 'eyebrow'), (profile.displayName.trim()?rawNode:node)('h3', name, 'profile-name'), rawNode('p', profile.bio, 'preserve'));
  const socials = node('div', undefined, 'socials');
  profile.links.forEach(link => socials.append(rawNode('span', link.title + ' ↗', 'preview-link')));
  hero.append(socials);
  if (templates.find(item => item.id === profile.template)?.landing) hero.append(node('span', 'Trao đổi nhu cầu →', 'preview-cta'));
  const services = node('section', undefined, 'card profile-services');
  services.append(node('h3', 'Dịch vụ'));
  const serviceItems = node('div', undefined, 'service-items');
  if (!profile.services.length) services.append(node('p', 'Liên hệ để trao đổi về nhu cầu của bạn.', 'muted'));
  profile.services.forEach(service => {
    const row = node('div', undefined, 'list-item');
    row.append(rawNode('strong', service.title), rawNode('p', service.description, 'preserve'), node('p', money(service.price), 'price'));
    serviceItems.append(row);
  });
  services.append(serviceItems);
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
