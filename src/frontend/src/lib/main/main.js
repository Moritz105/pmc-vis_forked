import { spawnPane, getPanes } from '../views/panes/panes.js';
import { params } from '../views/graph/layout-options/klay.js';
import { spawnGraph } from '../views/graph/node-link.js';
import { PROJECT } from '../utils/controls.js';
import { CONSTANTS } from '../utils/names.js';
import { socket } from '../views/imports/import-socket.js';
import { loadFingerPrints } from '../views/attributes/fingerprinits.js';

let BACKEND = import.meta.env.VITE_BACKEND_RESTFUL;

const info = {
  details: {},
  observer: new ResizeObserver((ms) => {
    const panes = getPanes(); // TODO: move panes to be part of this object?
    ms.forEach(m => {
      panes[m.target.pane]?.cy?.fit(undefined, 30);
      panes[m.target.pane]?.cy?.pcp.redraw();
    });
  }),
}; // singleton

function getDefaultBadge(name) {
  return `<i class="fa-xs ${
    CONSTANTS.INTERACTIONS[name].icon
  }" title="${
    CONSTANTS.INTERACTIONS[name].type
  }"></i>`;
}

function setInfo(newInfo) {
  Object.keys(newInfo).forEach(k => {
    info[k] = newInfo[k];
  });
  info.details = {};
  info.types = {};
  ['s', 't'].forEach(type => {
    Object.keys(info[type]).forEach(k => {
      info.details[k] = info[type][k];
      const t = info.types[k];
      info.types[k] = t ? t + '+' + type : type;
    });
    delete info[type];
  });

  info.badges = {
    ap_init: getDefaultBadge('ap_init'),
    ap_deadlock: getDefaultBadge('ap_deadlock'),
    ap_end: getDefaultBadge('ap_end'),
  };

  Object.keys(info.badges).forEach(ap => {
    const userSelected = info.details[CONSTANTS.atomicPropositions][CONSTANTS[ap]];
    if (userSelected) {
      if (userSelected.icon) {
        info.badges[ap] = `<i class="fa-xs ${userSelected.identifier}" title="${CONSTANTS.INTERACTIONS[ap].type}"></i>`;
      } else {
        info.badges[ap] = `<p title="${CONSTANTS.INTERACTIONS[ap].type}">${userSelected.identifier}</p>`;
      }
    }
  });

  Object.values(getPanes()).forEach(pane => {
    pane.cy.vars['update'].fn(pane.cy);
  });
}

const ww = window.innerWidth;
const numberOfPanes = document.getElementById('numberOfPanes');
if (ww && numberOfPanes) {
  numberOfPanes.value = Math.floor(ww / 200);
}

if (import.meta.env.VITE_HIDE_TODOS !== 'true') {
  document.querySelectorAll('.to-do').forEach(el => el.classList.remove('to-do'));
}

addEventListener('linked-selection', e => {
  const selection = e.detail.selection;
  const panes = getPanes();
  panes[e.detail.pane].cy.nodes().unselect();
  const strSelection = '#' + selection.map(n => n.id).join(', #');

  if (strSelection !== '#') {
    panes[e.detail.pane].cy.$(strSelection).select();
  }
}, true);

const interval = setInterval(async () => {
  if (socket.connected) {
    clearInterval(interval);
    start();
  } else {
    console.log('waiting for socket...');
  }
}, 50);

async function start() {
  const data = await socket.emitWithAck('MC_STATUS', PROJECT);
  setInfo(data.info);

  // 1. Trigger analysis first (sequential start)
  fetch(`${BACKEND}/${PROJECT}/fingerprints`)
      .then(response => response.json())
      .then(fingerprintData => {

        // 2. Only after fingerprints are done, fetch the graphs
        return Promise.all([
          fetch(`${BACKEND}/${PROJECT}/initial`).then(r => r.json()),
          fetch(`${BACKEND}/${PROJECT}/compare`).then(r => r.json()),
          Promise.resolve(fingerprintData)
        ]);
      })
      .then((results) => {
        const data1 = results[0];
        const data2 = results[1];
        const myFingerprints = results[2][0];

        // --- Process Nodes ---
        const getNodes = (d) => (Array.isArray(d) ? d : (d.nodes || []));
        const nodes1 = getNodes(data1).map(n => ({
          group: 'nodes',
          data: { ...n, diffColor: n.diffColor || 'none', origin: 'm1' }
        }));

        const greenNodes = getNodes(data2)
            .filter(n => n.diffColor === 'green')
            .map(n => ({
              group: 'nodes',
              data: { ...n, origin: 'm2' }
            }));

        // --- Process Edges (with duplicate filter) ---
        const getEdges = (d) => (Array.isArray(d) ? d : (d.edges || []));
        const edgeMap = new Map();
        [...getEdges(data1), ...getEdges(data2)].forEach(e => {
          if (e && e.id) edgeMap.set(e.id, { group: 'edges', data: e });
        });

        const overlayData = {
          nodes: [...nodes1, ...greenNodes],
          edges: Array.from(edgeMap.values()),
        };

        // --- UI Setup ---
        const uiNodesIds = nodes1.map(n => n.data.id).filter(id => id && !id.startsWith('t'));
        const pane = spawnPane({ id: 'pane-0' }, uiNodesIds);
        const cy = spawnGraph(pane, overlayData, params);

        if (cy) {
          // Styling rules for the colors from Java
          cy.style()
              .selector('node').style({ 'label': 'data(name)', 'background-color': '#ccc' })
              .selector('node[diffColor="blue"]').style({ 'background-color': '#007bff', 'color': '#fff' })
              .selector('node[diffColor="red"]').style({ 'background-color': '#dc3545', 'color': '#fff' })
              .selector('node[diffColor="green"]').style({ 'background-color': '#28a745', 'border-style': 'dashed', 'border-width': 3 })
              .selector('node[diffColor="halo"]').style({ 'background-color': '#fff', 'border-color': '#ffc107', 'border-width': 4 })
              .selector('edge').style({ 'curve-style': 'bezier', 'target-arrow-shape': 'triangle', 'line-color': '#999', 'opacity': 0.6 })
              .update();
        }

        loadFingerPrints(myFingerprints);
      })
      .catch(err => console.error("Loading failed:", err));
}

export { info, setInfo, BACKEND };
