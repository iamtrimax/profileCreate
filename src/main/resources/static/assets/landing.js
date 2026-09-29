import {node} from './common.js';

export const sectionTypes = [
  {id: 'ABOUT', title: 'Giới thiệu', anchor: 'about-section', area: 'intro'},
  {id: 'FEATURE', title: 'Điểm nổi bật', anchor: 'features-section', area: 'intro'},
  {id: 'PROCESS', title: 'Cách làm việc', anchor: 'process-section', area: 'intro'},
  {id: 'PROJECT', title: 'Dự án & sản phẩm', anchor: 'projects-section', area: 'details'},
  {id: 'FAQ', title: 'Câu hỏi thường gặp', anchor: 'faq-section', area: 'details'}
];

export function renderSections(container, sections, area, preview = false) {
  container.replaceChildren();
  sectionTypes.filter(type => type.area === area).forEach(type => {
    const items = sections.filter(item => item.enabled && item.type === type.id).slice().sort((a, b) => a.sortOrder - b.sortOrder);
    if (!items.length) return;
    const section = node('section', undefined, 'card landing-section');
    section.dataset.sectionType = type.id;
    if (!preview) section.id = type.anchor;
    section.append(node('p', type.id === 'PROJECT' ? 'NHỮNG ĐIỀU ĐÃ LÀM' : 'TÌM HIỂU THÊM', 'eyebrow'), node('h2', type.title));
    const grid = node('div', undefined, 'section-items');
    items.forEach((item, index) => {
      const row = node(type.id === 'FAQ' ? 'details' : 'article', undefined, 'section-item');
      if (type.id === 'PROCESS') row.append(node('span', String(index + 1).padStart(2, '0'), 'step-number'));
      row.append(node(type.id === 'FAQ' ? 'summary' : 'h3', item.title));
      row.append(node('p', item.body, 'preserve'));
      if (item.url) {
        const link = node(preview ? 'span' : 'a', 'Xem chi tiết ↗', 'section-link');
        if (!preview) { link.href = item.url; link.target = '_blank'; link.rel = 'noopener noreferrer'; }
        row.append(link);
      }
      grid.append(row);
    });
    section.append(grid); container.append(section);
  });
  container.hidden = !container.childElementCount;
}

export function renderLandingNav(container, sections) {
  container.replaceChildren();
  const visible = sectionTypes.filter(type => sections.some(item => item.enabled && item.type === type.id));
  const destinations = [...visible.filter(type => type.area === 'intro'), {title: 'Dịch vụ', anchor: 'services-section'},
    ...visible.filter(type => type.area === 'details'), {title: 'Liên hệ', anchor: 'contact-section'}];
  destinations.forEach(item => { const link = node('a', item.title); link.href = '#' + item.anchor; container.append(link); });
}
