import {node} from './common.js';

export function initLandingEffects() {
  if (document.body.dataset.layout !== 'landing' || document.body.dataset.effectsReady) return;
  document.body.dataset.effectsReady = 'true';
  document.documentElement.classList.add('landing-page');
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const nav = document.getElementById('landing-nav');
  const destinations = Array.from(nav.querySelectorAll('a[href^="#"]'))
    .map(link => ({link, section: document.getElementById(link.hash.slice(1))})).filter(item => item.section);

  const progress = node('progress', undefined, 'reading-progress');
  progress.max = 100; progress.value = 0;
  progress.setAttribute('aria-label', 'Tiến độ đọc trang');
  const top = node('button', '↑ Đầu trang', 'back-to-top');
  top.type = 'button'; top.hidden = true;
  top.addEventListener('click', () => {
    // Restore keyboard focus as well as scroll position.
    const heading = document.getElementById('name');
    heading.setAttribute('tabindex', '-1');
    heading.focus({preventScroll: true});
    window.scrollTo({top: 0, behavior: reducedMotion.matches ? 'instant' : 'smooth'});
  });
  document.body.append(progress, top);

  let frame = null;
  let activeLink = null;
  function updateScrollState() {
    frame = null;
    const length = document.documentElement.scrollHeight - window.innerHeight;
    progress.value = length <= 0 ? 100 : Math.min(100, Math.max(0, window.scrollY / length * 100));
    top.hidden = window.scrollY < 600;
    const offset = nav.getBoundingClientRect().bottom + 40;
    let current = null;
    destinations.forEach(item => { if (item.section.getBoundingClientRect().top <= offset) current = item.link; });
    if (window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 4 && window.scrollY > 0) current = destinations.at(-1)?.link || current;
    if (activeLink !== current) {
      activeLink?.removeAttribute('aria-current');
      current?.setAttribute('aria-current', 'location');
      activeLink = current;
    }
  }
  function scheduleUpdate() { if (frame === null) frame = window.requestAnimationFrame(updateScrollState); }
  function measureNavigation() {
    document.documentElement.style.setProperty('--landing-nav-offset', `${nav.offsetHeight + 32}px`);
    scheduleUpdate();
  }
  window.addEventListener('scroll', scheduleUpdate, {passive: true});
  window.addEventListener('resize', measureNavigation, {passive: true});
  // Opening a FAQ or loading an image changes page height without a window resize.
  document.querySelectorAll('.section-item').forEach(item => {
    if (item.tagName === 'DETAILS') item.addEventListener('toggle', scheduleUpdate);
  });
  document.getElementById('qr').addEventListener('load', scheduleUpdate);
  measureNavigation();

  // Block entrances are handled by block-reveal.js for every template and preview.
}
