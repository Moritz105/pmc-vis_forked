export function loadFingerPrints(fingerprints) {
  const container = document.getElementById('fingerprints');
  container.innerHTML = '';

  const table = document.createElement('table');
  table.classList.add('ui', 'celled', 'table');
  const tbody = document.createElement('tbody');

  Object.entries(fingerprints).forEach(([key, value]) => {
    const tr = document.createElement('tr');
    const tdKey = document.createElement('td');
    tdKey.style.fontWeight = 'bold';
    tdKey.textContent = formatKey(key);

    const tdValue = document.createElement('td');

    if (Array.isArray(value)) {
      if (value.length > 0) {
        tdValue.textContent = formatMapToString(value[0]);
      } else {
        tdValue.textContent = 'Kein Matching gefunden';
        tdValue.style.color = '#db2828';
        tdValue.style.fontWeight = 'bold';
      }
    } else {
      tdValue.textContent = value ?? '0';
    }

    tr.appendChild(tdKey);
    tr.appendChild(tdValue);
    tbody.appendChild(tr);
  });

  table.appendChild(tbody);
  container.appendChild(table);
}

function formatMapToString(map) {
  return Object.entries(map)
      .map(([k, v]) => `${k}: ${v}`)
      .join(' | ');
}

function formatKey(key) {
  const labels = {
    numStatesRef: 'Zustände (Ref)',
    numStatesNew: 'Zustände (Neu)',
    numTransitionsRef: 'Transitionen (Ref)',
    numTransitionsNew: 'Transitionen (Neu)',
    variableComparison: 'Distribution Matching'
  };
  return labels[key] || key;
}
