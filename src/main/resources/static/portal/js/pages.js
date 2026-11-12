/*
 * Orbi Portal — páginas, layout e roteador
 */
(function () {
  'use strict';
  const P = window.OrbiPortal;
  const C = P.charts;
  const D = P.D, M = P.M;
  const { $, $$, esc, ic } = P;
  const COLORS = { ok: '#0F9F6E', skip: '#E59A1A', cancel: '#D64545', blue: '#2557D6', sky: '#3E8EDE', violet: '#6E56CF', pink: '#C2417A', teal: '#14857A' };
  const ROUTE_COLORS = ['#2557D6', '#C2417A', '#14857A', '#6E56CF', '#D9622B', '#3E8EDE', '#5E8C31', '#A86B12'];
  const pages = {};
  // endereço do protótipo HTML do app (modo demonstração); null quando o portal é publicado sem ele
  const PROTO = (window.ORBI_CONFIG || {}).prototipoUrl === undefined ? '../prototipo/' : window.ORBI_CONFIG.prototipoUrl;
  const protoA = label => PROTO ? `<a href="${PROTO}" target="_blank" rel="noopener">${label}</a>` : `<b>${label}</b>`;
  let current = null, cleanup = null;

  // ==========================================================================
  // Navegação
  // ==========================================================================
  const NAV = [
    ['overview', 'Visão geral', 'grid', 'Operação'], ['live', 'Ao vivo', 'radar', 'Operação'], ['routes', 'Rotas', 'route', 'Operação'], ['occurrences', 'Ocorrências', 'alert', 'Operação'],
    ['drivers', 'Motoristas', 'users', 'Equipe'], ['messages', 'Mensagens', 'chat', 'Equipe'], ['reports', 'Relatórios', 'bars', 'Análise'], ['settings', 'Configurações', 'settings', 'Conta']
  ];
  const navItems = () => NAV.filter(n => !(P.API && n[0] === 'messages'));
  const CFG = window.ORBI_CONFIG || {};
  function syncPillHtml() {
    if (!P.API) return `<span class="sync-pill" id="syncPill" data-tip="&lt;b&gt;Conectado ao app&lt;/b&gt;&lt;div&gt;Mudanças feitas no app aparecem aqui na hora&lt;/div&gt;"><i></i><span>Ao vivo</span></span>`;
    const st = P.sync.state;
    const label = st === 'waking' ? 'Acordando servidor…' : st === 'error' ? 'Sem conexão' : st === 'loading' ? 'Conectando…' : 'Sincronizado';
    const cls = st === 'error' ? 'err' : (st === 'waking' || st === 'loading') ? 'warn' : '';
    const tip = `<b>Conectado à Orbi API</b><div>Mesmos dados do app do motorista</div><div>Atualiza a cada ${CFG.pollSeconds || 10} s · última: ${P.sync.last ? P.hhmm(P.sync.last) : '—'}</div>${P.sync.error ? `<div>${esc(P.sync.error)}</div>` : ''}`;
    return `<span class="sync-pill ${cls}" id="syncPill" data-tip="${esc(tip)}"><i></i><span>${label}</span></span>`;
  }
  P.onSyncState = () => { const el = $('#syncPill'); if (el) el.outerHTML = syncPillHtml(); };
  function badges() {
    const live = P.S.routes.filter(r => r.status === 'IN_PROGRESS').length;
    const occ = openOccurrences().length;
    return { live, occurrences: occ, messages: P.totalUnread(), routes: P.S.routes.filter(r => r.status === 'DELAYED' || r.status === 'CREATED').length };
  }
  function go(h) { if (location.hash === h) render(); else location.hash = h; }
  P.go = go;

  // trilha no topo: "Grupo / Página" ou o caminho informado pela página (ex.: Rotas / R-102)
  function crumbHtml(active, title, o) {
    const n = NAV.find(x => x[0] === active);
    const path = o.crumb && /<a /.test(o.crumb) ? o.crumb.split(' / ').slice(0, -1) : n ? [n[3]] : [];
    return `<span class="crumb-path">${path.map(x => `${x}<span class="sep">/</span>`).join('')}<span class="cur">${esc(title)}</span></span>`;
  }
  function sessBannerHtml() {
    if (!P.API || !P.sessionExpiresIn) return '';
    const left = P.sessionExpiresIn() * 1000;
    if (left > 3 * 60000 || left <= 0) return '';
    return `<div class="sess-banner" id="sessBanner">${ic('clock', 'sm')}<span>Sua sessão expira em ${Math.max(1, Math.round(left / 60000))} min. Salve o que estiver fazendo e entre de novo para continuar.</span><button class="btn sm ghost" id="reLogin">Entrar de novo</button></div>`;
  }
  function layout(active, title, body, opts) {
    const o = opts || {}; const isPath = o.crumb && /<a /.test(o.crumb);
    const u = P.me(); const b = badges();
    const unreadN = P.notifs().filter(n => !n.read).length;
    return `<div class="app">
      <aside class="side" id="side">
        <a class="brand" href="#/overview"><span class="brand-logo">${P.LOGO}</span><span><b>Orbi</b><small>Portal da empresa</small></span></a>
        <nav class="side-nav">${navItems().map((n, i, all) => `${!i || all[i - 1][3] !== n[3] ? `<div class="side-group">${n[3]}</div>` : ''}<a href="#/${n[0]}" class="${active === n[0] ? 'on' : ''}">${ic(n[2])}<span>${n[1]}</span>${b[n[0]] ? `<em class="${n[0] === 'occurrences' ? 'red' : ''}">${b[n[0]]}</em>` : ''}</a>`).join('')}</nav>
        <div class="side-foot">
          ${P.API ? `<a class="side-app" href="?demo" target="_blank" rel="noopener">${ic('eye', 'sm')}<span>Ver modo demonstração</span></a>` : PROTO ? `<a class="side-app" href="${PROTO}" target="_blank" rel="noopener">${ic('link', 'sm')}<span>Abrir app do motorista</span></a>` : ''}
          <div class="side-co">${ic('building', 'sm')}<span><b>${esc(P.S.company.name)}</b><small>${P.drivers().length} motoristas · ${P.admins().length} gestores</small></span></div>
        </div>
      </aside>
      <div class="side-scrim" id="sideScrim"></div>
      <div class="main">
        <header class="top">
          <button class="icon-btn only-m" id="menuBtn" aria-label="Menu">${ic('menu')}</button>
          <div class="top-title">${crumbHtml(active, title, o)}</div>
          <button class="search-btn" id="searchBtn" aria-label="Buscar">${ic('search', 'sm')}<span>Buscar rotas, motoristas…</span><kbd>Ctrl K</kbd></button>
          ${syncPillHtml()}
          <span class="top-sep only-d"></span>
          <button class="icon-btn" id="themeBtn" aria-label="Alternar tema">${ic(document.documentElement.dataset.theme === 'dark' ? 'sun' : 'moon')}</button>
          <button class="icon-btn" id="bellBtn" aria-label="Notificações">${ic('bell')}${unreadN ? `<span class="dot">${unreadN}</span>` : ''}</button>
          <button class="user-btn" id="userBtn"><span class="avatar sm">${P.initials(u.name)}</span><span class="only-d"><b>${esc(P.first(u.name))}</b><small>${esc(u.title || 'Gestor')}</small></span>${ic('chevd', 'sm only-d')}</button>
        </header>
        ${sessBannerHtml()}
        <main class="page" id="page">${o.noHead ? '' : `<div class="page-header"><div><h1>${esc(title)}</h1>${o.crumb && !isPath ? `<p>${o.crumb}</p>` : ''}</div>${o.actions ? `<div class="page-actions">${o.actions}</div>` : ''}</div>`}${body}</main>
      </div>
      <nav class="bnav">${[NAV[0], NAV[1], NAV[2], NAV[4]].map(n => `<a href="#/${n[0]}" class="${active === n[0] ? 'on' : ''}">${ic(n[2])}<span>${n[1]}</span>${b[n[0]] ? `<em>${b[n[0]]}</em>` : ''}</a>`).join('')}<button id="moreBtn" class="${['drivers', 'reports', 'messages', 'settings'].includes(active) ? 'on' : ''}">${ic('menu')}<span>Mais</span>${b.messages ? '<em>' + b.messages + '</em>' : ''}</button></nav>
    </div>`;
  }
  function bindLayout(root) {
    const side = $('#side', root), scrim = $('#sideScrim', root);
    const open = () => { side.classList.add('open'); scrim.classList.add('on'); };
    const close = () => { side.classList.remove('open'); scrim.classList.remove('on'); };
    $('#menuBtn', root).onclick = open; $('#moreBtn', root).onclick = open; scrim.onclick = close;
    $$('.side-nav a', root).forEach(a => a.addEventListener('click', close));
    $('#searchBtn', root).onclick = palette;
    $('#themeBtn', root).onclick = () => { setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'); render(); };
    $('#bellBtn', root).onclick = e => { e.stopPropagation(); notifPanel(); };
    $('#userBtn', root).onclick = e => { e.stopPropagation(); userMenu(); };
    const rl = $('#reLogin', root); if (rl) rl.onclick = () => { P.setSession(null); go('#/login'); };
  }

  // ==========================================================================
  // Pop-overs: notificações, usuário, busca (Ctrl K)
  // ==========================================================================
  function popover(html, anchor) {
    $$('.pop').forEach(p => p.remove());
    const p = document.createElement('div'); p.className = 'pop'; p.innerHTML = html;
    document.body.appendChild(p);
    const r = $(anchor).getBoundingClientRect();
    p.style.top = (r.bottom + 8) + 'px';
    p.style.right = Math.max(8, innerWidth - r.right) + 'px';
    setTimeout(() => document.addEventListener('click', function off(e) { if (!p.contains(e.target)) { p.remove(); document.removeEventListener('click', off); } }), 0);
    return p;
  }
  function portalLink(link) {
    if (!link) return '#/occurrences';
    const m = /#\/a\/route\/(\w+)/.exec(link); if (m) return '#/route/' + m[1];
    const v = /#\/a\/vehicle\/(\w+)/.exec(link); if (v) return '#/driver/' + v[1];
    if (/dashboard/.test(link)) return '#/overview';
    return '#/occurrences';
  }
  function notifPanel() {
    const list = P.notifs().slice(0, 30);
    const p = popover(`<div class="pop-head"><b>Notificações</b>${list.some(n => !n.read) ? '<button class="link" id="readAll">Marcar todas como lidas</button>' : ''}</div>
      <div class="pop-list">${list.length ? list.map(n => `<a href="${portalLink(n.link)}" class="nt ${n.read ? '' : 'unread'}" data-n="${n.id}"><span class="nt-ic">${ic(/🚨|seguran|sos/i.test(n.title) ? 'shield' : /priorid|urgente|ciente/i.test(n.title) ? 'zap' : /coment|revis|resposta/i.test(n.title) ? 'chat' : /cancel|atras/i.test(n.title) ? 'alert' : 'route', 'sm')}</span><span><b>${esc(n.title)}</b><small>${esc(n.text)}</small><em>${P.rel(n.at)}</em></span></a>`).join('') : '<p class="muted small" style="padding:16px">Nenhuma notificação.</p>'}</div>`, '#bellBtn');
    const ra = $('#readAll', p); if (ra) ra.onclick = () => { P.act.readAllNotifs(); p.remove(); render(); };
    $$('[data-n]', p).forEach(a => a.addEventListener('click', () => { P.act.readNotif(a.dataset.n); p.remove(); }));
  }
  function userMenu() {
    const u = P.me();
    const p = popover(`<div class="pop-user"><span class="avatar">${P.initials(u.name)}</span><span><b>${esc(u.name)}</b><small>${esc(u.email)}</small><small>${esc(u.title || 'Gestor')} · ${esc(P.S.company.name)}</small></span></div>
      <a class="pop-item" href="#/settings">${ic('settings', 'sm')} Configurações</a>
      <button class="pop-item" id="kb">${ic('keyboard', 'sm')} Atalhos do teclado</button>
      ${P.API || !PROTO ? '' : `<a class="pop-item" href="${PROTO}" target="_blank" rel="noopener">${ic('link', 'sm')} Abrir app do motorista</a>`}
      <button class="pop-item danger" id="out">${ic('logout', 'sm')} Sair</button>`, '#userBtn');
    $('#out', p).onclick = () => { P.setSession(null); p.remove(); P.toast('Sessão encerrada', 'logout'); go('#/login'); };
    $('#kb', p).onclick = () => { p.remove(); P.modal('Atalhos do teclado', `<ul class="kbd-list"><li><kbd>Ctrl</kbd> + <kbd>K</kbd><span>Buscar e executar ações</span></li><li><kbd>N</kbd><span>Nova rota</span></li><li><kbd>G</kbd> depois <kbd>V</kbd>/<kbd>A</kbd>/<kbd>R</kbd>/<kbd>M</kbd>/<kbd>O</kbd><span>Ir para Visão geral, Ao vivo, Rotas, Motoristas, Ocorrências</span></li><li><kbd>Esc</kbd><span>Fechar janelas</span></li></ul>`); };
  }
  function palette() {
    const items = [];
    navItems().forEach(n => items.push({ t: n[1], s: 'Página', icon: n[2], go: '#/' + n[0] }));
    items.push({ t: 'Nova rota', s: 'Ação', icon: 'plus', go: '#/new' });
    if (!P.API) items.push({ t: 'Novo motorista', s: 'Ação', icon: 'users', run: newDriverModal });
    items.push( { t: 'Alternar tema claro/escuro', s: 'Ação', icon: 'moon', run: () => { setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'); render(); } }, { t: 'Imprimir relatório', s: 'Ação', icon: 'printer', go: '#/reports', after: () => setTimeout(() => window.print(), 400) });
    P.S.routes.forEach(r => { const d = P.user(r.driverId); items.push({ t: `${r.code} · ${d ? d.name : 'sem motorista'}`, s: P.ROUTE_STATUS[r.status][0] + ' · ' + r.window, icon: 'route', go: '#/route/' + r.id, k: r.stops.map(P.stopAddr).join(' ') }); });
    P.drivers().forEach(d => items.push({ t: d.name, s: 'Motorista · ' + d.vehicle, icon: 'truck', go: '#/driver/' + d.id, k: d.email }));
    P.modal('Buscar', `<div class="pal"><div class="input">${ic('search', 'sm')}<input id="q" placeholder="Digite uma rota, motorista, endereço ou ação…" autocomplete="off"></div><div class="pal-list" id="pl"></div><p class="muted small" style="margin:10px 0 0">↑ ↓ para navegar · Enter para abrir · Esc para fechar</p></div>`, (m, close) => {
      const q = $('#q', m), pl = $('#pl', m); let sel = 0, res = [];
      const draw = () => {
        const t = q.value.trim().toLowerCase();
        res = items.filter(i => !t || (i.t + ' ' + i.s + ' ' + (i.k || '')).toLowerCase().includes(t)).slice(0, 12);
        sel = Math.min(sel, Math.max(0, res.length - 1));
        pl.innerHTML = res.length ? res.map((i, k) => `<button class="pal-item ${k === sel ? 'on' : ''}" data-k="${k}">${ic(i.icon, 'sm')}<span><b>${esc(i.t)}</b><small>${esc(i.s)}</small></span>${ic('chev', 'sm')}</button>`).join('') : '<p class="muted small">Nada encontrado.</p>';
        $$('[data-k]', pl).forEach(b => b.onclick = () => run(res[+b.dataset.k]));
      };
      const run = i => { if (!i) return; close(); if (i.go) go(i.go); if (i.run) i.run(); if (i.after) i.after(); };
      q.oninput = () => { sel = 0; draw(); };
      q.onkeydown = e => { if (e.key === 'ArrowDown') { sel = Math.min(res.length - 1, sel + 1); draw(); e.preventDefault(); } else if (e.key === 'ArrowUp') { sel = Math.max(0, sel - 1); draw(); e.preventDefault(); } else if (e.key === 'Enter') run(res[sel]); };
      draw();
    });
  }
  let gPressed = 0;
  document.addEventListener('keydown', e => {
    if (!P.me()) return;
    const typing = /INPUT|TEXTAREA|SELECT/.test((document.activeElement || {}).tagName || '');
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); palette(); return; }
    if (typing || document.querySelector('.layer')) return;
    const k = e.key.toLowerCase();
    if (k === 'n') { go('#/new'); return; }
    if (k === 'g') { gPressed = Date.now(); return; }
    if (Date.now() - gPressed < 1200) { const map = { v: 'overview', a: 'live', r: 'routes', m: 'drivers', o: 'occurrences' }; if (map[k]) go('#/' + map[k]); gPressed = 0; }
  });

  // ==========================================================================
  // Peças comuns
  // ==========================================================================
  function healthDot(r) { const h = P.health(r); return `<span class="hdot ${h.worst}" data-tip="${esc(`<b>${{ ok: 'Tudo certo', warn: 'Requer atenção', bad: 'Ação necessária' }[h.worst]}</b>${h.checks.filter(c => c[0] !== 'ok').map(c => `<div>• ${esc(c[1])}</div>`).join('')}`)}"></span>`; }
  function routeFlags(r) {
    let h = '';
    if (P.S.incidents.some(i => i.routeId === r.id && i.status === 'OPEN')) h += `<span class="badge red">${ic('shield', 'xs')} SOS</span>`;
    if (P.prioRank(r)) h += `<span class="badge ${P.PRIORITY[r.priority.level][1]}">${ic('zap', 'xs')} ${P.PRIORITY[r.priority.level][0]}</span>`;
    return h;
  }
  function progress(pct, cls) { return `<span class="bar ${cls || ''}"><i style="width:${pct}%"></i></span>`; }
  function kpi(o) {
    const delta = o.delta == null ? '' : `<span class="delta ${o.delta >= 0 === (o.goodUp !== false) ? 'up' : 'down'}">${o.delta >= 0 ? '▲' : '▼'} ${Math.abs(o.delta)}${o.deltaUnit || '%'}</span>`;
    return `<a class="kpi card" href="${o.href || '#/reports'}" ${o.tip ? `data-tip="${esc(o.tip)}"` : ''}>
      <div class="kpi-top"><span class="kpi-ic" style="--c:${o.color}">${ic(o.icon, 'sm')}</span><span class="kpi-label">${o.label}</span>${delta}</div>
      <div class="kpi-val">${o.value}${o.sub ? `<small>${o.sub}</small>` : ''}</div>
      ${o.spark ? `<div class="kpi-spark">${C.spark(o.spark, o.color)}</div>` : ''}${o.foot ? `<div class="kpi-foot">${o.foot}</div>` : ''}</a>`;
  }
  function empty(icon, title, text) { return `<div class="empty">${ic(icon, 'lg')}<h3>${esc(title)}</h3><p>${esc(text)}</p></div>`; }
  function avg(a) { return a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0; }
  // taxa de sucesso por dia; dias sem operação herdam o valor anterior (não contam como 0%)
  function successSeries(days) {
    let last = null;
    const raw = days.map(d => { const t = d.delivered + d.skipped + d.cancelled; return t ? P.pct(d.delivered, t) : null; });
    const first = raw.find(v => v != null);
    return raw.map(v => { if (v != null) last = v; return v != null ? v : (last != null ? last : (first != null ? first : 0)); });
  }
  const workDays = days => days.filter(d => d.stops > 0);
  function driverStats(id, hist) {
    const ds = hist.map(d => d.drivers[id]).filter(Boolean);
    const sum = k => ds.reduce((a, x) => a + x[k], 0);
    const stops = sum('stops'), del = sum('delivered');
    return { days: ds.length, routes: sum('routes'), stops, delivered: del, skipped: sum('skipped'), cancelled: sum('cancelled'), km: sum('km'), minutes: sum('minutes'), success: P.pct(del, stops), onTime: P.pct(sum('onTime'), sum('routes') || 1), rating: ds.length ? avg(ds.map(x => x.rating)) : 0, series: hist.map(d => d.drivers[id] ? d.drivers[id].delivered : 0) };
  }

  // Mapa compartilhado (pan/zoom + motoristas ao vivo)
  function ensureCity() {
    if ($('#orbi-city-defs')) return;
    const defs = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    defs.setAttribute('id', 'orbi-city-defs'); defs.setAttribute('style', 'position:absolute;width:0;height:0;overflow:hidden'); defs.setAttribute('aria-hidden', 'true');
    defs.innerHTML = `<defs><g id="orbi-city">${M.citySvg()}</g></defs>`;
    document.body.appendChild(defs);
  }
  function liveMap(el, routes, opts) {
    opts = opts || {};
    ensureCity();
    const pts = [D.DEPOT].concat(routes.flatMap(r => r.stops));
    let minX = Math.min(...pts.map(p => p.x)), maxX = Math.max(...pts.map(p => p.x)), minY = Math.min(...pts.map(p => p.y)), maxY = Math.max(...pts.map(p => p.y));
    const pad = 140; const fit = () => {
      const w = el.clientWidth || 600, h = el.clientHeight || 400;
      let vw = maxX - minX + pad * 2, vh = maxY - minY + pad * 2;
      if (vw / vh < w / h) vw = vh * w / h; else vh = vw * h / w;
      return { x: (minX + maxX) / 2 - vw / 2, y: (minY + maxY) / 2 - vh / 2, w: vw, h: vh };
    };
    let vb = opts.keep && opts.keep.vb ? opts.keep.vb : fit();
    const colorOf = r => ROUTE_COLORS[Math.max(0, P.S.routes.indexOf(r)) % ROUTE_COLORS.length];
    const k = () => Math.max(0.8, vb.w / (el.clientWidth || 600) * 1.1);
    el.innerHTML = `<svg class="lmap" preserveAspectRatio="xMidYMid slice"><use href="#orbi-city"/><g id="lr"></g><g id="ls"></g><g id="ld"></g></svg>
      <div class="lmap-ctl"><button class="icon-btn" data-z="in" aria-label="Aproximar">${ic('plus')}</button><button class="icon-btn" data-z="out" aria-label="Afastar">${ic('minus')}</button><button class="icon-btn" data-z="fit" aria-label="Centralizar">${ic('target')}</button></div>
      ${opts.legend !== false ? `<div class="lmap-legend">${routes.filter(r => r.status !== 'COMPLETED').slice(0, 6).map(r => `<span><i style="background:${colorOf(r)}"></i>${esc(r.code)}</span>`).join('')}</div>` : ''}`;
    const svg = $('svg', el);
    function draw() {
      const K = k();
      svg.setAttribute('viewBox', `${vb.x} ${vb.y} ${vb.w} ${vb.h}`);
      $('#lr', svg).innerHTML = routes.map(r => {
        const line = M.polyline(M.pathThrough([D.DEPOT].concat(r.stops)));
        const dim = opts.focus && opts.focus !== r.id;
        return `<polyline points="${line}" fill="none" stroke="${colorOf(r)}" stroke-width="${(r.status === 'IN_PROGRESS' ? 7 : 4.5) * K}" stroke-linecap="round" stroke-linejoin="round" opacity="${dim ? .18 : r.status === 'IN_PROGRESS' ? .9 : .55}" ${r.status === 'IN_PROGRESS' ? '' : `stroke-dasharray="${2 * K} ${10 * K}"`}/>`;
      }).join('');
      $('#ls', svg).innerHTML = M.depotMarker(0, K) + routes.map(r => r.stops.map((s, i) => (opts.focus && opts.focus !== r.id) ? '' : M.stopMarker(s, i + 1, M.kindOf(s.status), 0, K * 0.8)).join('')).join('');
      drawDrivers();
    }
    function drawDrivers() {
      const K = k();
      const g = $('#ld', svg);
      const live = routes.filter(r => r.status === 'IN_PROGRESS');
      const have = new Set();
      live.forEach(r => {
        const pos = P.livePos(r); if (!pos) return;
        const d = P.user(r.driverId); const sos = P.S.incidents.some(i => i.routeId === r.id && i.status === 'OPEN');
        let m = g.querySelector(`[data-r="${r.id}"]`);
        if (!m) {
          m = document.createElementNS('http://www.w3.org/2000/svg', 'g');
          m.setAttribute('data-r', r.id); m.setAttribute('class', 'lv-drv' + (sos ? ' sos' : ''));
          m.innerHTML = `<circle class="lv-pulse" r="22"/><circle r="15" fill="${sos ? '#DC2626' : colorOf(r)}" stroke="#fff" stroke-width="3"/><text y="4.5" text-anchor="middle">${esc(P.initials(d ? d.name : '?'))}</text>`;
          m.addEventListener('click', () => opts.onPick && opts.onPick(r.id));
          g.appendChild(m);
        }
        m.setAttribute('data-tip', `<b>${esc(d ? d.name : '')} · ${esc(r.code)}</b><div>${sos ? 'Alerta de segurança ativo' : `Indo para ${esc(pos.next ? P.stopAddr(pos.next) : '—')}`}</div><div>Chegada em ~${pos.eta} min · em ${esc(P.streetAt(pos))}</div>`);
        m.style.transform = `translate(${pos.x}px, ${pos.y}px) scale(${K})`;
        m.style.opacity = opts.focus && opts.focus !== r.id ? .35 : 1;
        have.add(r.id);
      });
      $$('[data-r]', g).forEach(m => { if (!have.has(m.dataset.r)) m.remove(); });
    }
    // zoom e arraste
    const zoom = (f, cx, cy) => {
      const nw = Math.max(250, Math.min(3200, vb.w * f)); const real = nw / vb.w;
      const px = cx == null ? 0.5 : cx, py = cy == null ? 0.5 : cy;
      vb = { x: vb.x + vb.w * px * (1 - real), y: vb.y + vb.h * py * (1 - real), w: nw, h: vb.h * real };
      if (opts.keep) opts.keep.vb = vb; draw();
    };
    $$('[data-z]', el).forEach(b => b.onclick = () => { if (b.dataset.z === 'fit') { vb = fit(); if (opts.keep) opts.keep.vb = vb; draw(); } else zoom(b.dataset.z === 'in' ? 0.7 : 1.4); });
    svg.addEventListener('wheel', e => { e.preventDefault(); const r = svg.getBoundingClientRect(); zoom(e.deltaY > 0 ? 1.15 : 0.87, (e.clientX - r.left) / r.width, (e.clientY - r.top) / r.height); }, { passive: false });
    let drag = null;
    svg.addEventListener('pointerdown', e => { if (e.target.closest('.lv-drv')) return; drag = { x: e.clientX, y: e.clientY, vb: Object.assign({}, vb) }; svg.setPointerCapture(e.pointerId); svg.classList.add('drag'); });
    svg.addEventListener('pointermove', e => { if (!drag) return; const r = svg.getBoundingClientRect(); const s = Math.max(vb.w / r.width, vb.h / r.height); vb = { x: drag.vb.x - (e.clientX - drag.x) * s, y: drag.vb.y - (e.clientY - drag.y) * s, w: vb.w, h: vb.h }; svg.setAttribute('viewBox', `${vb.x} ${vb.y} ${vb.w} ${vb.h}`); });
    const end = () => { if (drag) { drag = null; svg.classList.remove('drag'); if (opts.keep) opts.keep.vb = vb; } };
    svg.addEventListener('pointerup', end); svg.addEventListener('pointercancel', end);
    draw();
    const t = setInterval(drawDrivers, 1000);
    return { stop: () => clearInterval(t), redraw: draw, focus(id) { opts.focus = id; const r = routes.find(x => x.id === id); if (r) { const p = P.livePos(r) || r.stops[0]; vb = { x: p.x - vb.w / 2, y: p.y - vb.h / 2, w: vb.w, h: vb.h }; if (opts.keep) opts.keep.vb = vb; } draw(); } };
  }

  // ==========================================================================
  // Ocorrências (lista unificada)
  // ==========================================================================
  function openOccurrences() { return occurrences().filter(o => o.open); }
  function occurrences() {
    const out = [];
    P.S.incidents.forEach(i => { const d = P.user(i.driverId); out.push({ id: 'i:' + i.id, kind: 'SOS', open: i.status === 'OPEN', at: i.at, icon: 'shield', tone: 'red', title: `${i.label} · ${d ? d.name : ''}`, sub: `Perto de ${i.street}${i.routeId ? ' · ' + (P.route(i.routeId) || {}).code : ''}`, status: i.status === 'OPEN' ? (i.seenAt ? 'Acompanhando' : 'Novo') : 'Resolvido', ref: i }); });
    P.S.routes.forEach(r => {
      const d = P.user(r.driverId);
      r.stops.forEach((s, idx) => {
        if (s.review && s.review.status === 'REQUESTED') out.push({ id: `rv:${r.id}:${s.id}`, kind: 'REVIEW', open: true, at: s.review.at, icon: 'chat', tone: 'violet', title: `Revisão pedida · ${r.code} #${String(idx + 1).padStart(2, '0')}`, sub: `${d ? d.name : ''}: ${s.review.text || P.STOP_STATUS[s.status][0]}`, status: 'Aguardando você', r, s });
        if (s.status === 'SKIPPED' || s.status === 'CANCELLED') out.push({ id: `st:${r.id}:${s.id}`, kind: s.status, open: P.isToday(r) && !(s.comments || []).some(c => c.role === 'ADMIN'), at: s.at, icon: s.status === 'SKIPPED' ? 'skip' : 'xcircle', tone: s.status === 'SKIPPED' ? 'amber' : 'red', title: `${P.STOP_STATUS[s.status][0]} · ${r.code} #${String(idx + 1).padStart(2, '0')}`, sub: `${s.reason || ''} · ${P.stopAddr(s)}`, status: (s.comments || []).some(c => c.role === 'ADMIN') ? 'Respondida' : 'Sem resposta', r, s });
      });
      if (r.status === 'DELAYED') out.push({ id: 'dl:' + r.id, kind: 'DELAY', open: true, at: (P.windowStart(r) || new Date()).toISOString(), icon: 'clock', tone: 'red', title: `Rota atrasada · ${r.code}`, sub: `${d ? d.name : 'Sem motorista'} · janela ${r.window}`, status: r.priority ? 'Alerta enviado' : 'Sem ação', r });
      if (r.status === 'CREATED') out.push({ id: 'nd:' + r.id, kind: 'NODRIVER', open: true, at: r.createdAt, icon: 'users', tone: 'amber', title: `Rota sem motorista · ${r.code}`, sub: `${r.stops.length} paradas · ${r.window}`, status: 'Sem ação', r });
      if (r.priority && r.priority.requireAck && !r.priority.ackAt && r.status !== 'COMPLETED') out.push({ id: 'pa:' + r.id, kind: 'ACK', open: true, at: r.priority.at, icon: 'zap', tone: 'amber', title: `Aguardando “Ciente” · ${r.code}`, sub: `${d ? d.name : ''} ainda não confirmou o alerta`, status: 'Aguardando', r });
    });
    return out.sort((a, b) => (b.open - a.open) || (Date.parse(b.at || 0) - Date.parse(a.at || 0)));
  }

  // Detalhe de uma ocorrência / parada
  function stopModal(r, s) {
    const idx = r.stops.indexOf(s); const d = P.user(r.driverId);
    const st = P.STOP_STATUS[s.status];
    const p = s.proof || {};
    P.modal(`${r.code} · Parada #${String(idx + 1).padStart(2, '0')}`, `
      <div class="row wrap" style="gap:8px;margin-bottom:12px">${P.badge(P.STOP_STATUS, s.status)}${s.review ? `<span class="badge ${s.review.status === 'REQUESTED' ? 'violet' : 'green'}">${s.review.status === 'REQUESTED' ? 'Revisão pedida' : 'Revisada'}</span>` : ''}</div>
      <div class="kv">
        <div><span>Endereço</span><b>${esc(P.stopAddr(s))} · ${esc(s.district)}</b></div>
        <div><span>Destinatário</span><b>${esc(s.recipient)} · ${esc(s.phone)}</b></div>
        <div><span>Pacote</span><b>${s.packages.map(x => esc(x.desc) + ' (' + P.num(x.weight, 1) + ' kg)').join(', ')}</b></div>
        <div><span>Motorista</span><b>${d ? esc(d.name) : '—'}</b></div>
        ${s.at ? `<div><span>Registrado</span><b>${P.date(s.at)} às ${P.hhmm(s.at)} (${P.rel(s.at)})</b></div>` : ''}
        ${s.reportDist != null ? `<div><span>Distância do endereço</span><b>${s.reportDist} m${s.reportDist > 150 ? ' <span class="badge amber">longe do endereço</span>' : ''}</b></div>` : ''}
        ${s.status === 'COMPLETED' ? `<div><span>Recebido por</span><b>${esc(p.receiver || '—')}${p.doc ? ' · ' + esc(p.doc) : ''}</b></div><div><span>Comprovante</span><b>${p.photo ? '<span class="photo-thumb">Foto anexada</span>' : 'Sem foto'}${p.note ? ` · “${esc(p.note)}”` : ''}</b></div>` : ''}
        ${s.reason ? `<div><span>Motivo informado</span><b class="txt-${st[1]}">${esc(s.reason)}</b></div>` : ''}
      </div>
      ${s.review && s.review.status === 'REQUESTED' ? `<div class="callout violet">${ic('chat', 'sm')}<span><b>O motorista pediu revisão</b> ${P.rel(s.review.at)}${s.review.text ? ` · “${esc(s.review.text)}”` : ''}</span></div>` : ''}
      ${!P.API ? '' : (s.details ? `<div class="callout neutral">${ic('edit', 'sm')}<span><b>Observação registrada no app:</b> ${esc(s.details)}</span></div>` : '')}
      ${P.API ? '<p class="muted small">Comentários e revisões ainda não existem na Orbi API.</p>' : `<h3 class="h3">Comentários</h3>
      <div class="thread">${(s.comments || []).length ? s.comments.map(c => `<div class="cmt ${c.role === 'ADMIN' ? 'me' : ''}"><b>${esc(c.by)}</b><span class="badge ${c.role === 'ADMIN' ? 'blue' : 'neutral'}">${c.role === 'ADMIN' ? 'Gestor' : 'Motorista'}</span><em>${P.rel(c.at)}</em><p>${esc(c.text)}</p></div>`).join('') : '<p class="muted small">Nenhum comentário ainda.</p>'}</div>
      ${P.TERMINAL.includes(s.status) ? `<textarea class="ta" id="cm" placeholder="Responder ao motorista (aparece no app dele)"></textarea>
      <div class="row end wrap" style="margin-top:10px">${s.review && s.review.status === 'REQUESTED' ? '<button class="btn success" id="res">' + ic('check', 'sm') + ' Responder e marcar como revisada</button>' : ''}<button class="btn primary" id="send">${ic('send', 'sm')} Enviar comentário</button></div>` : ''}`}`,
      (m, close) => {
        const cm = $('#cm', m);
        const sendc = (resolve) => { const t = cm.value.trim(); if (!t && !resolve) { cm.focus(); return; } P.act.comment(r.id, s.id, t, resolve); close(); P.toast(resolve ? 'Revisão concluída · motorista avisado' : 'Comentário enviado ao motorista', 'check'); render(); };
        const sb = $('#send', m); if (sb) sb.onclick = () => sendc(false);
        const rb = $('#res', m); if (rb) rb.onclick = () => sendc(true);
      }, { wide: true, noFocus: true });
  }
  function incidentModal(inc) {
    if (!inc.seenAt && inc.status === 'OPEN') { P.act.seeIncident(inc.id); inc = P.S.incidents.find(i => i.id === inc.id); }
    const d = P.user(inc.driverId); const r = inc.routeId && P.route(inc.routeId); const ins = P.insuranceOf(d); const v = P.vehicleOf(d);
    const urgent = ['ACCIDENT', 'HEALTH', 'ROBBERY'].includes(inc.type);
    P.modal(`Alerta de segurança · ${inc.label}`, `
      <div class="callout ${inc.status === 'OPEN' ? 'red' : 'green'}">${ic(inc.status === 'OPEN' ? 'shield' : 'check', 'sm')}<span><b>${inc.status === 'OPEN' ? 'Alerta ativo' : 'Resolvido ' + P.rel(inc.resolvedAt)}</b> · enviado ${P.rel(inc.at)} às ${P.hhmm(inc.at)}${inc.seenAt ? ` · visualizado às ${P.hhmm(inc.seenAt)}` : ''}</span></div>
      <div class="kv">
        <div><span>Motorista</span><b>${esc(d.name)} · ${esc(d.phone)}</b></div>
        <div><span>Localização enviada</span><b>Perto de ${esc(inc.street)}</b></div>
        ${r ? `<div><span>Rota</span><b><a href="#/route/${r.id}" data-close>${esc(r.code)}</a>${inc.paused ? ' · pausada' : ''}</b></div>` : ''}
        <div><span>Veículo</span><b>${esc(d.vehicle)} · ${P.OWNERSHIP[v.ownership][0].toLowerCase()}</b></div>
        <div><span>Assistência 24h</span><b>${ins.phone ? `${esc(ins.insurer || '')} · ${esc(ins.phone)}` : '<span class="txt-amber">Não cadastrada</span>'}</b></div>
        ${inc.note ? `<div><span>Detalhes do motorista</span><b>“${esc(inc.note)}”</b></div>` : ''}
        ${inc.resolution ? `<div><span>Resolução</span><b>${esc(inc.resolution)}</b></div>` : ''}
      </div>
      <div class="calls">${ins.phone && ['BREAKDOWN', 'TIRE', 'ACCIDENT'].includes(inc.type) ? `<button class="call" data-call="${esc(ins.insurer || 'Assistência')} ${esc(ins.phone)}">${ic('phone', 'sm')}<span><b>${esc(ins.phone)}</b><small>${esc(ins.insurer || 'Assistência 24h')}</small></span></button>` : ''}
        <button class="call" data-call="${esc(d.name)} ${esc(d.phone)}">${ic('phone', 'sm')}<span><b>${esc(d.phone)}</b><small>Ligar para o motorista</small></span></button>
        ${urgent ? `<button class="call red" data-call="${inc.type === 'ROBBERY' ? 'Polícia 190' : 'SAMU 192'}">${ic('phone', 'sm')}<span><b>${inc.type === 'ROBBERY' ? '190' : '192'}</b><small>${inc.type === 'ROBBERY' ? 'Polícia' : 'SAMU'}</small></span></button>` : ''}</div>
      ${inc.status === 'OPEN' ? `<textarea class="ta" id="res" placeholder="Como foi resolvido? (ex.: guincho a caminho)"></textarea>
      <div class="row end wrap" style="margin-top:10px">${r ? `<a class="btn ghost" href="#/messages/${r.id}" data-close>${ic('chat', 'sm')} Conversa</a><button class="btn ghost" id="reas">${ic('users', 'sm')} Reatribuir rota</button>` : ''}<button class="btn success" id="done">${ic('check', 'sm')} Marcar como resolvido</button></div>` : ''}`,
      (m, close) => {
        $$('[data-call]', m).forEach(b => b.onclick = () => P.toast(`Ligando para ${b.dataset.call}… (simulado)`, 'phone'));
        const dn = $('#done', m); if (dn) dn.onclick = () => { P.act.resolveIncident(inc.id, $('#res', m).value.trim()); close(); P.toast('Alerta encerrado · motorista avisado', 'check'); render(); };
        const ra = $('#reas', m); if (ra) ra.onclick = () => assignModal(r);
      }, { wide: true, noFocus: true });
  }
  function openOccurrence(o) {
    if (o.kind === 'SOS') incidentModal(o.ref);
    else if (o.s) stopModal(o.r, o.s);
    else if (o.kind === 'DELAY') priorityModal(o.r);
    else if (o.kind === 'NODRIVER') assignModal(o.r);
    else if (o.kind === 'ACK') go('#/messages/' + o.r.id);
  }

  // Ações em rota
  function priorityModal(r) {
    const d = P.user(r.driverId);
    if (!d) { assignModal(r); P.toast('Atribua um motorista antes do alerta', 'info'); return; }
    const TPL = ['Rota atrasada: priorize estas entregas.', 'Cliente aguardando, entregue o quanto antes.', 'Carga sensível: siga direto para as paradas.', 'Comece por esta rota antes das outras.'];
    let level = r.status === 'DELAYED' ? 'URGENT' : 'HIGH';
    P.modal(`Alerta de prioridade · ${r.code}`, `<p class="muted" style="margin-top:0">Para <b>${esc(d.name)}</b>. Aparece em destaque no app do motorista.</p>
      <div class="seg2">${Object.keys(P.PRIORITY).map(k => `<button class="opt ${level === k ? 'on' : ''}" data-l="${k}"><b>${P.PRIORITY[k][0]}</b><small>${k === 'URGENT' ? 'Aviso em tela cheia no app' : 'Destaque na lista de rotas'}</small></button>`).join('')}</div>
      <label class="lbl">Mensagem</label><div class="chips wrap">${TPL.map((t, i) => `<button class="chip" data-t="${i}">${esc(t)}</button>`).join('')}</div>
      <textarea class="ta" id="msg">${r.status === 'DELAYED' ? esc(TPL[0]) : ''}</textarea>
      <label class="check"><input type="checkbox" id="ack" checked> Exigir confirmação “Ciente” do motorista</label>
      <div class="row end" style="margin-top:14px">${r.priority ? '<button class="btn ghost" id="clr">Remover prioridade</button>' : ''}<button class="btn danger" id="send">${ic('zap', 'sm')} Enviar alerta</button></div>`, (m, close) => {
      $$('[data-l]', m).forEach(b => b.onclick = () => { level = b.dataset.l; $$('[data-l]', m).forEach(x => x.classList.toggle('on', x === b)); });
      $$('[data-t]', m).forEach(b => b.onclick = () => { $('#msg', m).value = TPL[+b.dataset.t]; });
      const clr = $('#clr', m); if (clr) clr.onclick = () => { P.act.clearPriority(r.id); close(); P.toast('Prioridade removida', 'check'); render(); };
      $('#send', m).onclick = () => { P.act.priority(r.id, level, $('#msg', m).value.trim() || TPL[0], $('#ack', m).checked); close(); P.toast(`Alerta enviado para ${P.first(d.name)} · aparece no app agora`, 'zap'); render(); };
    }, { noFocus: true });
  }
  function assignModal(r) {
    let pick = r.driverId;
    P.modal(`Atribuir motorista · ${r.code}`, `<div class="pick-list">${P.drivers().map(d => { const st = P.driverState(d); return `<button class="pick ${pick === d.id ? 'on' : ''}" data-d="${d.id}"><span class="avatar">${P.initials(d.name)}</span><span><b>${esc(d.name)}</b><small>${esc(d.vehicle)}${P.API ? '' : ` · nota ${P.num(d.rating || 0, 1)}`}</small></span><span class="badge ${st[1]}">${st[0]}</span></button>`; }).join('')}</div>
      ${r.status === 'IN_PROGRESS' ? '<div class="callout amber">' + ic('info', 'sm') + '<span>A rota está em andamento. Ao trocar, ela volta para “A iniciar” com o novo motorista.</span></div>' : ''}
      <div class="row end" style="margin-top:14px"><button class="btn primary" id="ok">Salvar</button></div>`, (m, close) => {
      $$('[data-d]', m).forEach(b => b.onclick = () => { pick = b.dataset.d; $$('[data-d]', m).forEach(x => x.classList.toggle('on', x === b)); });
      $('#ok', m).onclick = () => { if (pick && pick !== r.driverId) { P.act.assign(r.id, pick); P.toast(`${P.user(pick).name} atribuído · notificado no app`, 'check'); } close(); render(); };
    }, { noFocus: true });
  }

  // ==========================================================================
  // LOGIN — somente empresa / gestor
  // ==========================================================================
  pages.login = () => ({
    bare: true,
    html: `<div class="login">
      <section class="login-form">
        <div class="lf-top"><span class="brand-logo">${P.LOGO}</span>Orbi</div>
        <form id="f" novalidate>
        <h2>Entrar</h2><p class="muted">Portal de operações para empresas e gestores.</p>
        <label class="lbl" for="em">${P.API ? 'E-mail ou usuário' : 'E-mail corporativo'}</label><div class="input" id="emw">${ic('mail', 'sm')}<input id="em" type="${P.API ? 'text' : 'email'}" autocomplete="username" placeholder="nome@empresa.com"></div>
        <label class="lbl" for="pw">Senha</label><div class="input" id="pww">${ic('lock', 'sm')}<input id="pw" type="password" autocomplete="current-password" placeholder="Sua senha"><button type="button" class="icon-btn sm" id="eye" aria-label="Mostrar senha">${ic('eye', 'sm')}</button></div>
        <div class="form-err" id="err" role="alert"></div>
        <button class="btn primary block lg" id="btn">Entrar</button>
        <div class="row between" style="margin-top:14px"><button type="button" class="link" id="forgot">Esqueci minha senha</button>${P.API ? `<a class="link" href="?demo">Ver demonstração</a>` : ''}</div>
        ${P.API ? `<p class="muted small" style="margin:18px 0 0">Use sua conta de gestor (perfil ADMIN) da Orbi API.</p>` : `<div class="or">contas de demonstração · senha orbi123</div>`}
        ${P.API ? '' : `<div class="demo-row"><button type="button" class="demo" data-demo="gestor@orbi.demo">${ic('users', 'sm')}<span><b>Gestor</b><small>Ana Souza</small></span></button><button type="button" class="demo" data-demo="empresa@orbi.demo">${ic('building', 'sm')}<span><b>Empresa</b><small>Roberto Almeida</small></span></button></div>`}
        <p class="muted small" style="margin:20px 0 0">É motorista? O acesso é pelo ${P.API ? 'app Orbi no celular' : protoA('app Orbi')}.</p>
      </form>
        <div class="lf-foot"><span>© ${new Date().getFullYear()} Orbi Logística</span><span>${P.API ? 'Conectado à Orbi API' : 'Modo demonstração'}</span></div></section>
      <section class="login-art">
        <span class="eyebrow">Orbi para empresas</span>
        <div class="login-copy"><h1>A operação de entregas inteira, em uma tela.</h1>
          <p>Acompanhe as rotas enquanto acontecem, resolva ocorrências antes que virem reclamação e meça o resultado de cada motorista.</p></div>
        <div class="login-feats"><div><b>Ao vivo</b><span>Posição e progresso de cada rota, sincronizados com o app do motorista.</span></div><div><b>Ocorrências</b><span>Atrasos, paradas puladas e rotas sem motorista em uma só fila.</span></div><div><b>Relatórios</b><span>Taxa de sucesso, custo por entrega e exportação em CSV ou PDF.</span></div></div>
      </section></div>`,
    mount(root) {
      const em = $('#em', root), pw = $('#pw', root), err = $('#err', root), btn = $('#btn', root);
      $('#eye', root).onclick = () => { pw.type = pw.type === 'password' ? 'text' : 'password'; };
      $('#forgot', root).onclick = () => P.toast(P.API ? 'A Orbi API ainda não tem recuperação de senha. Peça a redefinição ao administrador do sistema.' : 'Enviamos um link de redefinição para o e-mail (simulado)', 'mail');
      const fail = (msg, field) => { err.innerHTML = msg; ['#emw', '#pww'].forEach(s => $(s, root).classList.toggle('err', s === field)); };
      const login = (email, pass) => {
        // na Orbi API o administrador padrão usa o usuário "admin" (sem @), então no modo API aceitamos qualquer login
        if (P.API ? !email : !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return fail(P.API ? 'Informe seu e-mail ou usuário.' : 'Informe um e-mail válido.', '#emw');
        if (!pass) return fail('Informe sua senha.', '#pww');
        btn.disabled = true; btn.innerHTML = `${ic('refresh', 'sm spin')} Entrando…`;
        if (P.API) {
          const slow = setTimeout(() => { btn.innerHTML = `${ic('refresh', 'sm spin')} Acordando o servidor… (até 1 min)`; }, 6000);
          P.apiLogin(email, pass).then(() => { clearTimeout(slow); P.toast(`Sessão iniciada como ${P.first(P.me().name)}`, 'check'); go('#/overview'); })
            .catch(e => { clearTimeout(slow); btn.disabled = false; btn.textContent = 'Entrar';
              if (e.restricted) fail(`${ic('lock', 'sm')} <span><b>Acesso restrito.</b> O portal é exclusivo para empresas e gestores (perfil ADMIN). Motoristas usam o app Orbi.</span>`, '#emw');
              else fail(esc(e.message || 'Não foi possível entrar.'), e.status === 401 ? '#pww' : null); });
          return;
        }
        setTimeout(() => {
          btn.disabled = false; btn.textContent = 'Entrar';
          P.reload();
          const u = P.S.users.find(x => x.email.toLowerCase() === email.toLowerCase());
          if (!u || u.password !== pass) return fail('E-mail ou senha incorretos.', '#pww');
          if (u.role !== 'ADMIN') return fail(`${ic('lock', 'sm')} <span><b>Acesso restrito.</b> O portal é exclusivo para empresas e gestores. Motoristas usam o ${protoA('app Orbi')}.</span>`, '#emw');
          P.setSession({ userId: u.id, at: new Date().toISOString() });
          P.toast(`Sessão iniciada como ${P.first(u.name)}`, 'check');
          go('#/overview');
        }, 600);
      };
      $('#f', root).onsubmit = e => { e.preventDefault(); login(em.value.trim(), pw.value); };
      if (P.API && P.sessionExpired) fail('Sua sessão expirou (a Orbi API encerra o login após alguns minutos). Entre novamente.', null);
      $$('[data-demo]', root).forEach(b => b.onclick = () => { em.value = b.dataset.demo; pw.value = 'orbi123'; login(em.value, pw.value); });
    }
  });

  // ==========================================================================
  // VISÃO GERAL
  // ==========================================================================
  pages.overview = () => {
    const u = P.me(); const t = P.todayStats(); const hist = P.history(30); const h14 = hist.slice(-13); const h7 = hist.slice(-7);
    const goals = P.S.company.goals || { successRate: 92, onTime: 90 };
    const successToday = P.pct(t.delivered, t.delivered + t.skipped + t.cancelled);
    const avg7 = k => avg(h7.map(d => d[k]));
    const succ7 = avg(workDays(h7).map(d => P.pct(d.delivered, d.stops)));
    const hour = new Date().getHours();
    const hello = hour < 12 ? 'Bom dia' : hour < 18 ? 'Boa tarde' : 'Boa noite';
    const sos = P.openIncidents();
    const occ = openOccurrences();
    const todayRoutes = P.S.routes.filter(P.isToday).sort((a, b) => P.prioRank(b) - P.prioRank(a) || ['IN_PROGRESS', 'DELAYED', 'ASSIGNED', 'CREATED', 'COMPLETED'].indexOf(a.status) - ['IN_PROGRESS', 'DELAYED', 'ASSIGNED', 'CREATED', 'COMPLETED'].indexOf(b.status));
    const labels = h14.map(d => d.label).concat(['Hoje']);
    const reasons = {}; hist.forEach(d => Object.entries(d.reasons).forEach(([k, v]) => { reasons[k] = (reasons[k] || 0) + v; })); Object.entries(t.reasons).forEach(([k, v]) => { reasons[k] = (reasons[k] || 0) + v; });
    const rank = P.drivers().map(d => Object.assign({ d }, driverStats(d.id, h7))).sort((a, b) => b.success - a.success || b.delivered - a.delivered);
    const activeDrivers = P.drivers().filter(d => ['Em rota', 'SOS', 'Escalado'].includes(P.driverState(d)[0])).length;
    return {
      active: 'overview', title: 'Visão geral',
      crumb: `${(d => d.charAt(0).toUpperCase() + d.slice(1))(new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' }))} · ${t.live ? P.plural(t.live, 'rota em andamento', 'rotas em andamento') : 'nenhuma rota em andamento'} · ${occ.length ? P.plural(occ.length, 'pendência', 'pendências') : 'sem pendências'}`,
      actions: `<a class="btn ghost" href="#/reports">${ic('bars', 'sm')} Relatórios</a><a class="btn primary" href="#/new">${ic('plus', 'sm')} Nova rota</a>`,
      html: `
      ${sos.map(i => { const d = P.user(i.driverId); return `<button class="sos-strip" data-inc="${i.id}"><span class="pulse-ic">${ic('shield')}</span><span><b>Alerta de segurança · ${esc(d.name)}</b><small>${esc(i.label)} · perto de ${esc(i.street)} · ${P.rel(i.at)}${i.seenAt ? ' · você está acompanhando' : ' · ainda não visualizado'}</small></span><span class="btn sm light">Ver alerta</span></button>`; }).join('')}
      <div class="kpi-grid">
        ${kpi({ label: 'Entregas concluídas', icon: 'check', color: COLORS.ok, value: `${t.delivered}<span class="of">/${t.stops}</span>`, sub: `${t.pending} pendentes`, spark: h14.map(d => d.delivered).concat([t.delivered]), href: '#/routes', tip: `<b>Entregas de hoje</b><div>${P.pct(t.delivered, t.stops)}% das paradas do dia · média diária: ${P.num(avg7('delivered'))}</div>`, foot: `${P.pct(t.delivered, t.stops)}% do planejado para hoje` })}
        ${kpi({ label: 'Taxa de sucesso', icon: 'trend', color: COLORS.blue, value: `${successToday}%`, sub: `meta ${goals.successRate}%`, spark: successSeries(h14).concat([successToday]), href: '#/reports', delta: workDays(h7).length ? successToday - Math.round(succ7) : null, deltaUnit: ' p.p.', tip: '<b>Entregues ÷ paradas tratadas</b><div>Média dos últimos 7 dias: ' + Math.round(succ7) + '%</div>' })}
        ${kpi({ label: 'Rotas em andamento', icon: 'radar', color: COLORS.sky, value: `${t.live}<span class="of">/${t.routes}</span>`, sub: t.delayed ? P.plural(t.delayed, 'atrasada', 'atrasadas') : 'nenhuma atrasada', href: '#/live', foot: `${activeDrivers} de ${P.drivers().length} motoristas na operação` })}
        ${kpi({ label: 'Ocorrências abertas', icon: 'alert', color: COLORS.cancel, value: `${occ.length}`, sub: `${P.API ? '' : sos.length + ' SOS · '}${P.plural(t.skipped + t.cancelled, 'parada com problema', 'paradas com problema')}`, href: '#/occurrences', goodUp: false, foot: occ.length ? 'Ver e resolver' : 'Nenhuma pendência' })}
        ${P.API ? kpi({ label: 'Rotas concluídas hoje', icon: 'check', color: COLORS.violet, value: `${P.S.routes.filter(r => P.isToday(r) && r.status === 'COMPLETED').length}`, sub: `de ${t.routes} rotas`, href: '#/routes' }) : kpi({ label: 'Pontualidade · 7 dias', icon: 'clock', color: COLORS.violet, value: `${P.pct(h7.reduce((a, d) => a + d.onTime, 0), h7.reduce((a, d) => a + d.routes, 0) || 1)}%`, sub: `meta ${goals.onTime}%`, href: '#/reports', spark: h14.map(d => P.pct(d.onTime, d.routes || 1)), foot: `Hoje: ${t.onTimePct}% das rotas iniciadas no horário`, tip: '<b>Rotas iniciadas até 20 min após o início da janela</b>' })}
        ${kpi({ label: 'Distância prevista', icon: 'route', color: COLORS.teal, value: `${P.num(t.km, 1)}<span class="of"> km</span>`, sub: `média 7d ${P.num(avg7('km'), 0)} km`, href: '#/reports', spark: h14.map(d => d.km).concat([t.km]) })}
        ${kpi({ label: 'Custo estimado hoje', icon: 'wallet', color: COLORS.skip, value: P.money(t.cost), sub: t.delivered ? `${P.money(t.cost / Math.max(1, t.stops))} por parada` : '', href: '#/reports', tip: '<b>Estimativa</b><div>km × R$ 1,85 + R$ 2,50 por parada</div>' })}
        ${P.API ? kpi({ label: 'Motoristas', icon: 'users', color: '#F59E0B', value: `${activeDrivers}<span class="of">/${P.drivers().length}</span>`, sub: 'em rota ou escalados', href: '#/drivers' }) : kpi({ label: 'Avaliação média', icon: 'star', color: '#F59E0B', value: P.num(avg(P.drivers().map(d => d.rating || 0)), 1) + '<span class="of"> / 5</span>', sub: `${P.S.reviews.length} avaliações recentes`, href: '#/drivers' })}
      </div>
      <div class="grid g-2-1">
        <section class="card"><div class="card-head"><h3>Entregas nos últimos 14 dias</h3><a class="link" href="#/reports">Ver relatório ${ic('chev', 'xs')}</a></div>
          ${C.stacked(labels, [{ name: 'Entregues', color: COLORS.ok, values: h14.map(d => d.delivered).concat([t.delivered]) }, { name: 'Puladas', color: COLORS.skip, values: h14.map(d => d.skipped).concat([t.skipped]) }, { name: 'Canceladas', color: COLORS.cancel, values: h14.map(d => d.cancelled).concat([t.cancelled]) }], { highlightLast: true, tipLabels: h14.map(d => `${d.wd} ${d.label}`).concat(['Hoje (dados do app)']) })}
          <div class="legend-row"><span><i style="background:${COLORS.ok}"></i>Entregues</span><span><i style="background:${COLORS.skip}"></i>Puladas</span><span><i style="background:${COLORS.cancel}"></i>Canceladas</span></div></section>
        <section class="card"><div class="card-head"><h3>Paradas de hoje</h3></div>
          ${C.donut([{ label: 'Entregues', value: t.delivered, color: COLORS.ok }, { label: 'Pendentes', value: t.pending, color: '#94A3B8' }, { label: 'Puladas', value: t.skipped, color: COLORS.skip }, { label: 'Canceladas', value: t.cancelled, color: COLORS.cancel }], `${P.pct(t.delivered + t.skipped + t.cancelled, t.stops)}%`, 'tratadas')}</section>
      </div>
      <div class="grid g-1-1">
        <section class="card pad0"><div class="card-head pad"><h3>Frota ao vivo</h3><a class="link" href="#/live">Abrir mapa ${ic('chev', 'xs')}</a></div><div class="mini-map" id="ovMap"></div></section>
        <section class="card"><div class="card-head"><h3>Rotas de hoje</h3><a class="link" href="#/routes">Todas ${ic('chev', 'xs')}</a></div>
          <div class="rlist">${todayRoutes.length ? todayRoutes.map(r => { const inf = P.routeInfo(r); const d = P.user(r.driverId); return `<a class="rrow" href="#/route/${r.id}">${healthDot(r)}<span class="rr-main"><b>${esc(r.code)}</b><small>${d ? esc(d.name) : 'Sem motorista'} · ${esc(r.window)}</small></span><span class="rr-flags">${routeFlags(r)}${P.badge(P.ROUTE_STATUS, r.status)}</span><span class="rr-prog">${progress(inf.pct, r.status === 'COMPLETED' ? 'green' : '')}<small>${inf.handled}/${inf.total}</small></span></a>`; }).join('') : empty('route', 'Nenhuma rota hoje', 'Crie a primeira rota do dia.')}</div></section>
      </div>
      <div class="grid g-1-1">
        <section class="card"><div class="card-head"><h3>Motivos de ocorrência · 30 dias</h3><a class="link" href="#/occurrences">Ocorrências ${ic('chev', 'xs')}</a></div>
          ${C.hbars(Object.entries(reasons).sort((a, b) => b[1] - a[1]).slice(0, 6).map(([k, v], i) => ({ label: k, value: v, color: i === 0 ? COLORS.cancel : i < 3 ? COLORS.skip : '#94A3B8' })))}</section>
        <section class="card"><div class="card-head"><h3>Ranking de motoristas · 7 dias</h3><a class="link" href="#/drivers">Motoristas ${ic('chev', 'xs')}</a></div>
          <div class="rank">${rank.map((x, i) => `<a class="rank-row" href="#/driver/${x.d.id}"><span class="pos p${i + 1}">${i + 1}º</span><span class="avatar sm">${P.initials(x.d.name)}</span><span class="rk-main"><b>${esc(x.d.name)}</b><small>${x.delivered} entregas${P.API ? '' : ` · nota ${P.num(x.d.rating || 0, 1)}`}</small></span><span class="rk-val"><b>${x.success}%</b><small>sucesso</small></span></a>`).join('')}</div></section>
      </div>`,
      mount(root) {
        $$('[data-inc]', root).forEach(b => b.onclick = () => incidentModal(P.S.incidents.find(i => i.id === b.dataset.inc)));
        const mm = (P.API ? P.geoMap : liveMap)($('#ovMap', root), P.S.routes.filter(r => P.isToday(r) && r.status !== 'COMPLETED'), { legend: true, onPick: id => go('#/route/' + id) });
        return () => mm.stop();
      }
    };
  };

  // ==========================================================================
  // AO VIVO
  // ==========================================================================
  const liveState = { vb: null, focus: null, filter: 'ALL' };
  pages.live = () => {
    const rs = P.S.routes.filter(r => P.isToday(r) && r.status !== 'COMPLETED');
    const FL = [['ALL', 'Todas', rs.length], ['IN_PROGRESS', 'Em andamento', rs.filter(r => r.status === 'IN_PROGRESS').length], ['LATE', 'Atenção', rs.filter(r => P.health(r).worst !== 'ok').length], ['SOS', 'SOS', rs.filter(r => P.S.incidents.some(i => i.routeId === r.id && i.status === 'OPEN')).length]];
    const list = rs.filter(r => liveState.filter === 'ALL' || (liveState.filter === 'IN_PROGRESS' && r.status === 'IN_PROGRESS') || (liveState.filter === 'LATE' && P.health(r).worst !== 'ok') || (liveState.filter === 'SOS' && P.S.incidents.some(i => i.routeId === r.id && i.status === 'OPEN')));
    const f = liveState.focus && P.route(liveState.focus);
    return {
      active: 'live', title: 'Ao vivo', crumb: P.API ? 'Rotas e paradas da Orbi API, atualizadas automaticamente' : 'Posição estimada da frota em tempo real',
      html: `<div class="live" id="liveWrap">
        <section class="live-map card pad0"><div class="map-host" id="lm"></div>
          <div class="live-top"><span class="live-dot"></span><b>${rs.filter(r => r.status === 'IN_PROGRESS').length} em rota</b><span class="muted small">${P.API ? `atualiza a cada ${CFG.pollSeconds || 10} s · posição = última parada registrada` : 'atualiza a cada segundo'}</span><button class="btn sm ghost" id="tv">${ic('expand', 'sm')} Tela cheia</button></div></section>
        <aside class="live-side">
          <div class="chips">${FL.map(x => `<button class="chip ${liveState.filter === x[0] ? 'on' : ''}" data-f="${x[0]}">${x[1]} <em>${x[2]}</em></button>`).join('')}</div>
          ${f ? focusCard(f) : ''}
          <div class="live-list">${list.length ? list.map(r => { const d = P.user(r.driverId); const inf = P.routeInfo(r); const pos = P.livePos(r); const n = P.nextStop(r); return `<button class="lrow ${liveState.focus === r.id ? 'on' : ''}" data-r="${r.id}"><span class="lr-color" style="background:${ROUTE_COLORS[P.S.routes.indexOf(r) % ROUTE_COLORS.length]}"></span>
            <span class="avatar sm">${d ? P.initials(d.name) : '?'}</span><span class="lr-main"><b>${esc(r.code)} · ${d ? esc(P.first(d.name)) : 'sem motorista'}</b><small>${pos ? `Próxima: ${esc(n ? P.stopAddr(n) : '—')} · ~${pos.eta} min` : esc(P.ROUTE_STATUS[r.status][0]) + ' · ' + esc(r.window)}</small>${progress(inf.pct)}</span><span class="lr-end">${healthDot(r)}${routeFlags(r)}</span></button>`; }).join('') : empty('radar', 'Nada neste filtro', 'Nenhuma rota corresponde ao filtro.')}</div>
        </aside></div>`,
      mount(root) {
        const mp = (P.API ? P.geoMap : liveMap)($('#lm', root), rs, { keep: liveState, focus: liveState.focus, onPick: id => { liveState.focus = id; render(); } });
        $$('[data-f]', root).forEach(b => b.onclick = () => { liveState.filter = b.dataset.f; render(); });
        $$('[data-r]', root).forEach(b => b.onclick = () => { liveState.focus = liveState.focus === b.dataset.r ? null : b.dataset.r; render(); if (liveState.focus) setTimeout(() => { const m = window.__lm; if (m) m.focus(liveState.focus); }, 0); });
        window.__lm = mp;
        const fx = $('#fx', root); if (fx) fx.onclick = () => { liveState.focus = null; render(); };
        bindFocus(root);
        $('#tv', root).onclick = () => { const w = $('#liveWrap', root); if (document.fullscreenElement) document.exitFullscreen(); else if (w.requestFullscreen) w.requestFullscreen().then(() => setTimeout(() => mp.redraw(), 200)); else P.toast('Seu navegador não suporta tela cheia', 'info'); };
        const t = setInterval(() => { $$('.lrow', root).forEach(b => { const r = P.route(b.dataset.r); const pos = r && P.livePos(r); const n = r && P.nextStop(r); const s = $('.lr-main small', b); if (pos && s) s.textContent = `Próxima: ${n ? P.stopAddr(n) : '—'} · ~${pos.eta} min`; }); }, 1000);
        return () => { mp.stop(); clearInterval(t); window.__lm = null; };
      }
    };
  };
  function focusCard(r) {
    const d = P.user(r.driverId); const inf = P.routeInfo(r); const pos = P.livePos(r); const h = P.health(r);
    return `<div class="focus card"><div class="row between"><b>${esc(r.code)} ${P.badge(P.ROUTE_STATUS, r.status)}</b><button class="icon-btn sm" id="fx" aria-label="Fechar">${ic('close', 'sm')}</button></div>
      <p class="muted small" style="margin:4px 0 10px">${d ? esc(d.name) + ' · ' + esc(d.vehicle) : 'Sem motorista'}${pos ? ` · em ${esc(P.streetAt(pos))}` : ''}</p>
      <div class="mini-stats"><span><b>${inf.handled}/${inf.total}</b>paradas</span><span><b>${pos ? '~' + pos.eta + ' min' : '—'}</b>próxima</span><span><b>${P.num(inf.km, 1)}</b>km</span></div>
      ${h.checks.filter(c => c[0] !== 'ok').map(c => `<div class="hc ${c[0]}">${ic('alert', 'xs')} ${esc(c[1])}</div>`).join('')}
      <div class="row wrap" style="margin-top:10px;gap:6px">${P.API ? '' : `<button class="btn sm ghost" data-fa="prio">${ic('zap', 'sm')} Prioridade</button><a class="btn sm ghost" href="#/messages/${r.id}">${ic('chat', 'sm')} Mensagem</a>`}<a class="btn sm primary" href="#/route/${r.id}">Abrir rota</a></div></div>`;
  }
  function bindFocus(root) { const b = $('[data-fa="prio"]', root); if (b) b.onclick = () => priorityModal(P.route(liveState.focus)); }

  // ==========================================================================
  // ROTAS
  // ==========================================================================
  const routesState = { q: '', st: 'ALL', sort: 'status', dir: 1, day: 'today' };
  const ORDER = ['IN_PROGRESS', 'DELAYED', 'CREATED', 'ASSIGNED', 'COMPLETED'];
  pages.routes = () => {
    const base = P.S.routes.filter(r => routesState.day === 'all' || P.isToday(r));
    const q = routesState.q.toLowerCase();
    let list = base.filter(r => routesState.st === 'ALL' || r.status === routesState.st || (routesState.st === 'ATTN' && P.health(r).worst !== 'ok'))
      .filter(r => { if (!q) return true; const d = P.user(r.driverId); return (r.code + ' ' + (d ? d.name : '') + ' ' + r.stops.map(s => P.stopAddr(s) + ' ' + s.recipient).join(' ')).toLowerCase().includes(q); });
    const key = { status: r => ORDER.indexOf(r.status) * 10 - P.prioRank(r), code: r => r.code, driver: r => (P.user(r.driverId) || {}).name || 'zz', window: r => r.window, progress: r => P.routeInfo(r).pct, km: r => P.routeInfo(r).km, cost: r => P.routeInfo(r).cost };
    list.sort((a, b) => { const x = key[routesState.sort](a), y = key[routesState.sort](b); return (x > y ? 1 : x < y ? -1 : 0) * routesState.dir; });
    const cnt = s => base.filter(r => s === 'ALL' || r.status === s || (s === 'ATTN' && P.health(r).worst !== 'ok')).length;
    const PER = 25; const nPages = Math.max(1, Math.ceil(list.length / PER)); const pg0 = Math.min(routesState.page || 0, nPages - 1);
    const th = (k, l) => `<th data-s="${k}" class="${routesState.sort === k ? 'sorted' : ''}">${l}${routesState.sort === k ? (routesState.dir > 0 ? ' ▲' : ' ▼') : ''}</th>`;
    return {
      active: 'routes', title: 'Rotas', crumb: `${P.plural(base.length, 'rota', 'rotas')} ${routesState.day === 'today' ? 'hoje' : 'no total'}`,
      actions: `<button class="btn ghost" id="csv">${ic('download', 'sm')} Exportar CSV</button><a class="btn primary" href="#/new">${ic('plus', 'sm')} Nova rota</a>`,
      html: `<div class="toolbar"><div class="input grow">${ic('search', 'sm')}<input id="q" value="${esc(routesState.q)}" placeholder="Buscar por código, motorista, endereço ou cliente"></div>
        <div class="seg"><button class="${routesState.day === 'today' ? 'on' : ''}" data-day="today">Hoje</button><button class="${routesState.day === 'all' ? 'on' : ''}" data-day="all">Todas</button></div></div>
        <div class="chips">${[['ALL', 'Todas'], ['ATTN', 'Precisam de atenção'], ['IN_PROGRESS', 'Em andamento'], ['DELAYED', 'Atrasadas'], ['ASSIGNED', 'A iniciar'], ['CREATED', 'Sem motorista'], ['COMPLETED', 'Concluídas']].map(x => `<button class="chip ${routesState.st === x[0] ? 'on' : ''}" data-st="${x[0]}">${x[1]} <em>${cnt(x[0])}</em></button>`).join('')}</div>
        <section class="card pad0">${list.length ? `<table class="tbl"><thead><tr><th></th>${th('code', 'Rota')}${th('status', 'Status')}${th('driver', 'Motorista')}${th('window', P.API ? 'Criação' : 'Janela')}${th('progress', 'Progresso')}${th('km', 'Distância')}${th('cost', 'Custo')}<th></th></tr></thead><tbody>
          ${list.slice(pg0 * PER, pg0 * PER + PER).map(r => { const inf = P.routeInfo(r); const d = P.user(r.driverId); return `<tr data-href="#/route/${r.id}" tabindex="0"><td data-l="">${healthDot(r)}</td><td data-l="Rota"><b>${esc(r.code)}</b></td><td data-l="Status"><span class="row wrap" style="gap:4px">${P.badge(P.ROUTE_STATUS, r.status)}${routeFlags(r)}</span></td>
            <td data-l="Motorista">${d ? `<span class="who"><span class="avatar xs">${P.initials(d.name)}</span>${esc(d.name)}</span>` : '<span class="muted">—</span>'}</td><td data-l="${P.API ? 'Criação' : 'Janela'}">${esc(r.window)}</td>
            <td data-l="Progresso"><span class="prog-cell">${progress(inf.pct, r.status === 'COMPLETED' ? 'green' : '')}<small>${inf.handled}/${inf.total}</small></span></td><td data-l="Distância">${P.num(inf.km, 1)} km</td><td data-l="Custo">${P.money(inf.cost)}</td><td class="chev">${ic('chev', 'sm')}</td></tr>`; }).join('')}</tbody></table>
          ${nPages > 1 ? `<div class="pager"><span>${pg0 * PER + 1}–${Math.min(list.length, pg0 * PER + PER)} de ${list.length}</span><span class="row"><button class="btn sm ghost" data-pg="-1" ${pg0 ? '' : 'disabled'}>Anterior</button><button class="btn sm ghost" data-pg="1" ${pg0 < nPages - 1 ? '' : 'disabled'}>Próxima</button></span></div>` : ''}`
        : empty('route', 'Nenhuma rota encontrada', 'Ajuste a busca ou os filtros.')}</section>`,
      mount(root) {
        const q = $('#q', root);
        $$('[data-pg]', root).forEach(b => b.onclick = () => { routesState.page = pg0 + Number(b.dataset.pg); render(); window.scrollTo(0, 0); });
        q.oninput = () => { routesState.q = q.value; routesState.page = 0; render(); const n = $('#q'); n.focus(); n.setSelectionRange(n.value.length, n.value.length); };
        $$('[data-st]', root).forEach(b => b.onclick = () => { routesState.st = b.dataset.st; routesState.page = 0; render(); });
        $$('[data-day]', root).forEach(b => b.onclick = () => { routesState.day = b.dataset.day; routesState.page = 0; render(); });
        $$('[data-s]', root).forEach(t => t.onclick = () => { if (routesState.sort === t.dataset.s) routesState.dir *= -1; else { routesState.sort = t.dataset.s; routesState.dir = 1; } render(); });
        $$('tr[data-href]', root).forEach(tr => { tr.onclick = () => go(tr.dataset.href); tr.onkeydown = e => { if (e.key === 'Enter') go(tr.dataset.href); }; });
        $('#csv', root).onclick = () => { P.downloadCSV(`orbi-rotas-${new Date().toISOString().slice(0, 10)}.csv`, [['Rota', 'Status', 'Motorista', 'Janela', 'Paradas', 'Tratadas', 'Entregues', 'Puladas', 'Canceladas', 'Km', 'Custo (R$)']].concat(list.map(r => { const i = P.routeInfo(r); return [r.code, P.ROUTE_STATUS[r.status][0], (P.user(r.driverId) || {}).name || '', r.window, i.total, i.handled, i.done, i.skipped, i.cancelled, P.num(i.km, 1), P.num(i.cost, 2)]; }))); P.toast('Planilha CSV gerada', 'download'); };
      }
    };
  };

  pages.route = (p) => {
    const r = P.route(p.id); if (!r) return notFound();
    const inf = P.routeInfo(r); const d = P.user(r.driverId); const h = P.health(r);
    const canDelete = ['CREATED', 'ASSIGNED', 'DELAYED'].concat(P.API ? ['CANCELLED'] : []).includes(r.status);
    const canCancel = P.API && !['COMPLETED', 'CANCELLED'].includes(r.status);
    const log = [];
    if (r.createdAt) log.push([r.createdAt, 'Rota criada', 'route']);
    if (r.startedAt) log.push([r.startedAt, 'Rota iniciada pelo motorista', 'truck']);
    r.stops.forEach((s, i) => { if (s.at) log.push([s.at, `Parada #${String(i + 1).padStart(2, '0')} · ${P.STOP_STATUS[s.status][0].toLowerCase()}${s.reason ? ' · ' + s.reason : ''}`, s.status === 'COMPLETED' ? 'check' : s.status === 'SKIPPED' ? 'skip' : 'xcircle']); });
    P.chatOf(r.id).filter(m => m.kind !== 'text').forEach(m => log.push([m.at, m.kind === 'priority' ? 'Alerta de prioridade enviado' : m.kind === 'ack' ? 'Motorista confirmou “Ciente”' : m.kind === 'sos' ? 'Alerta de segurança: ' + m.text : m.text, m.kind === 'sos' ? 'shield' : m.kind === 'priority' ? 'zap' : 'info']));
    if (r.finishedAt) log.push([r.finishedAt, 'Rota concluída', 'check']);
    log.sort((a, b) => Date.parse(b[0]) - Date.parse(a[0]));
    return {
      active: 'routes', title: `Rota ${r.code}`, crumb: `<a href="#/routes">Rotas</a> / ${esc(r.code)}`, noHead: true,
      html: `<div class="page-head"><a class="icon-btn" href="#/routes" aria-label="Voltar">${ic('back')}</a><div class="ph-main"><h2>${esc(r.code)}</h2><span class="row wrap" style="gap:6px">${P.badge(P.ROUTE_STATUS, r.status)}${routeFlags(r)}</span></div>
        <div class="row wrap ph-actions">${P.API ? '' : `<button class="btn ghost" id="prio" ${!d || r.status === 'COMPLETED' ? 'disabled' : ''}>${ic('zap', 'sm')} Prioridade</button><a class="btn ghost ${d ? '' : 'disabled'}" href="#/messages/${r.id}">${ic('chat', 'sm')} Mensagem${P.unreadChat(r.id, P.me()) ? ` <em class="pill-n">${P.unreadChat(r.id, P.me())}</em>` : ''}</a>`}
        <button class="btn ghost" id="asg" ${['COMPLETED', 'CANCELLED'].includes(r.status) ? 'disabled' : ''}>${ic('users', 'sm')} ${d ? 'Trocar motorista' : 'Atribuir'}</button><button class="btn ghost" id="csv">${ic('download', 'sm')}</button>${canCancel ? `<button class="btn ghost danger-t" id="cnl">${ic('xcircle', 'sm')} Cancelar rota</button>` : ''}${canDelete ? `<button class="btn ghost danger-t" id="del">${ic('trash', 'sm')}</button>` : ''}</div></div>
      ${r.priority && r.status !== 'COMPLETED' ? `<div class="callout ${r.priority.level === 'URGENT' ? 'red' : 'amber'}">${ic('zap', 'sm')}<span><b>${P.PRIORITY[r.priority.level][0]}</b> · “${esc(r.priority.message)}” · enviado ${P.rel(r.priority.at)} · ${r.priority.requireAck ? (r.priority.ackAt ? `motorista ciente às ${P.hhmm(r.priority.ackAt)}` : 'aguardando “Ciente”') : 'sem confirmação'}</span></div>` : ''}
      <div class="grid g-2-1">
        <section class="card pad0"><div class="map-host tall" id="rm"></div></section>
        <div class="stack">
          <section class="card health ${h.worst}"><div class="card-head"><h3><span class="hdot ${h.worst}"></span> Situação da rota</h3><span class="badge ${h.worst === 'ok' ? 'green' : h.worst === 'warn' ? 'amber' : 'red'}">${{ ok: 'Tudo certo', warn: 'Atenção', bad: 'Ação necessária' }[h.worst]}</span></div>
            ${h.checks.map(c => `<div class="hc-row ${c[0]}">${ic(c[0] === 'ok' ? 'check' : 'alert', 'sm')}<span><b>${esc(c[1])}</b><small>${c[2]}</small></span></div>`).join('')}
            ${h.inc ? `<button class="btn danger block sm" id="inc" style="margin-top:10px">${ic('shield', 'sm')} Ver alerta de segurança</button>` : r.status === 'DELAYED' && !r.priority ? `<button class="btn primary block sm" id="prio2" style="margin-top:10px">${ic('zap', 'sm')} Enviar alerta de prioridade</button>` : !d ? `<button class="btn primary block sm" id="asg2" style="margin-top:10px">Atribuir motorista</button>` : ''}</section>
          <section class="card"><div class="kv compact">
            <div><span>Motorista</span><b>${d ? `<a href="#/driver/${d.id}">${esc(d.name)}</a>` : '—'}</b></div>
            ${P.API ? `<div><span>Telefone</span><b>${d && d.phone ? esc(d.phone) : '—'}</b></div>` : `<div><span>Veículo</span><b>${d ? esc(d.vehicle) + ' · ' + P.OWNERSHIP[P.vehicleOf(d).ownership][0].toLowerCase() : '—'}</b></div>`}
            <div><span>${P.API ? 'Criação' : 'Janela'}</span><b>${esc(r.window)}</b></div>
            <div><span>Paradas</span><b>${inf.handled}/${inf.total} · ${inf.done} entregues</b></div>
            <div><span>Distância</span><b>${P.num(inf.km, 1)} km · ~${P.fmtMin(inf.minutes)}</b></div>
            <div><span>Custo estimado</span><b>${P.money(inf.cost)}</b></div>
            <div><span>Criada</span><b>${P.rel(r.createdAt)}${r.startedAt ? ' · iniciada às ' + P.hhmm(r.startedAt) : ''}</b></div></div></section>
        </div></div>
      <div class="grid g-2-1">
        <section class="card pad0"><div class="card-head pad"><h3>Paradas · ordem otimizada</h3><span class="muted small">Clique para ver o relato</span></div>
          <table class="tbl"><thead><tr><th>#</th><th>Endereço</th><th>Destinatário</th><th>Status</th><th>Registro</th><th></th></tr></thead><tbody>
          ${r.stops.map((s, i) => `<tr data-stop="${s.id}" tabindex="0"><td data-l="#"><b>${String(i + 1).padStart(2, '0')}</b></td><td data-l="Endereço">${esc(P.stopAddr(s))}<small class="block muted">${esc(s.district)}</small></td><td data-l="Destinatário">${esc(s.recipient)}</td>
            <td data-l="Status"><span class="row wrap" style="gap:4px">${P.badge(P.STOP_STATUS, s.status)}${s.review && s.review.status === 'REQUESTED' ? '<span class="badge violet">Revisão</span>' : ''}${(s.comments || []).length ? `<span class="badge neutral">${ic('chat', 'xs')} ${s.comments.length}</span>` : ''}</span></td>
            <td data-l="Registro">${s.at ? P.hhmm(s.at) : '—'}${s.reason ? `<small class="block txt-${P.STOP_STATUS[s.status][1]}">${esc(s.reason)}</small>` : s.proof ? `<small class="block muted">por ${esc(s.proof.receiver)}</small>` : ''}</td><td class="chev">${ic('chev', 'sm')}</td></tr>`).join('')}</tbody></table></section>
        <section class="card"><div class="card-head"><h3>Linha do tempo</h3></div><div class="timeline">${log.map(l => `<div class="tl"><span class="tl-ic">${ic(l[2], 'xs')}</span><span><b>${esc(l[1])}</b><small>${P.date(l[0])} às ${P.hhmm(l[0])} · ${P.rel(l[0])}</small></span></div>`).join('') || '<p class="muted small">Sem eventos.</p>'}</div></section>
      </div>`,
      mount(root) {
        const host = $('#rm', root);
        let gm = null;
        if (P.API) gm = P.geoMap(host, [r], {});
        else host.innerHTML = M.overviewSvg(r, { ratio: host.clientWidth / Math.max(1, host.clientHeight), highlightNext: r.status === 'IN_PROGRESS' }).replace(M.citySvg(), '<use href="#orbi-city"/>'); ensureCity();
        const on = (id, fn) => { const e = $(id, root); if (e) e.onclick = fn; };
        on('#prio', () => priorityModal(r)); on('#prio2', () => priorityModal(r)); on('#asg', () => assignModal(r)); on('#asg2', () => assignModal(r));
        on('#inc', () => incidentModal(h.inc));
        on('#cnl', () => P.confirm(`Cancelar ${r.code}?`, 'A rota e as entregas pendentes ficam canceladas. O motorista verá a mudança no app.', 'Cancelar rota', true, () => P.act.cancelRoute(r.id)));
        on('#del', () => P.confirm(`Excluir ${r.code}?`, 'A rota e as paradas serão removidas. O motorista deixa de ver esta rota.', 'Excluir', true, () => { if (P.API) { P.act.deleteRoute(r.id).then(ok => { if (ok) go('#/routes'); }); return; } P.act.deleteRoute(r.id); P.toast('Rota excluída', 'trash'); go('#/routes'); }));
        on('#csv', () => { P.downloadCSV(`orbi-${r.code}.csv`, [['#', 'Endereço', 'Bairro', 'Destinatário', 'Telefone', 'Status', 'Horário', 'Motivo', 'Recebido por']].concat(r.stops.map((s, i) => [i + 1, P.stopAddr(s), s.district, s.recipient, s.phone, P.STOP_STATUS[s.status][0], s.at ? P.hhmm(s.at) : '', s.reason || '', s.proof ? s.proof.receiver : '']))); P.toast('Planilha CSV gerada', 'download'); });
        $$('tr[data-stop]', root).forEach(tr => { const open = () => stopModal(r, r.stops.find(s => s.id === tr.dataset.stop)); tr.onclick = open; tr.onkeydown = e => { if (e.key === 'Enter') open(); }; });
        return () => { if (gm) gm.stop(); };
      }
    };
  };

  // ==========================================================================
  // NOVA ROTA
  // ==========================================================================
  // Nova rota no modo API: endereços reais (busca ou clique no mapa) -> POST /api/route/optimize
  let draftApi = null;
  function newRouteApi() {
    if (!draftApi) draftApi = { stops: [], driverId: null, chosen: null, label: '' };
    const list = draftApi.stops;
    const n = list.length;
    const est = n ? 1 + n * 3 : 0;
    const pseudo = { id: 'novo', code: 'Nova rota', status: 'CREATED', stops: list.map((s, i) => ({ id: 'n' + i, order: i + 1, lat: s.lat, lng: s.lng, street: s.address, status: 'PENDING' })) };
    return {
      active: 'routes', title: 'Nova rota', crumb: '<a href="#/routes">Rotas</a> / Nova',
      html: `<div class="grid g-1-1 new-route">
        <section class="card"><div class="card-head"><h3>1. Paradas</h3><span class="muted small">${P.plural(n, 'parada', 'paradas')}</span></div>
          <p class="muted small" style="margin-top:0">Busque o endereço <b>ou clique no mapa</b> para marcar o ponto. A primeira parada é o ponto de saída.</p>
          <label class="lbl" for="addr">Endereço</label>
          <div class="row" style="gap:8px"><div class="input grow">${ic('search', 'sm')}<input id="addr" value="${esc(draftApi.label)}" placeholder="Ex.: Av. Francisco Glicério, 900, Campinas"></div><button class="btn ghost" id="find" type="button">Buscar</button></div>
          <div id="sug" class="sug"></div>
          <div class="form-2"><div><label class="lbl" for="em">E-mail do destinatário (opcional)</label><div class="input"><input id="em" type="email" placeholder="cliente@exemplo.com"></div></div>
            <div><label class="lbl">Ponto no mapa</label><div class="input"><input id="pt" readonly value="${draftApi.chosen ? draftApi.chosen.lat.toFixed(5) + ', ' + draftApi.chosen.lng.toFixed(5) : ''}" placeholder="Busque ou clique no mapa"></div></div></div>
          <div class="form-err" id="err"></div>
          <button class="btn ghost" id="add" type="button">${ic('plus', 'sm')} Adicionar parada</button>
          <div class="stop-list">${n ? list.map((s, i) => `<div class="sl"><span class="sl-n">${i + 1}</span><span class="sl-main"><b>${esc(s.address)}</b><small>${s.lat.toFixed(5)}, ${s.lng.toFixed(5)}${s.email ? ' · ' + esc(s.email) : ''}</small></span><button class="icon-btn sm" data-up="${i}" aria-label="Subir" ${i === 0 ? 'disabled' : ''}>▲</button><button class="icon-btn sm" data-rm="${i}" aria-label="Remover">${ic('trash', 'sm')}</button></div>`).join('') : '<p class="muted small">Adicione pelo menos 2 paradas.</p>'}</div></section>
        <div class="stack">
          <section class="card pad0"><div class="card-head pad"><h3>Mapa</h3><span class="muted small">${n > 1 ? '≈ ' + P.num(P.geoKm(pseudo.stops), 1) + ' km' : 'clique para marcar um ponto'}</span></div><div class="map-host" id="nm"></div></section>
          <section class="card"><div class="card-head"><h3>2. Motorista</h3></div>
            <div class="pick-list">${P.drivers().map(d => { const st = P.driverState(d); return `<button class="pick ${draftApi.driverId === d.id ? 'on' : ''}" data-d="${d.id}"><span class="avatar">${P.initials(d.name)}</span><span><b>${esc(d.name)}</b><small>${esc(d.phone || d.email || '')}</small></span><span class="badge ${st[1]}">${st[0]}</span></button>`; }).join('')}
              <button class="pick ${!draftApi.driverId ? 'on' : ''}" data-d=""><span class="avatar">?</span><span><b>Atribuir depois</b><small>A rota fica “Sem motorista”</small></span></button></div>
            <div class="callout amber">${ic('info', 'sm')}<span>Ao criar, a Orbi API <b>otimiza a ordem</b> das paradas usando o <b>OpenRouteService</b>, que tem cota gratuita${n ? ` (estimativa: até ~${est} chamadas para ${n} paradas)` : ''}.</span></div>
            <button class="btn success block lg" id="create" ${n < 2 ? 'disabled' : ''}>${ic('check', 'sm')} Criar rota na Orbi API</button></section></div></div>`,
      mount(root) {
        const err = $('#err', root), addr = $('#addr', root), pt = $('#pt', root), sug = $('#sug', root);
        const gm = P.geoMap($('#nm', root), [pseudo], { scrollZoom: true });
        let temp = null;
        const mark = (lat, lng) => {
          draftApi.chosen = { lat, lng }; pt.value = lat.toFixed(5) + ', ' + lng.toFixed(5);
          if (gm.map && window.L) { if (temp) temp.remove(); temp = L.circleMarker([lat, lng], { radius: 9, color: '#2557D6', weight: 3, fillColor: '#fff', fillOpacity: 1 }).addTo(gm.map); }
        };
        if (draftApi.chosen) mark(draftApi.chosen.lat, draftApi.chosen.lng);
        if (gm.map) gm.map.on('click', e => { mark(e.latlng.lat, e.latlng.lng); if (!addr.value.trim()) addr.value = `Ponto ${pt.value}`; err.textContent = ''; });
        addr.oninput = () => { draftApi.label = addr.value; };
        // busca de endereço no OpenStreetMap (Nominatim): gratuita, uma consulta por clique
        $('#find', root).onclick = async () => {
          const q = addr.value.trim(); if (q.length < 4) { err.textContent = 'Digite um endereço mais completo.'; return; }
          sug.innerHTML = `<p class="muted small" style="padding:10px">${ic('refresh', 'xs spin')} Buscando…</p>`;
          try {
            const res = await fetch('https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&countrycodes=br&accept-language=pt-BR&q=' + encodeURIComponent(q));
            const data = await res.json();
            sug.innerHTML = data.length ? data.map((x, i) => `<button data-g="${i}">${ic('pin', 'sm')}<span>${esc(x.display_name.split(',').slice(0, 3).join(','))}<small>${esc(x.display_name.split(',').slice(3, 6).join(','))}</small></span></button>`).join('') + '<p class="muted small" style="padding:6px 12px;margin:0">Busca: © OpenStreetMap / Nominatim</p>' : '<p class="muted small" style="padding:10px">Nada encontrado. Tente outro texto ou clique no mapa.</p>';
            $$('[data-g]', sug).forEach(b => b.onclick = () => {
              const x = data[+b.dataset.g]; addr.value = x.display_name.split(',').slice(0, 4).join(','); draftApi.label = addr.value;
              mark(+x.lat, +x.lon); if (gm.map) gm.map.setView([+x.lat, +x.lon], 16); sug.innerHTML = '';
            });
          } catch (e) { sug.innerHTML = '<p class="muted small" style="padding:10px">Busca indisponível agora. Clique no mapa para marcar o ponto.</p>'; }
        };
        addr.onkeydown = e => { if (e.key === 'Enter') { e.preventDefault(); $('#find', root).click(); } };
        $('#add', root).onclick = () => {
          if (!draftApi.chosen) { err.textContent = 'Marque o ponto: busque o endereço ou clique no mapa.'; return; }
          if (!addr.value.trim()) { err.textContent = 'Informe o endereço.'; return; }
          const email = $('#em', root).value.trim();
          if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) { err.textContent = 'E-mail inválido.'; return; }
          draftApi.stops.push({ address: addr.value.trim(), lat: draftApi.chosen.lat, lng: draftApi.chosen.lng, email });
          draftApi.chosen = null; draftApi.label = ''; render();
        };
        $$('[data-rm]', root).forEach(b => b.onclick = () => { draftApi.stops.splice(+b.dataset.rm, 1); render(); });
        $$('[data-up]', root).forEach(b => b.onclick = () => { const i = +b.dataset.up; const t = draftApi.stops[i]; draftApi.stops[i] = draftApi.stops[i - 1]; draftApi.stops[i - 1] = t; render(); });
        $$('[data-d]', root).forEach(b => b.onclick = () => { draftApi.driverId = b.dataset.d || null; render(); });
        $('#create', root).onclick = () => P.confirm('Criar rota na Orbi API?', `A API vai otimizar ${n} paradas usando o OpenRouteService (consome a cota gratuita, estimativa de até ~${est} chamadas). A rota aparece no app do motorista na próxima atualização.`, 'Criar rota', false, async () => {
          const btn = $('#create'); if (btn) { btn.disabled = true; btn.innerHTML = `${ic('refresh', 'sm spin')} Criando e otimizando…`; }
          const before = new Set(P.S.routes.map(r => r.id));
          try {
            await P.act.createRouteApi(draftApi.stops, draftApi.driverId);
            const created = P.S.routes.find(r => !before.has(r.id));
            draftApi = null; P.toast(`Rota ${created ? created.code : ''} criada na Orbi API`, 'check');
            go(created ? '#/route/' + created.id : '#/routes');
          } catch (e) { P.toast(e.message || 'Não foi possível criar a rota', 'alert'); render(); }
        });
        return () => gm.stop();
      }
    };
  }

  let draft = null;
  pages.new = () => {
    if (P.API) return newRouteApi();
    if (!draft) draft = { stops: [], optimized: null, before: 0, after: 0, driverId: null, window: '08:00 – 12:00' };
    const list = draft.optimized || draft.stops;
    const preview = { stops: list };
    return {
      active: 'routes', title: 'Nova rota', crumb: '<a href="#/routes">Rotas</a> / Nova',
      html: `<div class="grid g-1-1 new-route">
        <section class="card"><div class="card-head"><h3>1. Paradas</h3><button class="link" id="ex">${ic('sparkle', 'xs')} Usar exemplos</button></div>
          <label class="lbl">Endereço</label><div class="input">${ic('search', 'sm')}<input id="q" placeholder="Buscar rua ou bairro do catálogo" autocomplete="off"></div><div id="sug" class="sug"></div>
          <div class="form-2"><div><label class="lbl">Destinatário</label><div class="input"><input id="rn" placeholder="Nome ou empresa"></div></div><div><label class="lbl">Telefone</label><div class="input"><input id="rp" placeholder="(11) 9...." inputmode="tel"></div></div></div>
          <div class="form-2"><div><label class="lbl">Pacote</label><div class="input"><input id="pk" placeholder="Ex.: caixa média"></div></div><div><label class="lbl">Peso (kg)</label><div class="input"><input id="pw" placeholder="1,5" inputmode="decimal"></div></div></div>
          <div class="form-err" id="err"></div><button class="btn ghost" id="add">${ic('plus', 'sm')} Adicionar parada</button>
          <div class="stop-list">${list.length ? list.map((s, i) => `<div class="sl"><span class="sl-n">${i + 1}</span><span class="sl-main"><b>${esc(P.stopAddr(s))}</b><small>${esc(s.district)} · ${esc(s.recipient)} · ${esc(s.packages[0].desc)}</small></span>${draft.optimized ? '' : `<button class="icon-btn sm" data-up="${i}" aria-label="Subir" ${i === 0 ? 'disabled' : ''}>▲</button>`}<button class="icon-btn sm" data-rm="${i}" aria-label="Remover">${ic('trash', 'sm')}</button></div>`).join('') : '<p class="muted small">Adicione pelo menos 2 paradas.</p>'}</div></section>
        <div class="stack">
          <section class="card pad0"><div class="card-head pad"><h3>2. Otimização</h3>${list.length ? `<span class="muted small">${list.length} paradas · ${P.km(M.totalDistance(D.DEPOT, list))}</span>` : ''}</div>
            <div class="map-host" id="nm">${list.length ? '' : empty('map', 'Mapa da rota', 'As paradas aparecem aqui.')}</div>
            <div class="pad">${draft.optimized ? `<div class="compare"><div><span>Ordem original</span><b>${P.km(draft.before)}</b></div><div class="better"><span>Otimizada</span><b>${P.km(draft.after)}</b><small>−${Math.max(0, Math.round((1 - draft.after / draft.before) * 100))}% · ~${P.fmtMin(M.unitsToMin(draft.after))}</small></div></div>` : ''}
              <button class="btn primary block" id="opt" ${draft.stops.length < 2 ? 'disabled' : ''}>${ic('sparkle', 'sm')} ${draft.optimized ? 'Otimizar de novo' : 'Otimizar ordem das paradas'}</button>
              <p class="muted small" style="margin:8px 0 0">Simulação local. No sistema real: <code>POST /api/route/optimize</code> (OpenRouteService). Nenhuma requisição é feita aqui.</p></div></section>
          <section class="card"><div class="card-head"><h3>3. Motorista e janela</h3></div>
            <div class="chips wrap">${['08:00 – 12:00', '13:00 – 17:00', '14:00 – 18:00', '18:00 – 21:00'].map(w => `<button class="chip ${draft.window === w ? 'on' : ''}" data-w="${w}">${w}</button>`).join('')}</div>
            <div class="pick-list">${P.drivers().map(d => { const st = P.driverState(d); return `<button class="pick ${draft.driverId === d.id ? 'on' : ''}" data-d="${d.id}"><span class="avatar">${P.initials(d.name)}</span><span><b>${esc(d.name)}</b><small>${esc(d.vehicle)}</small></span><span class="badge ${st[1]}">${st[0]}</span></button>`; }).join('')}
              <button class="pick ${!draft.driverId ? 'on' : ''}" data-d=""><span class="avatar">?</span><span><b>Atribuir depois</b><small>A rota fica “Sem motorista”</small></span></button></div>
            <button class="btn success block lg" id="create" ${draft.stops.length < 2 ? 'disabled' : ''} style="margin-top:14px">${ic('check', 'sm')} Criar rota</button></section></div></div>`,
      mount(root) {
        if (list.length) { const nm = $('#nm', root); nm.innerHTML = M.overviewSvg(preview, { ratio: nm.clientWidth / Math.max(1, nm.clientHeight) }).replace(M.citySvg(), '<use href="#orbi-city"/>'); ensureCity(); }
        let chosen = null; const q = $('#q', root), sug = $('#sug', root), err = $('#err', root);
        const used = () => new Set(draft.stops.map(s => s.x + ',' + s.y));
        q.oninput = () => {
          chosen = null; const t = q.value.trim().toLowerCase(); if (!t) { sug.innerHTML = ''; return; }
          const u = used(); const res = D.ADDRESS_BOOK.filter(a => !u.has(a.x + ',' + a.y) && (a.street + ' ' + a.number + ' ' + a.district).toLowerCase().includes(t)).slice(0, 6);
          sug.innerHTML = res.length ? res.map(a => `<button data-a="${a.id}">${ic('pin', 'sm')}<span>${esc(a.street)}, ${a.number}<small>${esc(a.district)}</small></span></button>`).join('') : '<p class="muted small" style="padding:10px">Nenhum endereço no catálogo.</p>';
          $$('[data-a]', sug).forEach(b => b.onclick = () => { chosen = D.ADDRESS_BOOK.find(a => a.id === b.dataset.a); q.value = `${chosen.street}, ${chosen.number} · ${chosen.district}`; sug.innerHTML = ''; $('#rn', root).focus(); });
        };
        const mk = (a, name, phone, pk, w) => ({ id: P.uid('s'), recipient: name, phone: phone || '(11) 90000-0000', street: a.street, number: a.number, district: a.district, city: a.city, x: a.x, y: a.y, status: 'PENDING', reason: null, at: null, proof: null, packages: [{ desc: pk || 'Pacote', weight: w || 1 }] });
        $('#add', root).onclick = () => {
          if (!chosen) { err.textContent = 'Escolha um endereço da lista de sugestões.'; return; }
          const name = $('#rn', root).value.trim(); if (!name) { err.textContent = 'Informe o destinatário.'; return; }
          draft.stops.push(mk(chosen, name, $('#rp', root).value.trim(), $('#pk', root).value.trim(), parseFloat(($('#pw', root).value || '1').replace(',', '.')) || 1)); draft.optimized = null; render();
        };
        $('#ex', root).onclick = () => { const u = used(); D.ADDRESS_BOOK.filter(a => !u.has(a.x + ',' + a.y)).sort(() => Math.random() - .5).slice(0, 5).forEach((a, i) => { const rc = D.RECIPIENTS[(i * 5 + draft.stops.length) % D.RECIPIENTS.length]; const pk = D.PACKAGES[(i + draft.stops.length) % D.PACKAGES.length]; draft.stops.push(mk(a, rc[0], rc[1], pk[0], pk[1])); }); draft.optimized = null; render(); };
        $$('[data-rm]', root).forEach(b => b.onclick = () => { const s = list[+b.dataset.rm]; draft.stops = draft.stops.filter(x => x !== s); draft.optimized = null; render(); });
        $$('[data-up]', root).forEach(b => b.onclick = () => { const i = +b.dataset.up; if (i > 0) { const t = draft.stops[i]; draft.stops[i] = draft.stops[i - 1]; draft.stops[i - 1] = t; render(); } });
        $('#opt', root).onclick = e => { const b = e.currentTarget; b.disabled = true; b.innerHTML = `${ic('refresh', 'sm spin')} Otimizando…`; setTimeout(() => { draft.before = M.totalDistance(D.DEPOT, draft.stops); draft.optimized = M.optimize(D.DEPOT, draft.stops); draft.after = M.totalDistance(D.DEPOT, draft.optimized); render(); P.toast('Ordem otimizada', 'sparkle'); }, 800); };
        $$('[data-w]', root).forEach(b => b.onclick = () => { draft.window = b.dataset.w; render(); });
        $$('[data-d]', root).forEach(b => b.onclick = () => { draft.driverId = b.dataset.d || null; render(); });
        $('#create', root).onclick = () => { const r = P.act.createRoute(draft.optimized || draft.stops, draft.driverId, draft.window); draft = null; P.toast(`Rota ${r.code} criada${r.driverId ? ' · já aparece no app do motorista' : ''}`, 'check'); go('#/route/' + r.id); };
      }
    };
  };

  // ==========================================================================
  // MOTORISTAS
  // ==========================================================================
  function newDriverModal() {
    let own = 'COMPANY';
    const fi = P.S.company.fleetInsurance || {};
    P.modal('Novo motorista', `<div class="form-2">
      ${[['name', 'Nome completo', 'text'], ['email', 'E-mail de acesso ao app', 'email'], ['phone', 'Telefone', 'tel'], ['cnh', 'CNH', 'text'], ['model', 'Modelo do veículo', 'text'], ['plate', 'Placa', 'text']].map(f => `<div><label class="lbl" for="${f[0]}">${f[1]}</label><div class="input" id="${f[0]}w"><input id="${f[0]}" type="${f[2]}"></div><div class="ferr" id="${f[0]}h"></div></div>`).join('')}</div>
      <label class="lbl">Propriedade do veículo</label><div class="seg2"><button class="opt on" data-o="COMPANY"><b>Da empresa</b><small>Usa o seguro da frota</small></button><button class="opt" data-o="PERSONAL"><b>Particular</b><small>O motorista cadastra o seguro no app</small></button></div>
      <p class="muted small" id="fnote">Seguro da frota: ${esc(fi.insurer || 'não configurado')} · ${esc(fi.phone || '')}. Altere em Configurações → Frota.</p>
      <div class="row end" style="margin-top:12px"><button class="btn primary" id="ok">Cadastrar motorista</button></div>`, (m, close) => {
      $$('[data-o]', m).forEach(b => b.onclick = () => { own = b.dataset.o; $$('[data-o]', m).forEach(x => x.classList.toggle('on', x === b)); $('#fnote', m).style.display = own === 'COMPANY' ? '' : 'none'; });
      $('#ok', m).onclick = () => {
        const v = k => $('#' + k, m).value.trim(); const e = {};
        if (v('name').length < 3) e.name = 'Informe o nome'; if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v('email'))) e.email = 'E-mail inválido'; else if (P.S.users.some(u => u.email.toLowerCase() === v('email').toLowerCase())) e.email = 'E-mail já cadastrado';
        if (v('phone').replace(/\D/g, '').length < 10) e.phone = 'Telefone inválido'; if (v('cnh').replace(/\D/g, '').length < 9) e.cnh = 'CNH inválida'; if (!v('model')) e.model = 'Informe o modelo'; if (!/^[A-Z]{3}-?\d[A-Z0-9]\d{2}$/i.test(v('plate'))) e.plate = 'Placa inválida';
        ['name', 'email', 'phone', 'cnh', 'model', 'plate'].forEach(k => { $('#' + k + 'w', m).classList.toggle('err', !!e[k]); $('#' + k + 'h', m).textContent = e[k] || ''; });
        if (Object.keys(e).length) return;
        const plate = v('plate').toUpperCase();
        const u = P.act.createDriver({ name: v('name'), email: v('email'), password: 'orbi123', phone: v('phone'), cnh: v('cnh'), vehicle: `${v('model')} · ${plate}`, vehicleInfo: { ownership: own, model: v('model'), plate, company: own === 'COMPANY' ? Object.assign({}, fi) : { insurer: '', policy: '', phone: '' }, personal: { insurer: '', policy: '', phone: '' } } });
        close(); P.toast(`${u.name} cadastrado · senha inicial orbi123`, 'check'); go('#/driver/' + u.id);
      };
    });
  }
  pages.drivers = () => {
    const hist = P.history(7);
    const ds = P.drivers();
    return {
      active: 'drivers', title: 'Motoristas', crumb: `${P.plural(ds.length, 'motorista cadastrado', 'motoristas cadastrados')}`,
      html: `<div class="toolbar"><div class="row wrap" style="gap:8px">${['Em rota', 'Escalado', 'Disponível', 'SOS'].map(s => { const n = ds.filter(d => P.driverState(d)[0] === s).length; return n ? `<span class="badge ${s === 'SOS' ? 'red' : s === 'Em rota' ? 'blue' : s === 'Disponível' ? 'green' : 'neutral'}">${s}: ${n}</span>` : ''; }).join('')}</div><span class="grow"></span>${P.API ? '' : `<button class="btn primary" id="nd">${ic('plus', 'sm')} Novo motorista</button>`}</div>
        <div class="dgrid">${ds.map(d => { const st = P.driverState(d); const s7 = driverStats(d.id, hist); const v = P.API ? null : P.vehicleOf(d); const ins = P.API ? {} : P.insuranceOf(d); return `<a class="dcard card" href="#/driver/${d.id}">
          <div class="row" style="gap:12px"><span class="avatar lg ${st[0] === 'SOS' ? 'sos' : ''}">${P.initials(d.name)}</span><span class="grow"><b>${esc(d.name)}</b><small class="block muted">${P.API ? esc(d.email || d.phone || 'Motorista') : `Nota ${P.num(d.rating || 0, 1)} · ${d.ratingsCount || 0} avaliações`}</small></span><span class="badge ${st[1]}">${st[0]}</span></div>
          <div class="dc-stats"><span><b>${s7.delivered}</b>entregas 7d</span><span><b>${s7.success}%</b>sucesso</span><span><b>${P.API ? s7.routes : s7.onTime + '%'}</b>${P.API ? 'rotas 7d' : 'pontual'}</span></div>
          <div class="dc-spark">${C.spark(s7.series, COLORS.blue)}</div>
          ${P.API ? `<div class="dc-veh">${ic('phone', 'sm')}<span>${esc(d.phone || 'Telefone não informado')}</span></div>` : `<div class="dc-veh">${ic('truck', 'sm')}<span>${esc(d.vehicle)}</span><span class="badge ${P.OWNERSHIP[v.ownership][1]}">${P.OWNERSHIP[v.ownership][0]}</span>${ins.phone ? '' : `<span class="badge amber" data-tip="Assistência 24h não cadastrada">${ic('alert', 'xs')}</span>`}</div>`}</a>`; }).join('')}</div>`,
      mount(root) { const nd = $('#nd', root); if (nd) nd.onclick = newDriverModal; }
    };
  };
  pages.driver = (p) => {
    const d = P.user(p.id); if (!d || d.role !== 'DRIVER') return notFound();
    const hist = P.history(30); const s30 = driverStats(d.id, hist); const st = P.driverState(d); const v = P.API ? { ownership: 'PERSONAL', model: '', plate: '' } : P.vehicleOf(d); const ins = P.API ? {} : P.insuranceOf(d);
    const rs = P.S.routes.filter(r => r.driverId === d.id).sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
    const reviews = P.S.reviews.filter(r => r.driverId === d.id);
    const lastChat = rs.find(r => r.status !== 'COMPLETED') || rs[0];
    return {
      active: 'drivers', title: d.name, crumb: `<a href="#/drivers">Motoristas</a> / ${esc(d.name)}`, noHead: true,
      html: `<div class="page-head"><a class="icon-btn" href="#/drivers" aria-label="Voltar">${ic('back')}</a><span class="avatar lg">${P.initials(d.name)}</span><div class="ph-main"><h2>${esc(d.name)}</h2><span class="row wrap" style="gap:6px"><span class="badge ${st[1]}">${st[0]}</span><span class="muted small">${esc(d.email)} · ${esc(d.phone)} · desde ${P.date(d.since || new Date())}</span></span></div>
        <div class="row wrap ph-actions"><button class="btn ghost" id="call">${ic('phone', 'sm')} Ligar</button>${lastChat && !P.API ? `<a class="btn ghost" href="#/messages/${lastChat.id}">${ic('chat', 'sm')} Mensagem</a>` : ''}<a class="btn primary" href="#/new">${ic('plus', 'sm')} Nova rota</a></div></div>
      <div class="kpi-grid k5">
        ${kpi({ label: 'Entregas · 30 dias', icon: 'check', color: COLORS.ok, value: s30.delivered, sub: `${s30.stops} paradas`, spark: s30.series.slice(-14), href: '#/reports' })}
        ${kpi({ label: 'Taxa de sucesso', icon: 'trend', color: COLORS.blue, value: s30.success + '%', sub: `${s30.skipped} puladas · ${s30.cancelled} canceladas`, href: '#/reports' })}
        ${kpi({ label: P.API ? 'Rotas · 30 dias' : 'Pontualidade', icon: 'clock', color: COLORS.violet, value: P.API ? s30.routes : s30.onTime + '%', sub: P.API ? 'criadas no período' : `${s30.routes} rotas`, href: '#/reports' })}
        ${kpi({ label: 'Distância', icon: 'route', color: COLORS.teal, value: P.num(s30.km, 0) + '<span class="of"> km</span>', sub: `${P.fmtMin(s30.minutes / Math.max(1, s30.days))} por dia`, href: '#/reports' })}
        ${P.API ? kpi({ label: 'Rotas ativas', icon: 'route', color: '#F59E0B', value: rs.filter(x => ['ASSIGNED', 'IN_PROGRESS'].includes(x.status)).length, sub: 'atribuídas ou em andamento', href: '#/routes' }) : kpi({ label: 'Avaliação', icon: 'star', color: '#F59E0B', value: P.num(d.rating || 0, 1) + '<span class="of"> / 5</span>', sub: `${d.ratingsCount || 0} avaliações`, href: '#/drivers' })}
      </div>
      <div class="grid g-2-1">
        <section class="card"><div class="card-head"><h3>Desempenho · 30 dias</h3></div>${C.line(hist.map(x => x.label), [{ name: 'Entregas', color: COLORS.blue, values: s30.series, area: true }], { height: 200, tipLabels: hist.map(x => `${x.wd} ${x.label}`) })}</section>
        ${P.API ? `<section class="card"><div class="card-head"><h3>Contato</h3></div><div class="kv compact"><div><span>Telefone</span><b>${esc(d.phone || '—')}</b></div><div><span>E-mail</span><b>${esc(d.email || '—')}</b></div><div><span>CNH</span><b>${d.cnh ? '•••••' + esc(String(d.cnh).slice(-4)) : '—'}</b></div><div><span>Cadastro</span><b>${d.since ? P.date(d.since) : '—'}</b></div></div><p class="muted small">Veículo e seguro ainda não existem na Orbi API.</p></section>` : `<section class="card"><div class="card-head"><h3>Veículo</h3><button class="link" id="veh">${ic('edit', 'xs')} Editar</button></div>
          <div class="veh"><span class="veh-ic">${ic('truck')}</span><span><b>${esc(v.model)}</b><small>${esc(v.plate)}</small></span><span class="badge ${P.OWNERSHIP[v.ownership][1]}">${P.OWNERSHIP[v.ownership][0]}</span></div>
          <div class="kv compact"><div><span>Seguradora</span><b>${esc(ins.insurer || '—')}</b></div><div><span>Apólice</span><b>${esc(ins.policy || '—')}</b></div><div><span>Assistência 24h</span><b>${ins.phone ? esc(ins.phone) : '<span class="txt-amber">Não cadastrada</span>'}</b></div><div><span>Quem cadastra</span><b>${v.ownership === 'COMPANY' ? 'A gestão (você)' : 'O motorista, pelo app'}</b></div></div></section>`}</div>
      <div class="grid g-2-1">
        <section class="card pad0"><div class="card-head pad"><h3>Rotas</h3></div>${rs.length ? `<table class="tbl"><thead><tr><th>Rota</th><th>Status</th><th>${P.API ? 'Criação' : 'Janela'}</th><th>Progresso</th><th></th></tr></thead><tbody>${rs.map(r => { const inf = P.routeInfo(r); return `<tr data-href="#/route/${r.id}"><td data-l="Rota"><b>${esc(r.code)}</b></td><td data-l="Status">${P.badge(P.ROUTE_STATUS, r.status)}</td><td data-l="Janela">${esc(r.window)}</td><td data-l="Progresso"><span class="prog-cell">${progress(inf.pct)}<small>${inf.handled}/${inf.total}</small></span></td><td class="chev">${ic('chev', 'sm')}</td></tr>`; }).join('')}</tbody></table>` : empty('route', 'Sem rotas', 'Nenhuma rota atribuída.')}</section>
        ${P.API ? '' : `<section class="card"><div class="card-head"><h3>Avaliações dos clientes</h3></div>${reviews.length ? reviews.map(x => `<div class="review"><span class="stars">${'★'.repeat(x.stars)}<i>${'★'.repeat(5 - x.stars)}</i></span><p>“${esc(x.text)}”</p><small>${esc(x.author)}</small></div>`).join('') : '<p class="muted small">Sem avaliações ainda.</p>'}</section>`}</div>`,
      mount(root) {
        $('#call', root).onclick = () => P.toast(`Ligando para ${d.name} (${d.phone})… (simulado)`, 'phone');
        $$('tr[data-href]', root).forEach(tr => tr.onclick = () => go(tr.dataset.href));
        const vb = $('#veh', root); if (vb) vb.onclick = () => vehicleModal(d);
      }
    };
  };
  function vehicleModal(d) {
    const vi = JSON.parse(JSON.stringify(P.vehicleOf(d)));
    const draw = (m) => {
      const ins = vi[vi.ownership === 'COMPANY' ? 'company' : 'personal'];
      $('.modal-body', m).innerHTML = `<div class="seg2">${Object.keys(P.OWNERSHIP).map(k => `<button class="opt ${vi.ownership === k ? 'on' : ''}" data-o="${k}"><b>${P.OWNERSHIP[k][0]}</b><small>${k === 'COMPANY' ? 'A gestão cadastra o seguro' : 'O motorista cadastra no app'}</small></button>`).join('')}</div>
        <div class="form-2"><div><label class="lbl">Modelo</label><div class="input"><input id="model" value="${esc(vi.model)}"></div></div><div><label class="lbl">Placa</label><div class="input" id="platew"><input id="plate" value="${esc(vi.plate)}"></div><div class="ferr" id="plateh"></div></div></div>
        ${vi.ownership === 'COMPANY' ? `<div class="form-2"><div><label class="lbl">Seguradora</label><div class="input"><input id="ins" value="${esc(ins.insurer)}"></div></div><div><label class="lbl">Apólice</label><div class="input"><input id="pol" value="${esc(ins.policy)}"></div></div></div>
          <label class="lbl">Telefone da assistência 24h</label><div class="input"><input id="aph" value="${esc(ins.phone)}" inputmode="tel"></div>
          <button class="link" id="fleet" style="margin-top:8px">Usar o seguro padrão da frota</button>`
          : `<div class="callout neutral">${ic('info', 'sm')}<span>Veículo particular: o seguro é informado pelo motorista no app (Perfil → Meu veículo).<br>Atual: <b>${esc(ins.insurer || 'não informado')}</b>${ins.phone ? ' · ' + esc(ins.phone) : ''}</span></div>`}
        <div class="row end" style="margin-top:14px"><button class="btn primary" id="ok">Salvar veículo</button></div>`;
      const collect = () => { vi.model = $('#model', m).value.trim(); vi.plate = $('#plate', m).value.trim().toUpperCase(); if (vi.ownership === 'COMPANY') vi.company = { insurer: $('#ins', m).value.trim(), policy: $('#pol', m).value.trim(), phone: $('#aph', m).value.trim() }; };
      $$('[data-o]', m).forEach(b => b.onclick = () => { collect(); vi.ownership = b.dataset.o; draw(m); });
      const fl = $('#fleet', m); if (fl) fl.onclick = () => { const fi = P.S.company.fleetInsurance || {}; $('#ins', m).value = fi.insurer || ''; $('#pol', m).value = fi.policy || ''; $('#aph', m).value = fi.phone || ''; };
      $('#ok', m).onclick = () => { collect(); if (!/^[A-Z]{3}-?\d[A-Z0-9]\d{2}$/.test(vi.plate)) { $('#platew', m).classList.add('err'); $('#plateh', m).textContent = 'Placa inválida'; return; } P.act.updateVehicle(d.id, vi); P.closeLayer(); P.toast('Veículo atualizado · motorista notificado', 'check'); render(); };
    };
    P.modal(`Veículo · ${d.name}`, '', (m) => draw(m), { noFocus: true });
  }

  // ==========================================================================
  // OCORRÊNCIAS
  // ==========================================================================
  const occState = { f: 'OPEN', q: '' };
  pages.occurrences = () => {
    const all = occurrences();
    const F = [['OPEN', 'Abertas', o => o.open]].concat(P.API ? [] : [['SOS', 'SOS', o => o.kind === 'SOS' && o.open], ['REVIEW', 'Revisões', o => o.kind === 'REVIEW']]).concat([ ['DELAY', 'Atrasos', o => o.kind === 'DELAY' || o.kind === 'NODRIVER' || o.kind === 'ACK'], ['SKIPPED', 'Puladas', o => o.kind === 'SKIPPED'], ['CANCELLED', 'Canceladas', o => o.kind === 'CANCELLED'], ['DONE', P.API ? 'Anteriores' : 'Resolvidas', o => !o.open]]);
    const fn = F.find(x => x[0] === occState.f)[2];
    const q = occState.q.toLowerCase();
    const list = all.filter(fn).filter(o => !q || (o.title + ' ' + o.sub).toLowerCase().includes(q));
    const reasons = {}; P.S.routes.flatMap(r => r.stops).filter(s => s.reason).forEach(s => { reasons[s.reason] = (reasons[s.reason] || 0) + 1; });
    const summary = [['SOS ativos', all.filter(o => o.kind === 'SOS' && o.open).length, 'red', 'shield'], ['Revisões pedidas', all.filter(o => o.kind === 'REVIEW').length, 'violet', 'chat'], ['Atrasos e pendências', all.filter(o => ['DELAY', 'NODRIVER', 'ACK'].includes(o.kind)).length, 'amber', 'clock'], ['Paradas sem resposta', all.filter(o => ['SKIPPED', 'CANCELLED'].includes(o.kind) && o.open).length, 'neutral', 'skip']];
    return {
      active: 'occurrences', title: 'Ocorrências', crumb: 'Atrasos, paradas com problema e rotas sem motorista',
      html: `<div class="sum-grid">${summary.map(s => `<div class="sum"><b>${s[1]}</b><span>${s[0]}</span></div>`).join('')}</div>
      <div class="grid g-2-1">
        <section><div class="toolbar"><div class="input grow">${ic('search', 'sm')}<input id="q" value="${esc(occState.q)}" placeholder="Buscar ocorrência"></div></div>
          <div class="chips">${F.map(x => `<button class="chip ${occState.f === x[0] ? 'on' : ''}" data-f="${x[0]}">${x[1]} <em>${all.filter(x[2]).length}</em></button>`).join('')}</div>
          <div class="card pad0 occ-list">${list.length ? list.map(o => `<button class="occ" data-o="${o.id}"><span class="occ-ic ${o.tone}">${ic(o.icon, 'sm')}</span><span class="occ-main"><b>${esc(o.title)}</b><small>${esc(o.sub)}</small></span><span class="occ-end"><span class="badge ${o.open ? (o.tone === 'red' ? 'red' : 'amber') : 'green'}">${esc(o.status)}</span><small>${P.rel(o.at)}</small></span></button>`).join('') : empty('check', 'Nada por aqui', 'Nenhuma ocorrência neste filtro.')}</div></section>
        <div class="stack"><section class="card"><div class="card-head"><h3>Motivos informados</h3><span class="muted small">dados do app</span></div>${C.hbars(Object.entries(reasons).sort((a, b) => b[1] - a[1]).map(([k, v], i) => ({ label: k, value: v, color: i === 0 ? COLORS.cancel : COLORS.skip })), { empty: 'Nenhum motivo registrado.' })}</section></div></div>`,
      mount(root) {
        $$('[data-f]', root).forEach(b => b.onclick = () => { occState.f = b.dataset.f; render(); });
        const q = $('#q', root); q.oninput = () => { occState.q = q.value; render(); const n = $('#q'); n.focus(); n.setSelectionRange(n.value.length, n.value.length); };
        $$('[data-o]', root).forEach(b => b.onclick = () => { const o = all.find(x => x.id === b.dataset.o); if (o) openOccurrence(o); });
      }
    };
  };

  // ==========================================================================
  // RELATÓRIOS
  // ==========================================================================
  const repState = { days: 14 };
  pages.reports = () => {
    const hist = P.history(repState.days - 1); const t = P.todayStats();
    const today = { label: 'Hoje', wd: 'hoje', routes: t.routes, stops: t.stops, delivered: t.delivered, skipped: t.skipped, cancelled: t.cancelled, km: t.km, minutes: 0, onTime: 0, reasons: t.reasons, hours: t.hours, drivers: {}, dow: new Date().getDay() };
    const days = hist.concat([today]);
    const sum = k => days.reduce((a, d) => a + d[k], 0);
    const stops = sum('stops'), del = sum('delivered'), kmv = sum('km');
    const goals = P.S.company.goals || { successRate: 92, onTime: 90 };
    const success = successSeries(days);
    const routes = sum('routes'); const onTime = P.pct(hist.reduce((a, d) => a + d.onTime, 0), hist.reduce((a, d) => a + d.routes, 0) || 1);
    const cost = kmv * 1.85 + stops * 2.5;
    const reasons = {}; days.forEach(d => Object.entries(d.reasons).forEach(([k, v]) => { reasons[k] = (reasons[k] || 0) + v; }));
    const wd = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb']; const hrs = []; for (let h = 7; h <= 19; h++) hrs.push(h + 'h');
    const heat = wd.map((_, w) => hrs.map((_, j) => days.filter(d => d.dow === w).reduce((a, d) => a + d.hours[7 + j], 0)));
    const drows = P.drivers().map(d => Object.assign({ d }, driverStats(d.id, hist))).sort((a, b) => b.delivered - a.delivered);
    const best = drows.slice().sort((a, b) => b.success - a.success)[0];
    const worstDay = workDays(hist).slice().sort((a, b) => P.pct(a.delivered, a.stops) - P.pct(b.delivered, b.stops))[0];
    const topReason = Object.entries(reasons).sort((a, b) => b[1] - a[1])[0];
    const avgSucc = Math.round(avg(workDays(days).map(d => P.pct(d.delivered, d.delivered + d.skipped + d.cancelled || 1))));
    const insights = [
      best && `<b>${esc(best.d.name)}</b> teve a melhor taxa de sucesso do período (${best.success}%).`,
      topReason && `O motivo de ocorrência mais comum foi <b>“${esc(topReason[0])}”</b> (${topReason[1]}×). ${/ausente|fechado/i.test(topReason[0]) ? 'Avisar o cliente antes da entrega pode reduzir esse número.' : /bloqueada|obras/i.test(topReason[0]) ? 'Vale revisar os trajetos dessas regiões.' : 'Vale investigar com a equipe.'}`,
      worstDay && `O pior dia foi <b>${worstDay.wd} ${worstDay.label}</b>, com ${P.pct(worstDay.delivered, worstDay.stops)}% de sucesso.`,
      `A taxa média de sucesso ficou em <b>${avgSucc}%</b>, ${avgSucc >= goals.successRate ? 'acima' : 'abaixo'} da meta de ${goals.successRate}%.`,
      `Custo estimado de <b>${P.money(cost / Math.max(1, del))}</b> por entrega concluída.`
    ].filter(Boolean);
    return {
      active: 'reports', title: 'Relatórios', crumb: `Últimos ${repState.days} dias · ${days[0].label} a hoje`,
      actions: `<div class="seg">${[7, 14, 30].map(n => `<button class="${repState.days === n ? 'on' : ''}" data-d="${n}">${n} dias</button>`).join('')}</div><button class="btn ghost" id="csv">${ic('download', 'sm')} Exportar CSV</button><button class="btn ghost" id="print">${ic('printer', 'sm')} Imprimir</button>`,
      html: `
      <div class="print-head"><span class="brand-logo">${P.LOGO}</span><div><b>${esc(P.S.company.name)}</b><small>Relatório operacional · ${days[0].label} a ${P.date(Date.now())} · gerado por ${esc(P.me().name)}</small></div></div>
      <div class="kpi-grid k6">
        ${kpi({ label: 'Entregas', icon: 'check', color: COLORS.ok, value: P.num(del), sub: `${P.num(stops)} paradas`, spark: days.map(d => d.delivered) })}
        ${kpi({ label: 'Taxa de sucesso', icon: 'trend', color: COLORS.blue, value: avgSucc + '%', sub: `meta ${goals.successRate}%`, spark: success })}
        ${P.API ? kpi({ label: 'Ocorrências', icon: 'alert', color: COLORS.violet, value: P.num(sum('skipped') + sum('cancelled')), sub: `${sum('skipped')} puladas · ${sum('cancelled')} canceladas` }) : kpi({ label: 'Pontualidade', icon: 'clock', color: COLORS.violet, value: onTime + '%', sub: `meta ${goals.onTime}%` })}
        ${kpi({ label: 'Rotas', icon: 'route', color: COLORS.sky, value: P.num(routes), sub: `${P.num(routes / days.length, 1)} por dia` })}
        ${kpi({ label: 'Distância', icon: 'truck', color: COLORS.teal, value: P.num(kmv, 0) + '<span class="of"> km</span>', sub: `${P.num(kmv / Math.max(1, del), 1)} km por entrega` })}
        ${kpi({ label: 'Custo estimado', icon: 'wallet', color: COLORS.skip, value: P.money(cost), sub: `${P.money(cost / Math.max(1, del))} por entrega` })}
      </div>
      <section class="card insights"><div class="card-head"><h3>Resumo do período</h3></div><ul>${insights.map(i => `<li>${i}</li>`).join('')}</ul></section>
      <div class="grid g-2-1">
        <section class="card"><div class="card-head"><h3>Entregas por dia</h3></div>${C.stacked(days.map(d => d.label), [{ name: 'Entregues', color: COLORS.ok, values: days.map(d => d.delivered) }, { name: 'Puladas', color: COLORS.skip, values: days.map(d => d.skipped) }, { name: 'Canceladas', color: COLORS.cancel, values: days.map(d => d.cancelled) }], { highlightLast: true, tipLabels: days.map(d => `${d.wd} ${d.label}`) })}
          <div class="legend-row"><span><i style="background:${COLORS.ok}"></i>Entregues</span><span><i style="background:${COLORS.skip}"></i>Puladas</span><span><i style="background:${COLORS.cancel}"></i>Canceladas</span></div></section>
        <section class="card"><div class="card-head"><h3>Motivos de ocorrência</h3></div>${C.donut(Object.entries(reasons).sort((a, b) => b[1] - a[1]).slice(0, 5).map(([k, v], i) => ({ label: k, value: v, color: [COLORS.cancel, COLORS.skip, COLORS.violet, COLORS.sky, '#94A3B8'][i] })), P.num(Object.values(reasons).reduce((a, b) => a + b, 0)), 'ocorrências')}</section></div>
      <div class="grid g-1-1">
        <section class="card"><div class="card-head"><h3>Taxa de sucesso diária</h3><span class="muted small">linha tracejada = meta</span></div>${C.line(days.map(d => d.label), [{ name: 'Sucesso', color: COLORS.blue, values: success, area: true }, { name: 'Meta', color: COLORS.cancel, values: days.map(() => goals.successRate), dash: true }], { max: 100, unit: '%', height: 200, tipLabels: days.map(d => `${d.wd} ${d.label}`) })}</section>
        <section class="card"><div class="card-head"><h3>Entregas por dia da semana e horário</h3></div>${C.heat(wd, hrs, heat)}</section></div>
      <section class="card pad0"><div class="card-head pad"><h3>Desempenho por motorista</h3><span class="muted small">período selecionado, sem o dia de hoje</span></div>
        <table class="tbl"><thead><tr><th>Motorista</th><th>Rotas</th><th>Entregas</th><th>Sucesso</th>${P.API ? '' : '<th>Pontualidade</th>'}<th>Km</th><th>Custo/entrega</th>${P.API ? '' : '<th>Avaliação</th>'}</tr></thead><tbody>
        ${drows.map(x => `<tr data-href="#/driver/${x.d.id}"><td data-l="Motorista"><span class="who"><span class="avatar xs">${P.initials(x.d.name)}</span>${esc(x.d.name)}</span></td><td data-l="Rotas">${x.routes}</td><td data-l="Entregas">${x.delivered}</td>
          <td data-l="Sucesso"><span class="prog-cell">${progress(x.success, x.success >= goals.successRate ? 'green' : 'amber')}<small>${x.success}%</small></span></td>${P.API ? '' : `<td data-l="Pontualidade">${x.onTime}%</td>`}<td data-l="Km">${P.num(x.km, 0)}</td><td data-l="Custo/entrega">${P.money((x.km * 1.85 + x.stops * 2.5) / Math.max(1, x.delivered))}</td>${P.API ? '' : `<td data-l="Avaliação">${P.num(x.rating, 1)}</td>`}</tr>`).join('')}</tbody></table></section>
      <p class="muted small center" style="margin:16px 0 4px">${P.API ? 'Dados reais da Orbi API (os mesmos do app do motorista). Distâncias estimadas pelas coordenadas das entregas.' : 'Dias anteriores: histórico simulado para demonstração. Hoje: dados reais do app.'}</p>`,
      mount(root) {
        $$('[data-d]', root).forEach(b => b.onclick = () => { repState.days = +b.dataset.d; render(); });
        $$('tr[data-href]', root).forEach(tr => tr.onclick = () => go(tr.dataset.href));
        $('#print', root).onclick = () => window.print();
        $('#csv', root).onclick = () => { P.downloadCSV(`orbi-relatorio-${repState.days}d.csv`, [['Dia', 'Rotas', 'Paradas', 'Entregues', 'Puladas', 'Canceladas', 'Sucesso (%)', 'Km']].concat(days.map((d, i) => [d.label, d.routes, d.stops, d.delivered, d.skipped, d.cancelled, success[i], P.num(d.km, 1)]))); P.toast('Relatório CSV gerado', 'download'); };
      }
    };
  };

  // ==========================================================================
  // MENSAGENS
  // ==========================================================================
  const QUICK = ['Qual sua previsão de chegada?', 'Priorize a próxima parada', 'O cliente ligou e está aguardando', 'Tudo certo por aí?', 'Pode me ligar quando puder?'];
  let keepFocus = false;
  pages.messages = (p) => {
    if (P.API) return { active: 'messages', title: 'Mensagens', html: `<section class="card">${empty('chat', 'Mensagens ainda não disponíveis', 'A Orbi API ainda não tem conversas entre gestor e motorista. Quando esse recurso existir na API, ele aparece aqui.')}</section>` };
    const me = P.me();
    const conv = P.S.routes.filter(r => r.driverId && (P.chatOf(r.id).length || ['IN_PROGRESS', 'DELAYED', 'ASSIGNED'].includes(r.status)))
      .sort((a, b) => { const la = P.chatOf(a.id).slice(-1)[0], lb = P.chatOf(b.id).slice(-1)[0]; return (lb ? Date.parse(lb.at) : 0) - (la ? Date.parse(la.at) : 0); });
    const r = p.id ? P.route(p.id) : null;
    if (r) P.act.readChat(r.id);
    const d = r && P.user(r.driverId);
    let last = '';
    return {
      active: 'messages', title: 'Mensagens', crumb: 'Conversas com os motoristas, por rota',
      html: `<div class="chat ${r ? 'has-thread' : ''}">
        <aside class="conv card pad0">${conv.length ? conv.map(c => { const o = P.user(c.driverId); const lm = P.chatOf(c.id).slice(-1)[0]; const n = P.unreadChat(c.id, me); return `<a class="cv ${r && r.id === c.id ? 'on' : ''}" href="#/messages/${c.id}"><span class="avatar">${P.initials(o.name)}</span><span class="cv-main"><b>${esc(o.name)} <span class="badge neutral">${esc(c.code)}</span></b><small class="${n ? 'unread' : ''}">${lm ? (lm.from === me.id ? 'Você: ' : '') + esc(lm.text) : 'Iniciar conversa'}</small></span><span class="cv-end"><small>${lm ? P.rel(lm.at) : ''}</small>${n ? `<em>${n}</em>` : ''}${P.prioRank(c) ? ic('zap', 'xs') : ''}</span></a>`; }).join('') : empty('chat', 'Sem conversas', 'As conversas ficam ligadas às rotas.')}</aside>
        <section class="thread-pane card pad0">${r ? `
          <header class="th-head"><a class="icon-btn only-m" href="#/messages" aria-label="Voltar">${ic('back')}</a><span class="avatar">${P.initials(d.name)}</span><span class="grow"><b>${esc(d.name)}</b><small class="block muted">${esc(r.code)} · ${P.ROUTE_STATUS[r.status][0]} · ${esc(r.window)}</small></span>
            <button class="icon-btn" id="call" aria-label="Ligar">${ic('phone')}</button><button class="icon-btn" id="prio" aria-label="Prioridade">${ic('zap')}</button><a class="icon-btn" href="#/route/${r.id}" aria-label="Abrir rota">${ic('route')}</a></header>
          <div class="msgs" id="msgs">${P.chatOf(r.id).length ? P.chatOf(r.id).map(m => { const day = P.date(m.at); const h = day !== last ? `<div class="m-day">${day}</div>` : ''; last = day; if (m.kind === 'system') return h + `<div class="m-sys">${esc(m.text)} · ${P.hhmm(m.at)}</div>`; const mine = m.role === 'ADMIN'; return h + `<div class="m ${mine ? 'mine' : ''} ${m.kind}">${!mine || m.from !== me.id ? `<span class="m-name">${esc(m.name)}</span>` : ''}${m.kind === 'priority' ? `<span class="m-tag">${ic('zap', 'xs')} ${P.PRIORITY[(m.meta || {}).level || 'HIGH'][0]}</span>` : m.kind === 'sos' ? `<span class="m-tag">${ic('shield', 'xs')} Alerta de segurança</span>` : m.kind === 'ack' ? `<span class="m-tag">${ic('check', 'xs')} Ciente</span>` : ''}${esc(m.text)}<small>${P.hhmm(m.at)}</small></div>`; }).join('') : empty('chat', 'Nenhuma mensagem', 'Envie a primeira mensagem para o motorista.')}</div>
          <footer class="th-foot"><div class="chips">${QUICK.map(q => `<button class="chip" data-q="${esc(q)}">${esc(q)}</button>`).join('')}</div>
            <form class="composer" id="f"><input id="txt" placeholder="Mensagem para ${esc(P.first(d.name))}" autocomplete="off"><button class="btn primary icon" aria-label="Enviar">${ic('send', 'sm')}</button></form>
            <p class="muted small hint">${ic('info', 'xs')} A mensagem aparece no app do motorista na hora. Abra o ${protoA('app')} em outra aba como ${esc(P.first(d.name))} para responder.</p></footer>`
          : `<div class="th-empty">${empty('chat', 'Selecione uma conversa', 'Escolha uma rota ao lado para conversar com o motorista.')}</div>`}</section></div>`,
      mount(root) {
        if (!r) return;
        const msgs = $('#msgs', root); msgs.scrollTop = msgs.scrollHeight;
        const send = t => { t = t.trim(); if (!t) return; P.act.message(r.id, t); keepFocus = true; render(); };
        $('#f', root).onsubmit = e => { e.preventDefault(); send($('#txt', root).value); };
        $$('[data-q]', root).forEach(b => b.onclick = () => send(b.dataset.q));
        $('#call', root).onclick = () => P.toast(`Ligando para ${d.name}… (simulado)`, 'phone');
        $('#prio', root).onclick = () => priorityModal(r);
        if (keepFocus) { keepFocus = false; $('#txt', root).focus(); }
      }
    };
  };

  // ==========================================================================
  // CONFIGURAÇÕES
  // ==========================================================================
  const setState = { tab: 'company' };
  pages.settings = () => {
    const co = P.S.company; const fi = co.fleetInsurance || {}; const g = co.goals || { successRate: 92, onTime: 90 };
    if (P.API && !['connection', 'team', 'goals', 'data'].includes(setState.tab)) setState.tab = 'connection';
    const TABS = P.API ? [['connection', 'Conexão com a API', 'link'], ['team', 'Equipe de gestão', 'users'], ['goals', 'Metas', 'trend'], ['data', 'Aparência', 'settings']] : [['company', 'Empresa', 'building'], ['fleet', 'Frota e seguro', 'truck'], ['team', 'Equipe de gestão', 'users'], ['goals', 'Metas', 'trend'], ['integrations', 'Integrações', 'link'], ['data', 'Aparência e dados', 'settings']];
    const company = P.drivers().filter(d => P.vehicleOf(d).ownership === 'COMPANY');
    const tabs = {
      connection: P.API ? `<div class="kv"><div><span>Servidor</span><b>${esc(CFG.apiPublicUrl || '')}</b></div><div><span>Situação</span><b>${{ ok: '<span class="txt-green">Conectado</span>', syncing: 'Atualizando…', loading: 'Conectando…', waking: '<span class="txt-amber">Acordando o servidor</span>', error: '<span class="txt-red">Sem conexão</span>', idle: '—' }[P.sync.state] || '—'}</b></div>
        <div><span>Última atualização</span><b>${P.sync.last ? P.hhmm(P.sync.last) + ' (' + P.rel(P.sync.last) + ')' : '—'}</b></div><div><span>Frequência</span><b>a cada ${CFG.pollSeconds || 10} segundos</b></div>
        <div><span>Sessão expira em</span><b>${P.fmtMin(Math.max(0, P.sessionExpiresIn() / 60))}</b></div><div><span>Dados</span><b>${P.S.routes.length} rotas · ${P.S.routes.reduce((a, r) => a + r.stops.length, 0)} entregas · ${P.drivers().length} motoristas</b></div></div>
        <div class="row end" style="margin-top:12px"><button class="btn primary" id="syncNow">${ic('refresh', 'sm')} Atualizar agora</button></div>
        <div class="callout neutral">${ic('info', 'sm')}<span>O portal lê e grava na mesma Orbi API que o app do motorista usa. O plano gratuito do Render “dorme” sem uso; a primeira conexão pode levar até 1 minuto.</span></div>` : '',
      company: `<div class="form-2">${[['name', 'Razão social', co.name], ['cnpj', 'CNPJ', co.cnpj], ['phone', 'Telefone', co.phone], ['email', 'E-mail', co.email]].map(f => `<div><label class="lbl">${f[1]}</label><div class="input"><input id="${f[0]}" value="${esc(f[2] || '')}"></div></div>`).join('')}</div>
        <label class="lbl">Centro de distribuição</label><div class="input">${ic('pin', 'sm')}<input id="depot" value="${esc(co.depot || '')}"></div>
        <div class="row end" style="margin-top:14px"><button class="btn primary" id="saveCo">Salvar</button></div>`,
      fleet: `<p class="muted" style="margin-top:0">Seguro padrão usado nos <b>veículos da empresa</b>. O número da assistência aparece no botão de segurança do motorista.</p>
        <div class="form-2"><div><label class="lbl">Seguradora</label><div class="input"><input id="fi" value="${esc(fi.insurer || '')}"></div></div><div><label class="lbl">Apólice</label><div class="input"><input id="fp" value="${esc(fi.policy || '')}"></div></div></div>
        <label class="lbl">Assistência 24h</label><div class="input">${ic('phone', 'sm')}<input id="fa" value="${esc(fi.phone || '')}" inputmode="tel"></div>
        <div class="row end" style="margin-top:14px"><button class="btn primary" id="saveFleet">Salvar e aplicar a ${P.plural(company.length, 'veículo', 'veículos')} da empresa</button></div>
        <h3 class="h3">Veículos</h3><table class="tbl"><thead><tr><th>Motorista</th><th>Veículo</th><th>Propriedade</th><th>Assistência</th></tr></thead><tbody>${P.drivers().map(d => { const v = P.vehicleOf(d); const ins = P.insuranceOf(d); return `<tr data-href="#/driver/${d.id}"><td data-l="Motorista">${esc(d.name)}</td><td data-l="Veículo">${esc(d.vehicle)}</td><td data-l="Propriedade"><span class="badge ${P.OWNERSHIP[v.ownership][1]}">${P.OWNERSHIP[v.ownership][0]}</span></td><td data-l="Assistência">${ins.phone ? esc(ins.phone) : '<span class="txt-amber">Não cadastrada</span>'}</td></tr>`; }).join('')}</tbody></table>`,
      team: `${P.API ? '<p class="muted" style="margin-top:0">Contas com perfil ADMIN na Orbi API. Só elas entram no portal.</p>' : ''}<table class="tbl"><thead><tr><th>Nome</th><th>Cargo</th><th>E-mail</th></tr></thead><tbody>${P.admins().map(a => `<tr><td data-l="Nome"><span class="who"><span class="avatar xs">${P.initials(a.name)}</span>${esc(a.name)}${a.id === P.me().id ? ' <span class="badge blue">você</span>' : ''}</span></td><td data-l="Cargo">${esc(a.title || 'Gestor')}</td><td data-l="E-mail">${esc(a.email)}</td></tr>`).join('')}</tbody></table>
        ${P.API ? '<p class="muted small">Novos gestores são criados direto na Orbi API (perfil ADMIN).</p>' : `<h3 class="h3">Convidar gestor</h3><div class="form-2"><div><label class="lbl">Nome</label><div class="input"><input id="mn"></div></div><div><label class="lbl">E-mail</label><div class="input"><input id="me" type="email"></div></div></div>
        <label class="lbl">Cargo</label><div class="input"><input id="mt" placeholder="Ex.: Supervisor de rotas"></div><div class="form-err" id="merr"></div>
        <div class="row end" style="margin-top:14px"><button class="btn primary" id="addM">Adicionar gestor</button></div>`}
        <div class="callout neutral">${ic('lock', 'sm')}<span>Somente contas de <b>gestor/empresa</b> acessam este portal. Motoristas usam apenas o app.</span></div>`,
      goals: `<p class="muted" style="margin-top:0">As metas aparecem nos indicadores e nos relatórios.</p>
        <label class="lbl">Taxa de sucesso: <b id="gs">${g.successRate}%</b></label><input type="range" min="70" max="100" value="${g.successRate}" id="gsr" class="range">
        <label class="lbl">Pontualidade: <b id="go">${g.onTime}%</b></label><input type="range" min="60" max="100" value="${g.onTime}" id="gor" class="range">
        <div class="row end" style="margin-top:14px"><button class="btn primary" id="saveG">Salvar metas</button></div>`,
      integrations: `<div class="integ">${[['Orbi API (Spring Boot)', 'Modo demonstração: dados locais, sem chamadas de rede.', 'amber', 'Demonstração'], ['App do motorista', 'Conectado em tempo real pelo navegador (mesmo armazenamento local).', 'green', 'Conectado'], ['OpenRouteService', 'Otimização de rotas. Desativado no protótipo para não consumir a cota gratuita.', 'neutral', 'Desativado'], ['Notificações push (Firebase)', 'Planejado para o app Android real.', 'neutral', 'Planejado']].map(x => `<div class="integ-row card"><span class="integ-ic">${ic('link', 'sm')}</span><span class="grow"><b>${x[0]}</b><small class="block muted">${x[1]}</small></span><span class="badge ${x[2]}">${x[3]}</span></div>`).join('')}</div>`,
      data: `<label class="lbl">Tema</label><div class="seg">${[['light', 'Claro'], ['dark', 'Escuro'], ['auto', 'Automático']].map(t => `<button class="${(localStorage.getItem('orbi-portal-theme') || 'auto') === t[0] ? 'on' : ''}" data-th="${t[0]}">${t[1]}</button>`).join('')}</div>
        ${P.API ? '' : `<h3 class="h3">Dados de demonstração</h3><p class="muted">Volta rotas, motoristas, conversas e alertas ao estado inicial, tanto no portal quanto no app.</p><button class="btn danger" id="reset">${ic('refresh', 'sm')} Restaurar dados de demonstração</button>`}`
    };
    return {
      active: 'settings', title: 'Configurações', crumb: esc(co.name),
      html: `<div class="settings"><nav class="set-nav card pad0">${TABS.map(t => `<button class="${setState.tab === t[0] ? 'on' : ''}" data-t="${t[0]}">${ic(t[2], 'sm')}<span>${t[1]}</span></button>`).join('')}</nav><section class="card set-body"><h3 class="h3" style="margin-top:0">${TABS.find(t => t[0] === setState.tab)[1]}</h3>${tabs[setState.tab]}</section></div>`,
      mount(root) {
        $$('[data-t]', root).forEach(b => b.onclick = () => { setState.tab = b.dataset.t; render(); });
        $$('tr[data-href]', root).forEach(tr => tr.onclick = () => go(tr.dataset.href));
        const on = (id, fn) => { const e = $(id, root); if (e) e.onclick = fn; };
        on('#saveCo', () => { P.act.company({ name: $('#name', root).value.trim(), cnpj: $('#cnpj', root).value.trim(), phone: $('#phone', root).value.trim(), email: $('#email', root).value.trim(), depot: $('#depot', root).value.trim() }); P.toast('Dados da empresa salvos', 'check'); render(); });
        on('#saveFleet', () => { const n = P.act.fleetInsurance({ insurer: $('#fi', root).value.trim(), policy: $('#fp', root).value.trim(), phone: $('#fa', root).value.trim() }); P.toast(`Seguro aplicado a ${P.plural(n, 'veículo', 'veículos')} · motoristas notificados`, 'check'); render(); });
        on('#addM', () => { const n = $('#mn', root).value.trim(), e = $('#me', root).value.trim(); if (n.length < 3 || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(e)) { $('#merr', root).textContent = 'Informe nome e e-mail válidos.'; return; } if (P.S.users.some(u => u.email.toLowerCase() === e.toLowerCase())) { $('#merr', root).textContent = 'E-mail já cadastrado.'; return; } P.act.addManager({ name: n, email: e, password: 'orbi123', phone: '', title: $('#mt', root).value.trim() || 'Gestor' }); P.toast(`${n} adicionado · senha inicial orbi123`, 'check'); render(); });
        const gs = $('#gsr', root), gor = $('#gor', root);
        if (gs) { gs.oninput = () => { $('#gs', root).textContent = gs.value + '%'; }; gor.oninput = () => { $('#go', root).textContent = gor.value + '%'; }; }
        on('#saveG', () => { P.act.company({ goals: { successRate: +gs.value, onTime: +gor.value } }); P.toast('Metas atualizadas', 'check'); render(); });
        $$('[data-th]', root).forEach(b => b.onclick = () => { setTheme(b.dataset.th); render(); });
        on('#syncNow', () => { P.syncNow(true).then(() => { P.toast('Dados atualizados', 'check'); render(); }); });
        on('#reset', () => P.confirm('Restaurar dados?', 'Tudo volta ao estado inicial, no portal e no app.', 'Restaurar', true, () => { P.resetDemo(); P.toast('Dados restaurados', 'refresh'); render(); }));
      }
    };
  };

  function notFound() { return { active: '', title: 'Não encontrado', html: empty('alert', 'Página não encontrada', 'O item pode ter sido removido.') + '<p class="center"><a class="btn primary" href="#/overview">Voltar à visão geral</a></p>' }; }

  // ==========================================================================
  // Tema, roteador e sincronização ao vivo
  // ==========================================================================
  function setTheme(t) {
    try { localStorage.setItem('orbi-portal-theme', t); } catch (e) { /* ignora */ }
    const dark = t === 'dark' || (t === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  }
  function route() {
    const h = (location.hash.slice(1) || '/overview').split('?')[0].split('/').filter(Boolean);
    return { name: h[0] || 'overview', id: h[1] ? decodeURIComponent(h[1]) : null };
  }
  function render() {
    const app = $('#portal');
    const rt = route();
    if (!P.me() && rt.name !== 'login') { history.replaceState(null, '', '#/login'); return render(); }
    if (P.me() && rt.name === 'login') { history.replaceState(null, '', '#/overview'); return render(); }
    if (cleanup) { try { cleanup(); } catch (e) { console.error(e); } cleanup = null; }
    $$('.pop').forEach(p => p.remove());
    if (P.API && P.me() && !P.sync.last && rt.name !== 'login') {
      if (cleanup) { try { cleanup(); } catch (e) { /* ignora */ } cleanup = null; }
      app.innerHTML = layout('', 'Carregando…', `<section class="card loading-card"><span class="loader-ring"></span><h3>${P.sync.state === 'waking' ? 'Acordando o servidor da Orbi API…' : P.sync.state === 'error' ? 'Não foi possível conectar à Orbi API' : 'Carregando dados da Orbi API…'}</h3><p class="muted">${P.sync.state === 'error' ? esc(P.sync.error || '') + ' · tentando de novo…' : 'O plano gratuito do Render pode levar até 1 minuto para acordar.'}</p></section>`);
      bindLayout(app);
      if (!loadingStarted) { loadingStarted = true; P.syncNow(true).finally(() => { loadingStarted = false; if (P.sync.last) { P.startPolling(); render(); } else setTimeout(render, 4000); }); }
      return;
    }
    const fn = pages[rt.name] || (() => notFound());
    const pg = fn({ id: rt.id });
    current = rt;
    const scrollY = ($('#page') || {}).scrollTop || 0;
    app.innerHTML = pg.bare ? pg.html : layout(pg.active, pg.title, pg.html, { crumb: pg.crumb, actions: pg.actions, noHead: pg.noHead });
    document.title = `${pg.title || 'Entrar'} · Orbi Portal`;
    if (!pg.bare) bindLayout(app);
    if (pg.mount) cleanup = pg.mount(app) || null;
    const page = $('#page'); if (page && app.dataset.last === location.hash) page.scrollTop = scrollY;
    app.dataset.last = location.hash;
  }
  P.render = render;

  let loadingStarted = false;
  // modo API: chegaram dados novos da Orbi API (ex.: o motorista entregou pelo app)
  P.onData = () => {
    if (!P.me() || !current || current.name === 'login') return;
    const typing = /INPUT|TEXTAREA/.test((document.activeElement || {}).tagName || '') && (document.activeElement.value || '').length;
    if (document.querySelector('.layer') || typing) { pendingSync = true; return; }
    const pill = $('#syncPill'); if (pill) pill.classList.add('flash');
    render();
    const p2 = $('#syncPill'); if (p2) { p2.classList.add('flash'); p2.lastElementChild.textContent = 'Atualizado agora'; setTimeout(() => { const p3 = $('#syncPill'); if (p3) { p3.classList.remove('flash'); P.onSyncState(); } }, 2500); }
  };
  // mudanças feitas no app (outra aba) chegam aqui na hora
  let pendingSync = false;
  window.addEventListener('storage', e => {
    if (e.key !== 'orbi-prototipo-v1') return;
    P.reload();
    const pill = $('#syncPill'); if (pill) { pill.classList.add('flash'); pill.lastElementChild.textContent = 'Atualizado agora'; setTimeout(() => { pill.classList.remove('flash'); pill.lastElementChild.textContent = 'Ao vivo'; }, 2500); }
    const typing = /INPUT|TEXTAREA/.test((document.activeElement || {}).tagName || '') && (document.activeElement.value || '').length;
    if (document.querySelector('.layer') || typing) { pendingSync = true; return; }
    render();
  });
  setInterval(() => { if (pendingSync && !document.querySelector('.layer')) { pendingSync = false; render(); } }, 1500);
  // aviso de sessão perto de expirar (modo API)
  setInterval(() => {
    if (!P.API || !P.me()) return;
    const main = $('.main'); if (!main) return;
    const old = $('#sessBanner'); const html = sessBannerHtml();
    if (old) { if (html) old.outerHTML = html; else old.remove(); } else if (html) $('#page').insertAdjacentHTML('beforebegin', html);
    const rl = $('#reLogin'); if (rl) rl.onclick = () => { P.setSession(null); go('#/login'); };
  }, 20000);
  // atualização periódica leve (horários relativos, atrasos)
  setInterval(() => { if (!P.API && P.me() && !document.querySelector('.layer') && current && ['overview', 'occurrences', 'routes'].includes(current.name) && !/INPUT|TEXTAREA/.test((document.activeElement || {}).tagName || '')) { P.reload(); render(); } }, 60000);

  // avisa o app que um gestor está com o portal aberto (o app desliga as respostas automáticas de demonstração)
  const beat = () => { try { if (P.me()) localStorage.setItem('orbi-portal-alive', String(Date.now())); } catch (e) { /* ignora */ } };
  setInterval(beat, 5000); beat();
  window.addEventListener('hashchange', render);
  window.addEventListener('DOMContentLoaded', () => { setTheme(localStorage.getItem('orbi-portal-theme') || 'auto'); render(); if (P.API && P.me() && P.sync.last) P.startPolling(); });
})();
