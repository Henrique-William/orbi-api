/*
 * Orbi Portal — gráficos leves (HTML + SVG), responsivos e com dicas (data-tip).
 */
(function () {
  'use strict';
  const P = window.OrbiPortal;
  const C = P.charts = {};
  const esc = P.esc;
  const tip = html => `data-tip="${esc(html)}"`;

  function niceMax(v) {
    if (v <= 0) return 1;
    const p = Math.pow(10, Math.floor(Math.log10(v)));
    const n = v / p;
    return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p;
  }
  function ticks(max, n) { const out = []; for (let i = 0; i <= n; i++) out.push(max * i / n); return out; }
  function every(labels) { const n = labels.length; return n > 20 ? Math.ceil(n / 8) : n > 10 ? 2 : 1; }
  const fmt = v => v >= 1000 ? P.num(v / 1000, 1) + ' mil' : P.num(v, v % 1 ? 1 : 0);

  // Linhas/áreas: series = [{ name, color, values, area }]
  C.line = (labels, series, opts) => {
    opts = opts || {};
    const N = labels.length;
    const max = opts.max || niceMax(Math.max(1, ...series.flatMap(s => s.values)));
    const ts = ticks(max, 4);
    const y = v => 100 - (v / max) * 100;
    const paths = series.map(s => {
      const pts = s.values.map((v, i) => `${i + 0.5},${y(v).toFixed(2)}`);
      const line = `<polyline points="${pts.join(' ')}" fill="none" stroke="${s.color}" stroke-width="2.5" vector-effect="non-scaling-stroke" stroke-linejoin="round" stroke-linecap="round" ${s.dash ? 'stroke-dasharray="5 5"' : ''}/>`;
      const area = s.area ? `<polygon points="0.5,100 ${pts.join(' ')} ${N - 0.5},100" fill="${s.color}" opacity=".12"/>` : '';
      return area + line;
    }).join('');
    const dots = series.filter(s => !s.dash).map(s => s.values.map((v, i) => `<i class="ch-dot" style="left:${(i + 0.5) / N * 100}%;bottom:${100 - y(v)}%;background:${s.color}"></i>`).join('')).join('');
    const k = every(labels);
    return `<div class="chart" style="--h:${opts.height || 220}px">
      <div class="ch-y">${ts.map(t => `<span style="bottom:${t / max * 100}%">${fmt(t)}${opts.unit || ''}</span>`).join('')}</div>
      <div class="ch-plot"><div class="ch-grid">${ts.map(t => `<i style="bottom:${t / max * 100}%"></i>`).join('')}</div>
        <svg viewBox="0 0 ${N} 100" preserveAspectRatio="none" aria-hidden="true">${paths}</svg><div class="ch-dots">${dots}</div>
        <div class="ch-hits">${labels.map((l, i) => `<div ${tip(`<b>${esc(opts.tipLabels ? opts.tipLabels[i] : l)}</b>${series.map(s => `<div><i style="background:${s.color}"></i>${esc(s.name)}: <b>${fmt(s.values[i])}${opts.unit || ''}</b></div>`).join('')}`)}></div>`).join('')}</div></div>
      <div class="ch-x">${labels.map((l, i) => `<span>${i % k === 0 ? esc(l) : ''}</span>`).join('')}</div></div>`;
  };

  // Barras empilhadas: stacks = [{ name, color, values }]
  C.stacked = (labels, stacks, opts) => {
    opts = opts || {};
    const totals = labels.map((_, i) => stacks.reduce((a, s) => a + s.values[i], 0));
    const max = niceMax(Math.max(1, ...totals));
    const ts = ticks(max, 4);
    const k = every(labels);
    return `<div class="chart" style="--h:${opts.height || 220}px">
      <div class="ch-y">${ts.map(t => `<span style="bottom:${t / max * 100}%">${fmt(t)}</span>`).join('')}</div>
      <div class="ch-plot"><div class="ch-grid">${ts.map(t => `<i style="bottom:${t / max * 100}%"></i>`).join('')}</div>
        <div class="ch-bars">${labels.map((l, i) => `<div class="ch-col ${opts.highlightLast && i === labels.length - 1 ? 'hl' : ''}" ${tip(`<b>${esc(opts.tipLabels ? opts.tipLabels[i] : l)}</b>${stacks.map(s => `<div><i style="background:${s.color}"></i>${esc(s.name)}: <b>${fmt(s.values[i])}</b></div>`).join('')}<div class="tip-sum">Total: <b>${fmt(totals[i])}</b></div>`)}>
          <div class="ch-stack" style="height:${totals[i] / max * 100}%">${stacks.map(s => s.values[i] ? `<i style="flex:${s.values[i]};background:${s.color}"></i>` : '').join('')}</div></div>`).join('')}</div></div>
      <div class="ch-x">${labels.map((l, i) => `<span>${i % k === 0 ? esc(l) : ''}</span>`).join('')}</div></div>`;
  };

  // Rosca: segs = [{ label, value, color }]
  C.donut = (segs, center, sub) => {
    const total = segs.reduce((a, s) => a + s.value, 0) || 1;
    let acc = 0; const R = 42, L = 2 * Math.PI * R;
    const arcs = segs.map(s => {
      const len = s.value / total * L; const off = -acc; acc += len;
      return `<circle r="${R}" cx="50" cy="50" fill="none" stroke="${s.color}" stroke-width="13" stroke-dasharray="${len} ${L - len}" stroke-dashoffset="${off}" transform="rotate(-90 50 50)" ${tip(`<b>${esc(s.label)}</b><div>${P.num(s.value)} · ${P.pct(s.value, total)}%</div>`)}/>`;
    }).join('');
    return `<div class="donut"><div class="donut-ring"><svg viewBox="0 0 100 100"><circle r="${R}" cx="50" cy="50" fill="none" stroke="var(--line)" stroke-width="13"/>${arcs}</svg>
      <div class="donut-c"><b>${center}</b><span>${sub || ''}</span></div></div>
      <ul class="legend">${segs.map(s => `<li ${tip(`<b>${esc(s.label)}</b><div>${P.num(s.value)} · ${P.pct(s.value, total)}%</div>`)}><i style="background:${s.color}"></i><span>${esc(s.label)}</span><b>${P.num(s.value)}</b><em>${P.pct(s.value, total)}%</em></li>`).join('')}</ul></div>`;
  };

  // Barras horizontais: items = [{ label, value, color, sub, href }]
  C.hbars = (items, opts) => {
    opts = opts || {};
    const max = Math.max(1, ...items.map(i => i.value));
    if (!items.length) return `<p class="muted small">${esc(opts.empty || 'Sem dados no período.')}</p>`;
    return `<div class="hbars">${items.map(i => `<${i.href ? `a href="${i.href}"` : 'div'} class="hbar" ${tip(`<b>${esc(i.label)}</b><div>${i.tip || P.num(i.value, opts.dec) + (opts.unit || '')}</div>`)}>
      <span class="hb-label">${esc(i.label)}${i.sub ? `<small>${esc(i.sub)}</small>` : ''}</span>
      <span class="hb-track"><i style="width:${i.value / max * 100}%;background:${i.color || 'var(--primary)'}"></i></span>
      <b class="hb-val">${P.num(i.value, opts.dec)}${opts.unit || ''}</b></${i.href ? 'a' : 'div'}>`).join('')}</div>`;
  };

  // Mapa de calor: rows x cols
  C.heat = (rows, cols, matrix, opts) => {
    opts = opts || {};
    const max = Math.max(1, ...matrix.flat());
    return `<div class="heat" style="--cols:${cols.length}"><span></span>${cols.map(c => `<span class="hx">${esc(c)}</span>`).join('')}
      ${rows.map((r, i) => `<span class="hy">${esc(r)}</span>${matrix[i].map((v, j) => `<i style="--a:${(v / max).toFixed(3)}" ${tip(`<b>${esc(r)} · ${esc(cols[j])}</b><div>${P.num(v)} ${opts.unit || 'entregas'}</div>`)}></i>`).join('')}`).join('')}</div>`;
  };

  C.spark = (values, color) => {
    const max = Math.max(1, ...values), min = Math.min(...values);
    const N = values.length;
    const pts = values.map((v, i) => `${(i / (N - 1) * 100).toFixed(2)},${(36 - (v - min) / Math.max(1, max - min) * 30).toFixed(2)}`).join(' ');
    return `<svg class="spark" viewBox="0 0 100 40" preserveAspectRatio="none" aria-hidden="true"><polygon points="0,40 ${pts} 100,40" fill="${color}" opacity=".12"/><polyline points="${pts}" fill="none" stroke="${color}" stroke-width="2" vector-effect="non-scaling-stroke"/></svg>`;
  };

  C.ring = (pct, color, label) => {
    const R = 40, L = 2 * Math.PI * R, len = Math.max(0, Math.min(100, pct)) / 100 * L;
    return `<div class="ring"><svg viewBox="0 0 100 100"><circle r="${R}" cx="50" cy="50" fill="none" stroke="var(--line)" stroke-width="10"/><circle r="${R}" cx="50" cy="50" fill="none" stroke="${color}" stroke-width="10" stroke-linecap="round" stroke-dasharray="${len} ${L}" transform="rotate(-90 50 50)"/></svg><div><b>${pct}%</b><span>${label || ''}</span></div></div>`;
  };
})();
