let csrf;
export async function refreshCsrf() {
  const response = await fetch('/api/auth/csrf', {credentials: 'same-origin'});
  if (!response.ok) throw new Error('Không thể kết nối máy chủ. Vui lòng thử lại.');
  csrf = await response.json();
}
export async function api(path, method = 'GET', body) {
  const headers = {};
  if (method !== 'GET') {
    if (!csrf) await refreshCsrf();
    headers[csrf.headerName] = csrf.token;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
  }
  const response = await fetch('/api' + path, {method, credentials: 'same-origin', headers, body: body === undefined ? undefined : JSON.stringify(body)});
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || `Yêu cầu thất bại (${response.status}).`);
  }
  return response.status === 204 ? null : response.json();
}
export const $ = id => document.getElementById(id);
export function node(tag, text, className) { const element = document.createElement(tag); if (text !== undefined) element.textContent = text; if (className) element.className = className; return element; }
export function notice(message, error = false) { $('notice').textContent = message; $('notice').className = error ? 'error' : ''; $('notice').hidden = false; }
export function action(text, handler, className = 'secondary') { const button = node('button', text, className); button.type = 'button'; button.addEventListener('click', () => busy(button, handler)); return button; }
export async function busy(button, handler) { button.disabled = true; try { await handler(); } catch (e) { notice(e.message, true); } finally { button.disabled = false; } }
export function form(id, handler) { $(id).addEventListener('submit', event => { event.preventDefault(); busy(event.submitter, () => handler(Object.fromEntries(new FormData(event.target)), event.target)); }); }
export const money = amount => new Intl.NumberFormat('vi-VN', {style: 'currency', currency: 'VND', maximumFractionDigits: 2}).format(amount);
