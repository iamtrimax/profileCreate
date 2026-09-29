// One reveal controller for public profiles and the dashboard's scrolling preview.
const controllers = new WeakMap();

export function initBlockReveal(container, {root = null, replayButton = null} = {}) {
  controllers.get(container)?.();
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const targets = Array.from(container.querySelectorAll('.profile-hero, .landing-section, .profile-services, .profile-contact, .qr-card, .section-item, .profile-services .list-item'));
  let observer;
  let firstFrame;
  let secondFrame;
  let disposed = false;

  function show(element) { element.classList.remove('is-pending', 'is-entering'); }
  function enter(element) {
    if (!element.classList.contains('is-pending')) return;
    element.classList.remove('is-pending');
    element.classList.add('is-entering');
  }
  function showAll() {
    observer?.disconnect();
    window.cancelAnimationFrame(firstFrame);
    window.cancelAnimationFrame(secondFrame);
    targets.forEach(show);
  }
  function focusReveal(event) {
    // An invisible link must become visible immediately when reached by keyboard.
    for (let element = event.target; element && element !== container; element = element.parentElement) {
      if (element.classList.contains('block-reveal')) { show(element); observer?.unobserve(element); }
    }
  }
  function start() {
    showAll();
    const enabled = !motion.matches || container.dataset.revealOverride === 'true';
    container.dataset.revealMotion = enabled ? 'on' : 'off';
    if (replayButton) {
      replayButton.hidden = false;
      replayButton.textContent = enabled ? 'Xem lại hiệu ứng' : 'Bật và xem hiệu ứng';
      replayButton.title = enabled ? 'Phát lại hiệu ứng xuất hiện các khối' : 'Thiết bị đang giảm chuyển động. Bấm để bật hiệu ứng trên trang này.';
    }
    if (disposed || !enabled || !('IntersectionObserver' in window)) return;
    try {
      observer = new window.IntersectionObserver(entries => {
        if (disposed) return;
        entries.forEach(entry => {
          if (entry.isIntersecting) { enter(entry.target); observer.unobserve(entry.target); }
        });
      }, {root, threshold: 0, rootMargin: '0px 0px -24px 0px'});
      targets.forEach(element => {
        element.classList.add('block-reveal', 'is-pending');
        const siblings = Array.from(element.parentElement.children);
        element.dataset.blockStep = String(element.matches('.section-item, .list-item') ? siblings.indexOf(element) % 3 : 0);
      });
      // Paint the initial state before observation; otherwise in-view blocks can
      // jump directly to the final state without a visible CSS transition.
      firstFrame = window.requestAnimationFrame(() => {
        secondFrame = window.requestAnimationFrame(() => {
          if (disposed) return;
          try { targets.forEach(element => observer.observe(element)); }
          catch { showAll(); }
        });
      });
    } catch { showAll(); }
  }
  function replay() {
    container.dataset.revealOverride = 'true';
    if (root) root.scrollTop = 0;
    else window.scrollTo({top: 0, behavior: 'instant'});
    start();
  }
  function animationEnded(event) {
    if (event.animationName === 'block-entrance') event.target.classList.remove('is-entering');
  }
  function cleanup() {
    disposed = true; showAll();
    container.removeEventListener('focusin', focusReveal);
    container.removeEventListener('animationend', animationEnded);
    replayButton?.removeEventListener('click', replay);
    motion.removeEventListener('change', start);
    controllers.delete(container);
  }
  container.addEventListener('focusin', focusReveal);
  container.addEventListener('animationend', animationEnded);
  replayButton?.addEventListener('click', replay);
  motion.addEventListener('change', start);
  controllers.set(container, cleanup);
  start();
  return cleanup;
}
