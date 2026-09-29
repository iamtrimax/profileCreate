// Loaded before styles so a saved preference is applied before the first paint.
(() => {
  const storageKey = 'linkhub.color-mode';
  const system = window.matchMedia('(prefers-color-scheme: dark)');
  const valid = value => value === 'light' || value === 'dark';
  let preference = null;
  try {
    const saved = localStorage.getItem(storageKey);
    if (valid(saved)) preference = saved;
  } catch { /* Switching still works when browser storage is unavailable. */ }

  function apply() {
    const mode = preference || (system.matches ? 'dark' : 'light');
    document.documentElement.dataset.colorMode = mode;
    document.querySelectorAll('[data-theme-toggle]').forEach(button => {
      button.setAttribute('aria-pressed', String(mode === 'dark'));
      button.title = mode === 'dark' ? 'Chuyển sang chế độ sáng' : 'Chuyển sang chế độ tối';
      button.querySelector('[data-theme-icon]').textContent = mode === 'dark' ? '☀' : '☾';
    });
  }
  apply();
  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-theme-toggle]').forEach(button => {
      button.hidden = false;
      button.addEventListener('click', () => {
        preference = document.documentElement.dataset.colorMode === 'dark' ? 'light' : 'dark';
        try { localStorage.setItem(storageKey, preference); } catch { /* Keep this page's choice. */ }
        apply();
      });
    });
    apply();
  });
  system.addEventListener('change', () => { if (preference === null) apply(); });
  window.addEventListener('storage', event => {
    if (event.key === storageKey || event.key === null) {
      preference = valid(event.newValue) ? event.newValue : null;
      apply();
    }
  });
})();
