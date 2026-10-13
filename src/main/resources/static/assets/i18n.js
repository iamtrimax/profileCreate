import {createTranslator} from './i18n-core.js';

// The browser and the API use this same catalogue.
const catalogueResponse = await fetch(new URL('./i18n-en.json', import.meta.url));
if (!catalogueResponse.ok) throw new Error('Unable to load language resources.');
const translate = createTranslator(await catalogueResponse.json());
const storageKey = 'linkhub.language';
let language = 'vi';
try { if (localStorage.getItem(storageKey) === 'en') language = 'en'; } catch { /* Storage is optional. */ }

export const getLanguage = () => language;
export const locale = () => language === 'en' ? 'en-US' : 'vi-VN';
export const t = value => translate(translate(value, 'vi'), language);

const ignored = 'script,style,[data-i18n-ignore]';
const textIgnored = ignored + ',textarea,[data-language-select]';
const attributes = ['title', 'placeholder', 'aria-label', 'alt'];
const bindings = new Map();
const attributeBindings = new WeakMap();

function textBinding(textNode, source = textNode.textContent) {
  if (!textNode.parentElement || textNode.parentElement.closest(textIgnored)) return;
  let binding = bindings.get(textNode);
  if (!binding || source !== binding.last) binding = {element: textNode, source: translate(source, 'vi'), last: source};
  const next = t(binding.source);
  if (next === binding.source && source === next && !bindings.has(textNode)) return;
  bindings.set(textNode, binding);
  binding.last = next;
  if (textNode.textContent !== next) textNode.textContent = next;
}

export function bindText(element, value) {
  element.textContent = String(value ?? '');
  if (element.firstChild) textBinding(element.firstChild);
}

function localizeAttributes(element) {
  if (element.closest(ignored)) return;
  let records = attributeBindings.get(element);
  if (!records) { records = new Map(); attributeBindings.set(element, records); }
  for (const attribute of attributes) {
    const source = element.getAttribute(attribute);
    if (source === null) continue;
    let binding = records.get(attribute);
    if (!binding || source !== binding.last) {
      binding = {element, attribute, source: translate(source, 'vi'), last: source};
      records.set(attribute, binding);
    }
    const next = t(binding.source);
    if (next !== binding.source || bindings.has(binding)) bindings.set(binding, binding);
    binding.last = next;
    if (source !== next) element.setAttribute(attribute, next);
  }
}

export function localize(root = document.documentElement) {
  if (root.nodeType === Node.TEXT_NODE) { textBinding(root); return; }
  if (root.nodeType !== Node.ELEMENT_NODE) return;
  if (root.closest(ignored)) return;
  localizeAttributes(root);
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const current = walker.currentNode;
    if (current.nodeType === Node.TEXT_NODE) textBinding(current);
    else localizeAttributes(current);
  }
}

const validationControls = new WeakSet();
function clearValidation(control) {
  if (validationControls.has(control)) { control.setCustomValidity(''); validationControls.delete(control); }
}
function render() {
  document.documentElement.lang = language;
  for (const [key, binding] of bindings) {
    const element = binding.element;
    if (!element.isConnected) { bindings.delete(key); continue; }
    if (binding.attribute ? element.closest(ignored) : element.parentElement?.closest(textIgnored)) continue;
    const current = binding.attribute ? element.getAttribute(binding.attribute) : element.textContent;
    if (current !== binding.last) binding.source = translate(current, 'vi');
    binding.last = t(binding.source);
    if (binding.attribute) element.setAttribute(binding.attribute, binding.last);
    else element.textContent = binding.last;
  }
  localize();
  document.querySelectorAll('[data-language-select]').forEach(select => { select.value = language; });
  document.querySelectorAll('input,select,textarea').forEach(clearValidation);
}

export function setLanguage(value) {
  if (value !== 'vi' && value !== 'en') return;
  language = value;
  try { localStorage.setItem(storageKey, language); } catch { /* Keep this page's choice. */ }
  render();
  window.dispatchEvent(new CustomEvent('linkhub:language-change', {detail: language}));
}

// Bind individual text nodes so icons, inputs and event handlers survive a switch.
const observer = new MutationObserver(mutations => {
  const roots = new Set();
  for (const mutation of mutations) {
    if (mutation.type === 'childList') mutation.addedNodes.forEach(node => roots.add(node));
    else roots.add(mutation.target);
  }
  roots.forEach(root => { if (root.isConnected) localize(root); });
});
observer.observe(document.documentElement, {subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: attributes});
document.addEventListener('change', event => {
  if (event.target.matches('[data-language-select]')) setLanguage(event.target.value);
});
window.addEventListener('storage', event => {
  if (event.key === storageKey || event.key === null) {
    language = event.newValue === 'en' ? 'en' : 'vi';
    render();
    window.dispatchEvent(new CustomEvent('linkhub:language-change', {detail: language}));
  }
});

// Native browser validation otherwise follows the browser's own language.
document.addEventListener('input', event => clearValidation(event.target), true);
document.addEventListener('change', event => clearValidation(event.target), true);
document.addEventListener('invalid', event => {
  const control = event.target;
  clearValidation(control);
  const state = control.validity;
  const message = state.valueMissing ? 'Vui lòng điền trường này.'
    : state.typeMismatch ? (control.type === 'email' ? 'Vui lòng nhập địa chỉ email hợp lệ.' : 'Vui lòng nhập đường dẫn hợp lệ.')
    : state.patternMismatch ? 'Vui lòng nhập đúng định dạng yêu cầu.'
    : state.badInput ? 'Vui lòng nhập một số hợp lệ.'
    : state.rangeUnderflow ? `Giá trị tối thiểu là ${control.min}.`
    : state.rangeOverflow ? `Giá trị tối đa là ${control.max}.`
    : state.tooShort ? `Vui lòng nhập ít nhất ${control.minLength} ký tự.`
    : state.tooLong ? `Vui lòng nhập tối đa ${control.maxLength} ký tự.`
    : state.stepMismatch ? `Vui lòng nhập giá trị theo bước ${control.step}.` : '';
  if (message) { control.setCustomValidity(t(message)); validationControls.add(control); }
}, true);
render();
