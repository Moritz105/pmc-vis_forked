export function loadFingerPrints(fingerprints) {
  const container = document.getElementById('fingerprints');

  container.innerHTML = '';

  const table = document.createElement('table');
  table.classList.add('ui', 'celled', 'table');

  const tbody = document.createElement('tbody');
  Object.entries(fingerprints).forEach(([key, value]) => {
    const tr = document.createElement('tr');
    const tdKey = document.createElement('td');
    tdKey.textContent = key;
    const tdValue = document.createElement('td');
    tdValue.textContent = String(value);

    tr.appendChild(tdKey);
    tr.appendChild(tdValue);
    tbody.appendChild(tr);
  });
  table.appendChild(tbody);
  container.appendChild(table);
}
