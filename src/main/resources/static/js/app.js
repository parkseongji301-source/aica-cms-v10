(() => {
  const toggle = document.querySelector('.menu-toggle');
  const backdrop = document.querySelector('.nav-backdrop');
  const sidebar = document.querySelector('.sidebar');
  const mobile = window.matchMedia('(max-width: 900px)');
  const syncSidebar = () => {
    if (!sidebar) return;
    const hidden = mobile.matches && !document.body.classList.contains('nav-open');
    sidebar.inert = hidden;
    sidebar.setAttribute('aria-hidden', String(hidden));
  };
  const close = () => {
    document.body.classList.remove('nav-open');
    if (toggle) toggle.setAttribute('aria-expanded', 'false');
    if (backdrop) backdrop.hidden = true;
    syncSidebar();
  };
  toggle?.addEventListener('click', () => {
    const open = !document.body.classList.contains('nav-open');
    document.body.classList.toggle('nav-open', open);
    toggle.setAttribute('aria-expanded', String(open));
    backdrop.hidden = !open;
    syncSidebar();
    if (open) sidebar.querySelector('a')?.focus();
  });
  backdrop?.addEventListener('click', close);
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && document.body.classList.contains('nav-open')) { close(); toggle?.focus(); }
    if (event.key === 'Tab' && mobile.matches && document.body.classList.contains('nav-open')) {
      const items = [...sidebar.querySelectorAll('a[href], button, summary')].filter(item => item.getClientRects().length && !item.disabled);
      const first = items[0], last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    }
  });
  mobile.addEventListener('change', event => { if (!event.matches) close(); else syncSidebar(); });
  syncSidebar();
  document.querySelectorAll('form[data-confirm]').forEach(form => {
    form.addEventListener('submit', event => { if (!window.confirm(form.dataset.confirm)) event.preventDefault(); });
  });
  document.querySelectorAll('[data-count-for]').forEach(counter => {
    const field = document.getElementById(counter.dataset.countFor);
    if (!field) return;
    const update = () => { counter.textContent = field.value.length.toLocaleString('ko-KR') + ' / ' + Number(field.maxLength).toLocaleString('ko-KR'); };
    field.addEventListener('input', update); update();
  });
})();
