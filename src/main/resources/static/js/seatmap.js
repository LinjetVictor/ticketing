(function () {
  const form = document.getElementById('seatForm');
  if (!form) return;

  const eventId = form.dataset.eventId;
  const max = parseInt(form.dataset.max, 10);
  const currency = form.dataset.currency;
  const summary = document.getElementById('selSummary');
  const holdBtn = document.getElementById('holdBtn');

  function picked() {
    return form.querySelectorAll('input[name=seatIds]:checked');
  }

  function refreshSummary() {
    const chosen = picked();
    let total = 0;
    chosen.forEach(function (input) {
      total += parseInt(input.closest('.seat').dataset.price, 10);
    });
    holdBtn.disabled = chosen.length === 0;
    summary.textContent = chosen.length === 0
      ? 'No seats selected'
      : chosen.length + (chosen.length === 1 ? ' seat' : ' seats') + ', ' + currency + ' ' + (total / 100).toFixed(2);
  }

  form.addEventListener('change', function (e) {
    if (e.target.name !== 'seatIds') return;
    if (picked().length > max) {
      e.target.checked = false;
      summary.textContent = 'You can pick up to ' + max + ' seats';
      return;
    }
    refreshSummary();
  });

  // Pick up holds and bookings made by other people while this page is open.
  async function poll() {
    try {
      const response = await fetch('/api/events/' + eventId + '/seats', { headers: { Accept: 'application/json' } });
      if (!response.ok) return;
      const statuses = await response.json();
      form.querySelectorAll('.seat').forEach(function (seat) {
        const status = statuses[seat.dataset.seatId];
        if (!status) return;
        const input = seat.querySelector('input');
        const free = status === 'AVAILABLE';
        seat.classList.remove('available', 'held', 'booked');
        seat.classList.add(status.toLowerCase());
        if (!free && input.checked) input.checked = false;
        input.disabled = !free;
      });
      refreshSummary();
    } catch (err) { /* network blip: try again on the next tick */ }
  }

  setInterval(poll, 5000);
  refreshSummary();
})();
