(function () {
  const el = document.getElementById('countdown');
  if (!el || el.dataset.remaining === undefined) return;

  let remaining = parseInt(el.dataset.remaining, 10);
  const confirmBtn = document.getElementById('confirmBtn');
  const notice = document.getElementById('expiredNotice');

  function format(seconds) {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return m + ':' + String(s).padStart(2, '0');
  }

  function tick() {
    if (remaining <= 0) {
      el.textContent = 'Expired';
      el.classList.add('expired');
      if (confirmBtn) confirmBtn.disabled = true;
      if (notice) notice.classList.remove('hidden');
      return;
    }
    el.textContent = format(remaining);
    remaining -= 1;
    setTimeout(tick, 1000);
  }
  tick();
})();
