import { t, locale, bindText, getLanguage } from "./i18n.js";
export {t, locale, bindText, getLanguage};
let csrf;
export async function refreshCsrf() {
  const response = await fetch("/api/auth/csrf", {
    credentials: "same-origin",
    headers: {'Accept-Language': getLanguage()},
  });
  if (!response.ok)
    throw new Error(t("Không thể kết nối máy chủ. Vui lòng thử lại."));
  csrf = await response.json();
}
export async function api(path, method = "GET", body) {
  const headers = {'Accept-Language': getLanguage()};
  if (method !== "GET") {
    if (!csrf) await refreshCsrf();
    headers[csrf.headerName] = csrf.token;
    if (body !== undefined) headers["Content-Type"] = "application/json";
  }
  let response;
  try {
    response = await fetch("/api" + path, {
      method,
      credentials: "same-origin",
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new Error(t('Không thể kết nối mạng. Vui lòng thử lại.'));
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    const failure = new Error(
      t(error.message || `Yêu cầu thất bại (${response.status}).`),
    );
    failure.code = error.code;
    failure.retryable = error.retryable;
    failure.errors = (error.errors || []).map(item => ({...item, message: t(item.message)}));
    failure.status = response.status;
    throw failure;
  }
  return response.status === 204 ? null : response.json();
}
export const $ = (id) => document.getElementById(id);
export function node(tag, text, className) {
  const element = document.createElement(tag);
  if (text !== undefined) bindText(element, text);
  if (className) element.className = className;
  return element;
}
// Keep authored content out of UI translation, even if it equals a UI label.
export function rawNode(tag, text, className) {
  const element = document.createElement(tag);
  element.setAttribute('data-i18n-ignore', '');
  if (text !== undefined) element.textContent = String(text ?? '');
  if (className) element.className = className;
  return element;
}
export function notice(message, error = false) {
  bindText($("notice"), message);
  $("notice").className = error ? "error" : "";
  $("notice").hidden = false;
}
export function action(text, handler, className = "secondary") {
  const button = node("button", text, className);
  button.type = "button";
  button.addEventListener("click", () => busy(button, handler));
  return button;
}
export async function busy(button, handler) {
  if (button) button.disabled = true;
  try {
    await handler();
  } catch (e) {
    notice(e.message, true);
  } finally {
    if (button) button.disabled = false;
  }
}
export function form(id, handler) {
  $(id).addEventListener("submit", (event) => {
    event.preventDefault();
    busy(event.submitter, () =>
      handler(Object.fromEntries(new FormData(event.target)), event.target),
    );
  });
}
export const money = (amount) =>
  new Intl.NumberFormat(locale(), {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 2,
  }).format(amount);
