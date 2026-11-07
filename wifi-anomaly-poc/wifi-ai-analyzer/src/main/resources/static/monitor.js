'use strict';

const byId = id => document.getElementById(id);
const selector = byId('stream');
const status = byId('status');
const calibrate = byId('calibrate');
const exportButton = byId('export');
const number = new Intl.NumberFormat('pl-PL', {maximumFractionDigits: 1});
let selectedResults;
let busy = false;

async function request(path, options) {
    const response = await fetch(path, {cache: 'no-store', ...options});
    if (!response.ok) {
        if (response.status === 409) {
            throw new Error('Do kalibracji potrzeba 30 ostatnich okien z odebranym beaconem.');
        }
        throw new Error('Błąd połączenia z backendem (' + response.status + ').');
    }
    return response.status === 204 ? undefined : response.json();
}

function alertText(value) {
    if (value == null) return '–';
    return value ? 'Alarm' : 'Norma';
}

function svgElement(name, attributes) {
    const node = document.createElementNS('http://www.w3.org/2000/svg', name);
    Object.entries(attributes).forEach(([key, value]) => node.setAttribute(key, value));
    return node;
}

function drawChart(results) {
    const chart = byId('chart');
    chart.replaceChildren();
    const points = [...results.windows].reverse();
    const maximum = Math.max(1, ...points.flatMap(({window}) => [window.deauth, window.disassoc]));
    for (const fraction of [0, .5, 1]) {
        const y = 185 - fraction * 165;
        chart.append(svgElement('line', {x1: 55, x2: 810, y1: y, y2: y, stroke: '#d3dce7'}));
        const text = svgElement('text', {x: 48, y: y + 4, 'text-anchor': 'end', 'font-size': 13});
        text.textContent = number.format(maximum * fraction);
        chart.append(text);
    }
    const spacing = 755 / Math.max(points.length, 1);
    const width = Math.min(16, spacing * .42);
    points.forEach(({window}, index) => {
        const x = 55 + index * spacing + spacing / 2;
        for (const [value, color, offset] of [
            [window.deauth, '#a44026', -width],
            [window.disassoc, '#175a98', 0]
        ]) {
            const height = 165 * value / maximum;
            chart.append(svgElement('rect', {x: x + offset, y: 185 - height, width, height, fill: color}));
        }
    });
}

function render(results) {
    const latest = results.windows[0];
    for (const key of ['deauth', 'disassoc']) {
        byId(key).textContent = latest ? number.format(latest.window[key] * 5000 / latest.window.durationMs) : '–';
    }
    byId('threshold').textContent = alertText(latest?.thresholdAlert);
    byId('threshold').classList.toggle('alert', latest?.thresholdAlert === true);
    byId('model').textContent = latest?.score == null
        ? '–' : alertText(latest.mlAlert) + ' · ' + number.format(latest.score);
    byId('model').classList.toggle('alert', latest?.mlAlert === true);
    drawChart(results);

    byId('history').replaceChildren(...results.windows.map(item => {
        const w = item.window;
        const tr = document.createElement('tr');
        const values = [new Date(w.receivedAt).toLocaleTimeString('pl-PL'),
            w.beacon, w.deauth, w.disassoc, w.retry, alertText(item.thresholdAlert),
            alertText(item.mlAlert), item.gapBeforeMs == null ? '–' : number.format(item.gapBeforeMs / 1000) + ' s'];
        for (const value of values) {
            const td = document.createElement('td');
            td.textContent = value;
            if (value === 'Alarm') td.className = 'alert';
            tr.append(td);
        }
        return tr;
    }));
}

async function refresh() {
    if (busy) return;
    busy = true;
    try {
        const streams = await request('/api/streams');
        const previous = selector.value;
        selector.replaceChildren(...streams.map(stream => {
            const value = new URLSearchParams(stream).toString();
            const label = stream.sensorId + ' · ' + stream.bssid + ' · ' + stream.channel;
            return new Option(label, value, false, value === previous);
        }));
        if (!selector.value) {
            status.textContent = 'Oczekiwanie na pierwsze okno MQTT.';
            return;
        }
        const query = selector.value;
        const results = await request('/api/windows?' + query);
        if (selector.value !== query) return;
        selectedResults = results;
        render(results);
        const latest = results.windows[0]?.window;
        const message = [];
        if (new URLSearchParams(query).get('sensorId').startsWith('demo-')) message.push('Dane syntetyczne.');
        if (latest && Date.now() - new Date(latest.receivedAt).getTime() > 15000) message.push('Brak nowych okien: możliwa luka pomiarowa.');
        if (results.windows.some(item => item.gapBeforeMs != null)) message.push('W historii są możliwe luki pomiarowe.');
        if (latest?.beacon === 0) message.push('Brak beaconów w ostatnim oknie.');
        message.push(results.baselineSize
            ? 'Model kNN uczony na ' + results.baselineSize + ' oknach.'
            : 'Zbierz normalny ruch, a potem skalibruj model.');
        message.push('Progi alarmu: ' + results.countThreshold + ' ramek / 5 s oraz ' + results.scoreThreshold + ' dla kNN.');
        status.textContent = message.join(' ');
        calibrate.disabled = results.windows.length < 30;
        exportButton.disabled = results.windows.length === 0;
    } catch (error) {
        status.textContent = error.message;
    } finally {
        busy = false;
    }
}

calibrate.addEventListener('click', async () => {
    if (busy || !selector.value || !window.confirm('Czy ostatnie 30 okien zawiera wyłącznie zwykły ruch?')) return;
    busy = true;
    calibrate.disabled = true;
    try {
        await request('/api/calibration?' + selector.value, {method: 'POST'});
    } catch (error) {
        status.textContent = error.message;
        return;
    } finally {
        busy = false;
        calibrate.disabled = false;
    }
    await refresh();
});

exportButton.addEventListener('click', () => {
    const stream = new URLSearchParams(selector.value);
    const rows = ['receivedAt,sensorId,bssid,channel,windowId,durationMs,beacon,deauth,disassoc,retry,score,mlAlert,thresholdAlert,gapBeforeMs'];
    for (const {window: w, score, mlAlert, thresholdAlert, gapBeforeMs} of [...selectedResults.windows].reverse()) {
        rows.push([w.receivedAt, stream.get('sensorId'), stream.get('bssid'), stream.get('channel'),
            w.windowId, w.durationMs, w.beacon, w.deauth, w.disassoc, w.retry, score, mlAlert,
            thresholdAlert, gapBeforeMs].join(','));
    }
    const url = URL.createObjectURL(new Blob([rows.join('\n') + '\n'], {type: 'text/csv'}));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'wifi-windows.csv';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
});

selector.addEventListener('change', () => {
    calibrate.disabled = true;
    exportButton.disabled = true;
    refresh();
});
refresh();
setInterval(refresh, 5000);
