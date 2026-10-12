import {node} from './common.js';

const pages = [
  ['overview', 'Tổng quan', 'Một góc nhìn rõ ràng về các kết nối của bạn.', 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z'],
  ['design', 'Profile & giao diện', 'Chọn bố cục phù hợp. Tạo dấu ấn của riêng bạn.', 'M4 4h16v16H4z M4 10h16 M10 10v10'],
  ['content', 'Nội dung & dịch vụ', 'Những điều bạn muốn chia sẻ với khách hàng.', 'M5 4h14v16H5z M9 8h6 M9 12h6 M9 16h4'],
  ['bookings', 'Lịch hẹn', 'Sắp xếp thời gian và quản lý các cuộc hẹn.', 'M4 6h16v15H4z M8 3v6 M16 3v6 M4 11h16'],
  ['inbox', 'Hộp thư', 'Tiếp nối những kết nối mới.', 'M3 5h18v14H3z M3 5l9 7 9-7'],
  ['recruitment', 'Tuyển dụng', 'Tìm cơ hội, khám phá ứng viên và hẹn phỏng vấn.', 'M4 7h16v14H4z M9 7V3h6v4 M4 12h16 M10 12v3h4v-3'],
  ['account', 'Tài khoản', 'Thông tin và bảo mật tài khoản của bạn.', 'M8 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0 M4 21v-2a8 8 0 0 1 16 0v2']
];
let current = 'overview';
let ready = false;
const groups = {
  overview: '.stats, #upcoming-reminders, #analytics-panel, #overview-start',
  recruitment: '#recruitment-panel',
  design: '#profile-editor',
  content: '#links-editor, #services-editor, #landing-editor',
  bookings: '.booking-dashboard', inbox: '#inbox-panel', account: '#account'
};

export function selectWorkspace(value, focus = false) {
  if (!ready) return;
  current = pages.some(page => page[0] === value) ? value : 'overview';
  const page = pages.find(page => page[0] === current);
  document.getElementById('dashboard').dataset.view = current;
  for (const [id, selectors] of Object.entries(groups)) {
    document.querySelectorAll(selectors).forEach(panel => { panel.hidden = id !== current; });
  }
  document.querySelectorAll('[data-workspace]').forEach(link => {
    if (link.dataset.workspace === current) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  const title = document.getElementById('workspace-title');
  title.textContent = page[1];
  document.getElementById('workspace-description').textContent = page[2];
  if (focus) { title.focus({preventScroll: true}); title.scrollIntoView({block: 'start'}); }
}

export function initWorkspace() {
  if (!ready) {
    const dashboard = document.getElementById('dashboard');
    const nav = node('nav', undefined, 'workspace-nav');
    nav.setAttribute('aria-label', 'Không gian quản lý');
    nav.append(node('p', 'KHÔNG GIAN LÀM VIỆC', 'eyebrow'));
    pages.forEach(([id, label, , path]) => {
      const link = node('a'); link.href = '#view-' + id; link.dataset.workspace = id;
      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('aria-hidden', 'true');
      const line = document.createElementNS(svg.namespaceURI, 'path'); line.setAttribute('d', path); svg.append(line);
      link.append(svg, node('span', label));
      link.addEventListener('click', event => {
        event.preventDefault();
        if (location.hash !== link.hash) history.pushState(null, '', link.hash);
        selectWorkspace(id, true);
      });
      nav.append(link);
    });
    nav.append(node('p', 'Một đường link.\nMọi kết nối.', 'nav-note'));
    dashboard.prepend(nav);
    const heading = node('div', undefined, 'workspace-page-heading');
    const title = node('h2'); title.id = 'workspace-title'; title.tabIndex = -1;
    const description = node('p', undefined, 'muted'); description.id = 'workspace-description';
    heading.append(title, description);
    dashboard.querySelector('.heading').after(heading);
    const start = node('section', undefined, 'card overview-start'); start.id = 'overview-start';
    const copy = node('div'); copy.append(node('p', 'TRANG CỦA BẠN, PHONG CÁCH CỦA BẠN', 'eyebrow'), node('h3', 'Sẵn sàng cho một diện mạo mới?'), node('p', 'Khám phá 15 mẫu giao diện và xem trước ngay khi chỉnh sửa.', 'muted'));
    const link = node('a', 'Tùy chỉnh profile →', 'button-link'); link.href = '#view-design';
    link.addEventListener('click', () => selectWorkspace('design', true));
    start.append(copy, link); dashboard.querySelector('.stats').after(start);
    ready = true;
    window.addEventListener('hashchange', () => {
      if (location.hash.startsWith('#view-') || !location.hash) selectWorkspace(location.hash.replace('#view-', ''), true);
    });
    window.addEventListener('linkhub:open-workspace', event => {
      history.replaceState(null, '', '#view-' + event.detail);
      selectWorkspace(event.detail);
    });
  }
  selectWorkspace(location.hash.startsWith('#view-') ? location.hash.slice(6) : current);
}
