/*
 * Orbi Portal — núcleo
 * Estado compartilhado com o app (mesmo localStorage), regras de negócio e componentes de interface.
 * Nenhuma chamada de rede: tudo é simulado no navegador.
 */
(function () {
  'use strict';
  const D = window.OrbiData;
  const M = window.OrbiMap;
  const KEY = 'orbi-prototipo-v1';          // mesmo estado do app
  const SKEY = 'orbi-portal-session';       // sessão própria do portal
  const P = window.OrbiPortal = {};

  // ==========================================================================
  // Estado
  // ==========================================================================
  function migrate(s) {
    s.chats = s.chats || {}; s.chatRead = s.chatRead || {}; s.incidents = s.incidents || [];
    const seed = D.seed();
    seed.users.forEach(su => {
      const u = s.users.find(x => x.id === su.id);
      if (!u) s.users.push(su);
      else { if (su.vehicleInfo && !u.vehicleInfo) u.vehicleInfo = su.vehicleInfo; if (su.title && !u.title) u.title = su.title; }
    });
    s.company = s.company || seed.company;
    return s;
  }
  function load() {
    try { const raw = localStorage.getItem(KEY); if (raw) { const s = JSON.parse(raw); if (s && s.version === 1) return migrate(s); } } catch (e) { /* sem storage */ }
    return migrate(D.seed());
  }
  function save() { try { localStorage.setItem(KEY, JSON.stringify(P.S)); } catch (e) { /* ignora */ } }
  P.S = load();
  P.reload = () => { P.S = load(); };
  // lê o estado mais recente, altera e grava (evita sobrescrever mudanças feitas no app em outra aba)
  P.mutate = (fn) => { P.S = load(); const out = fn(P.S); save(); return out; };
  P.resetDemo = () => { P.S = migrate(D.seed()); save(); };

  P.session = () => { try { return JSON.parse(localStorage.getItem(SKEY) || 'null'); } catch (e) { return null; } };
  P.setSession = (v) => { try { if (v) localStorage.setItem(SKEY, JSON.stringify(v)); else localStorage.removeItem(SKEY); } catch (e) { /* ignora */ } };
  P.me = () => { const s = P.session(); return s && P.S.users.find(u => u.id === s.userId && u.role === 'ADMIN'); };

  // ==========================================================================
  // Utilitários
  // ==========================================================================
  P.$ = (sel, root) => (root || document).querySelector(sel);
  P.$$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
  P.esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  P.initials = n => String(n || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('');
  P.first = n => String(n || '').split(' ')[0];
  P.hhmm = d => new Date(d || Date.now()).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  P.date = d => new Date(d).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  P.rel = iso => {
    if (!iso) return '—';
    const m = Math.round((Date.now() - Date.parse(iso)) / 60000);
    if (m < 1) return 'agora'; if (m < 60) return `há ${m} min`;
    const h = Math.round(m / 60); if (h < 20) return `há ${h} h`; if (h < 44) return 'ontem';
    return `há ${Math.round(h / 24)} dias`;
  };
  P.num = (v, d) => Number(v).toLocaleString('pt-BR', { minimumFractionDigits: d || 0, maximumFractionDigits: d || 0 });
  P.km = u => P.num(M.unitsToKm(u), 1) + ' km';
  P.money = v => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  P.pct = (a, b) => b ? Math.round(a / b * 100) : 0;
  P.fmtMin = m => m >= 60 ? `${Math.floor(m / 60)}h${String(Math.round(m % 60)).padStart(2, '0')}` : `${Math.round(m)} min`;
  P.plural = (n, a, b) => `${n} ${n === 1 ? a : b}`;
  P.uid = p => p + Math.random().toString(36).slice(2, 8);

  // ==========================================================================
  // Regras do domínio (mesmas do app)
  // ==========================================================================
  const TERMINAL = P.TERMINAL = ['COMPLETED', 'SKIPPED', 'CANCELLED'];
  P.ROUTE_STATUS = { CREATED: ['Sem motorista', 'neutral'], ASSIGNED: ['A iniciar', 'neutral'], DELAYED: ['Atrasada', 'red'], IN_PROGRESS: ['Em andamento', 'blue'], COMPLETED: ['Concluída', 'green'], CANCELLED: ['Cancelada', 'red'] };
  P.STOP_STATUS = { PENDING: ['Pendente', 'neutral'], IN_TRANSIT: ['Em trânsito', 'blue'], COMPLETED: ['Entregue', 'green'], SKIPPED: ['Pulada', 'amber'], CANCELLED: ['Cancelada', 'red'] };
  P.badge = (map, st, extra) => { const v = map[st] || [st, 'neutral']; return `<span class="badge ${v[1]} ${extra || ''}">${v[0]}</span>`; };
  P.user = id => P.S.users.find(u => u.id === id);
  P.route = id => P.S.routes.find(r => r.id === id);
  P.drivers = () => P.S.users.filter(u => u.role === 'DRIVER');
  P.admins = () => P.S.users.filter(u => u.role === 'ADMIN');
  P.routeInfo = r => {
    const total = r.stops.length;
    const done = r.stops.filter(s => s.status === 'COMPLETED').length;
    const skipped = r.stops.filter(s => s.status === 'SKIPPED').length;
    const cancelled = r.stops.filter(s => s.status === 'CANCELLED').length;
    const handled = done + skipped + cancelled;
    const units = M.pathLength(M.pathThrough([D.DEPOT].concat(r.stops)));
    const kmv = M.unitsToKm(units);
    return { total, done, skipped, cancelled, handled, left: total - handled, units, km: kmv, minutes: M.unitsToMin(units) + total * 4, pct: P.pct(handled, total), cost: kmv * 1.85 + total * 2.5 };
  };
  P.nextStop = r => r.stops.find(s => s.status === 'IN_TRANSIT') || r.stops.find(s => s.status === 'PENDING');
  P.isToday = r => !r.finishedAt || (Date.now() - Date.parse(r.finishedAt)) < 16 * 3600000;
  P.stopAddr = s => `${s.street}, ${s.number}`;
  P.routeOfStop = s => P.S.routes.find(r => r.stops.includes(s));
  P.openIncidents = () => P.S.incidents.filter(i => i.status === 'OPEN');
  P.windowStart = r => { const m = /(\d{2}):(\d{2})/.exec(r.window || ''); if (!m) return null; const d = new Date(); d.setHours(+m[1], +m[2], 0, 0); return d; };
  P.prioRank = r => r.priority && r.status !== 'COMPLETED' ? (r.priority.level === 'URGENT' ? 2 : 1) : 0;
  P.PRIORITY = { HIGH: ['Alta prioridade', 'amber'], URGENT: ['Urgente', 'red'] };
  P.OWNERSHIP = { COMPANY: ['Da empresa', 'blue'], PERSONAL: ['Particular', 'amber'] };
  P.vehicleOf = u => {
    if (!u.vehicleInfo) { const [model, plate] = String(u.vehicle || '').split('·').map(x => x.trim()); u.vehicleInfo = { ownership: 'PERSONAL', model: model || '', plate: plate || '', company: { insurer: '', policy: '', phone: '' }, personal: { insurer: '', policy: '', phone: '' } }; }
    return u.vehicleInfo;
  };
  P.insuranceOf = u => { const v = P.vehicleOf(u); return v[v.ownership === 'COMPANY' ? 'company' : 'personal']; };
  P.driverState = d => {
    if (P.S.incidents.some(i => i.driverId === d.id && i.status === 'OPEN')) return ['SOS', 'red'];
    if (P.S.routes.some(r => r.driverId === d.id && r.status === 'IN_PROGRESS')) return ['Em rota', 'blue'];
    if (P.S.routes.some(r => r.driverId === d.id && ['ASSIGNED', 'DELAYED'].includes(r.status))) return ['Escalado', 'neutral'];
    return ['Disponível', 'green'];
  };
  P.chatOf = rid => P.S.chats[rid] || [];
  P.unreadChat = (rid, u) => {
    const last = ((P.S.chatRead || {})[u.id] || {})[rid];
    return P.chatOf(rid).filter(m => m.from !== u.id && (!last || Date.parse(m.at) > Date.parse(last))).length;
  };
  P.totalUnread = () => { const u = P.me(); return u ? P.S.routes.reduce((a, r) => a + P.unreadChat(r.id, u), 0) : 0; };
  P.notifs = () => { const u = P.me(); return u ? P.S.notifications.filter(n => n.userId === u.id) : []; };

  // Situação da rota (mesmas verificações do app)
  P.health = r => {
    const checks = []; const d = P.user(r.driverId);
    const inc = P.S.incidents.find(i => i.routeId === r.id && i.status === 'OPEN');
    checks.push(d ? ['ok', 'Motorista atribuído', P.esc(d.name)] : ['bad', 'Sem motorista', 'Atribua alguém para a rota sair']);
    const ws = P.windowStart(r);
    if (r.status === 'COMPLETED') checks.push(['ok', 'Rota concluída', `Finalizada ${P.rel(r.finishedAt)}`]);
    else if (r.status === 'IN_PROGRESS') checks.push(['ok', 'Rota iniciada', `Às ${P.hhmm(r.startedAt)}`]);
    else if (ws && Date.now() > ws.getTime()) checks.push(['bad', 'Não iniciada no horário', `Janela começou às ${P.hhmm(ws)} · atraso de ${P.fmtMin((Date.now() - ws) / 60000)}`]);
    else checks.push(['ok', 'Dentro do horário', ws ? `Início previsto às ${P.hhmm(ws)}` : P.esc(r.window)]);
    if (r.status === 'IN_PROGRESS') {
      const last = r.stops.filter(s => s.at).map(s => Date.parse(s.at)).concat([Date.parse(r.startedAt)]).sort((a, b) => b - a)[0];
      const mins = Math.round((Date.now() - last) / 60000);
      checks.push(mins > 30 ? ['warn', 'Sem atividade recente', `Última atualização há ${P.fmtMin(mins)}`] : ['ok', 'Atividade recente', `Última atualização ${P.rel(new Date(last).toISOString())}`]);
    }
    const iss = r.stops.filter(s => s.status === 'SKIPPED' || s.status === 'CANCELLED').length;
    const rev = r.stops.filter(s => s.review && s.review.status === 'REQUESTED').length;
    checks.push(iss ? ['warn', P.plural(iss, 'ocorrência', 'ocorrências'), rev ? `${rev} aguardando revisão` : 'Paradas puladas ou canceladas'] : ['ok', 'Sem ocorrências', 'Nenhuma parada pulada ou cancelada']);
    if (r.priority && r.status !== 'COMPLETED') checks.push(r.priority.requireAck && !r.priority.ackAt ? ['warn', 'Alerta de prioridade enviado', 'Aguardando o motorista confirmar'] : ['ok', 'Alerta de prioridade', 'Motorista ciente']);
    if (inc) checks.push(['bad', 'Alerta de segurança ativo', `${P.esc(inc.label)} · ${P.rel(inc.at)}`]);
    const worst = checks.some(c => c[0] === 'bad') ? 'bad' : checks.some(c => c[0] === 'warn') ? 'warn' : 'ok';
    return { checks, worst, inc, rev };
  };

  // Posição estimada do motorista (para o mapa ao vivo)
  function pathFrom(pos, b) {
    const onV = Math.abs(pos.x / 100 - Math.round(pos.x / 100)) < 1e-3;
    if (onV) return M.pathBetween({ x: Math.round(pos.x / 100) * 100, y: pos.y }, b);
    const xi = (b.x >= pos.x ? Math.ceil(pos.x / 100) : Math.floor(pos.x / 100)) * 100;
    return [{ x: pos.x, y: pos.y }].concat(M.pathBetween({ x: xi, y: pos.y }, b));
  }
  P.livePos = r => {
    if (r.status !== 'IN_PROGRESS') return null;
    const n = P.nextStop(r);
    const handled = r.stops.filter(s => TERMINAL.includes(s.status));
    const from = r.driverPos || (handled.length ? handled[handled.length - 1] : D.DEPOT);
    if (!n) return { x: from.x, y: from.y, heading: 0, eta: 0 };
    const path = pathFrom(from, n);
    const len = M.pathLength(path);
    const seed = r.id.split('').reduce((a, c) => a + c.charCodeAt(0), 0);
    const t = ((Date.now() / 1000 + seed * 7) % 150) / 150;   // anda e "reinicia" a cada 2,5 min
    const p = M.pointAt(path, len * Math.min(0.92, t));
    return { x: p.x, y: p.y, heading: p.heading, eta: M.unitsToMin(len * (1 - Math.min(0.92, t))), path, next: n };
  };
  P.streetAt = pos => {
    if (!pos) return D.DEPOT.street;
    if (Math.abs(pos.x / 100 - Math.round(pos.x / 100)) < 1e-3) return D.VERTICAL_STREETS[Math.round(pos.x / 100)] || '—';
    return D.HORIZONTAL_STREETS[Math.round(pos.y / 100)] || '—';
  };

  // ==========================================================================
  // Histórico simulado (para relatórios) + dia de hoje real
  // ==========================================================================
  function rng(seed) { let x = Math.abs(Math.floor(seed)) % 2147483647 || 1; return () => (x = x * 16807 % 2147483647) / 2147483647; }
  const hashStr = s => String(s).split('').reduce((a, c) => (a * 31 + c.charCodeAt(0)) | 0, 7);
  const REASONS = D.SKIP_REASONS.concat(D.CANCEL_REASONS).filter((x, i, a) => a.indexOf(x) === i && x !== 'Outro motivo').concat(['Outro motivo']);
  P.history = (days) => {
    const out = [];
    const today0 = new Date(); today0.setHours(0, 0, 0, 0);
    for (let d = days; d >= 1; d--) {
      const date = new Date(today0.getTime() - d * 86400000);
      const dayIdx = Math.floor(date.getTime() / 86400000);
      const dow = date.getDay();
      const day = { date, label: date.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }), wd: date.toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', ''), dow, routes: 0, stops: 0, delivered: 0, skipped: 0, cancelled: 0, km: 0, minutes: 0, onTime: 0, reasons: {}, hours: new Array(24).fill(0), drivers: {} };
      P.drivers().forEach(dr => {
        const r = rng(hashStr(dr.id) + dayIdx * 7919);
        if (dow === 0 && r() < 0.75) return;               // domingo quase sem rota
        const routes = dow === 6 ? 1 : 1 + (r() > 0.55 ? 1 : 0);
        const stops = routes * (5 + Math.floor(r() * 6));
        const delivered = Math.round(stops * (0.84 + r() * 0.13));
        const cancelled = Math.round((stops - delivered) * (0.25 + r() * 0.3));
        const skipped = stops - delivered - cancelled;
        const km = stops * (1.1 + r() * 0.9);
        const minutes = stops * (8 + r() * 6);
        const onTime = r() > 0.2 ? routes : routes - 1;
        for (let k = 0; k < skipped + cancelled; k++) { const rs = REASONS[Math.floor(Math.pow(r(), 1.6) * REASONS.length)]; day.reasons[rs] = (day.reasons[rs] || 0) + 1; }
        for (let k = 0; k < delivered; k++) { const h = 8 + Math.floor(Math.pow(r(), 0.9) * 10); day.hours[h]++; }
        day.drivers[dr.id] = { routes, stops, delivered, skipped, cancelled, km, minutes, onTime, rating: 4.3 + r() * 0.7 };
        day.routes += routes; day.stops += stops; day.delivered += delivered; day.skipped += skipped; day.cancelled += cancelled; day.km += km; day.minutes += minutes; day.onTime += onTime;
      });
      out.push(day);
    }
    return out;
  };
  P.todayStats = () => {
    const rs = P.S.routes.filter(P.isToday);
    const st = rs.flatMap(r => r.stops);
    const c = k => st.filter(s => s.status === k).length;
    const reasons = {}; st.filter(s => s.reason).forEach(s => { reasons[s.reason] = (reasons[s.reason] || 0) + 1; });
    const hours = new Array(24).fill(0); st.filter(s => s.status === 'COMPLETED' && s.at).forEach(s => { hours[new Date(s.at).getHours()]++; });
    const kmv = rs.reduce((a, r) => a + P.routeInfo(r).km, 0);
    const started = rs.filter(r => r.startedAt || r.status === 'COMPLETED');
    const onTime = started.filter(r => { const ws = P.windowStart(r); return !ws || !r.startedAt || Date.parse(r.startedAt) <= ws.getTime() + 20 * 60000; }).length;
    return { routes: rs.length, stops: st.length, delivered: c('COMPLETED'), skipped: c('SKIPPED'), cancelled: c('CANCELLED'), pending: st.filter(s => !TERMINAL.includes(s.status)).length, km: kmv, cost: rs.reduce((a, r) => a + P.routeInfo(r).cost, 0), reasons, hours, live: rs.filter(r => r.status === 'IN_PROGRESS').length, delayed: rs.filter(r => r.status === 'DELAYED').length, onTimePct: P.pct(onTime, started.length || 1) };
  };

  // ==========================================================================
  // Ações (gravam no estado compartilhado e notificam o app)
  // ==========================================================================
  function notify(S, userId, title, text, link) { S.notifications.unshift({ id: P.uid('n'), userId, title, text, at: new Date().toISOString(), read: false, link }); }
  function postMsg(S, rid, from, text, kind, meta) {
    S.chats[rid] = S.chats[rid] || [];
    const m = { id: P.uid('m'), from: from ? from.id : null, role: from ? from.role : 'SYSTEM', name: from ? from.name : 'Orbi', text, kind: kind || 'text', at: new Date().toISOString(), meta: meta || null };
    S.chats[rid].push(m); return m;
  }
  P.act = {
    assign(rid, did) {
      P.mutate(S => {
        const r = S.routes.find(x => x.id === rid); const me = S.users.find(u => u.id === P.session().userId);
        const prev = r.driverId; if (prev === did) return;
        r.driverId = did; r.assignedAt = new Date().toISOString();
        if (r.status === 'CREATED') r.status = 'ASSIGNED';
        if (r.status === 'IN_PROGRESS') { r.status = 'ASSIGNED'; delete r.driverPos; r.stops.forEach(x => { if (x.status === 'IN_TRANSIT') x.status = 'PENDING'; }); }
        const d = S.users.find(u => u.id === did);
        notify(S, did, 'Nova rota atribuída', `A rota ${r.code} foi atribuída a você (${r.window}).`, '#/d/route/' + r.id);
        if (prev) notify(S, prev, `Rota reatribuída · ${r.code}`, `${r.code} foi passada para ${d.name}.`, '#/d/home');
        postMsg(S, r.id, null, `Rota atribuída a ${d.name} por ${me.name}`, 'system');
      });
    },
    priority(rid, level, message, ack) {
      P.mutate(S => {
        const r = S.routes.find(x => x.id === rid); const me = S.users.find(u => u.id === P.session().userId);
        r.priority = { level, message, by: me.id, byName: me.name, at: new Date().toISOString(), requireAck: ack, ackAt: null };
        postMsg(S, rid, me, message, 'priority', { level });
        notify(S, r.driverId, `${level === 'URGENT' ? '⚡ Urgente' : 'Alta prioridade'} · ${r.code}`, message, '#/d/route/' + r.id);
      });
    },
    clearPriority(rid) {
      P.mutate(S => { const r = S.routes.find(x => x.id === rid); r.priority = null; r.priorityClearedAt = new Date().toISOString(); postMsg(S, rid, null, 'Prioridade removida pelo gestor', 'system'); if (r.driverId) notify(S, r.driverId, `Prioridade removida · ${r.code}`, 'A rota voltou ao normal.', '#/d/route/' + r.id); });
    },
    message(rid, text) {
      P.mutate(S => { const me = S.users.find(u => u.id === P.session().userId); postMsg(S, rid, me, text, 'text'); S.chatRead[me.id] = S.chatRead[me.id] || {}; S.chatRead[me.id][rid] = new Date().toISOString(); });
    },
    readChat(rid) {
      P.mutate(S => { const id = P.session().userId; S.chatRead[id] = S.chatRead[id] || {}; S.chatRead[id][rid] = new Date().toISOString(); });
    },
    seeIncident(id) {
      P.mutate(S => {
        const inc = S.incidents.find(i => i.id === id); if (!inc || inc.seenAt) return;
        const me = S.users.find(u => u.id === P.session().userId);
        inc.seenAt = new Date().toISOString();
        if (inc.routeId) postMsg(S, inc.routeId, null, `${me.name} visualizou o alerta de segurança`, 'system');
        notify(S, inc.driverId, 'Gestor visualizou seu alerta', `${me.name} já está acompanhando: ${inc.label}.`, '#/d/home');
      });
    },
    resolveIncident(id, note) {
      P.mutate(S => {
        const inc = S.incidents.find(i => i.id === id); const me = S.users.find(u => u.id === P.session().userId);
        inc.status = 'RESOLVED'; inc.resolvedAt = new Date().toISOString(); inc.resolvedBy = me.id; inc.resolution = note || '';
        if (inc.routeId) postMsg(S, inc.routeId, null, `Alerta de segurança encerrado por ${me.name}${note ? ': ' + note : ''}`, 'system');
        notify(S, inc.driverId, 'Alerta encerrado', `${me.name} marcou seu alerta como resolvido.`, '#/d/home');
      });
    },
    comment(rid, sid, text, resolveReview) {
      P.mutate(S => {
        const r = S.routes.find(x => x.id === rid); const s = r.stops.find(x => x.id === sid); const me = S.users.find(u => u.id === P.session().userId);
        const idx = r.stops.indexOf(s) + 1;
        if (text) { s.comments = s.comments || []; s.comments.push({ by: me.name, role: 'ADMIN', text, at: new Date().toISOString() }); }
        if (resolveReview && s.review && s.review.status === 'REQUESTED') {
          s.review.status = 'RESOLVED'; s.review.resolvedAt = new Date().toISOString(); s.review.answer = text || '';
          notify(S, r.driverId, `Revisão concluída · ${r.code}`, `O gestor revisou a parada #${String(idx).padStart(2, '0')}${text ? `: “${text}”` : '.'}`, '#/d/route/' + r.id);
        } else if (text && r.driverId) notify(S, r.driverId, `Resposta do gestor · ${r.code}`, `Parada #${String(idx).padStart(2, '0')}: “${text}”`, '#/d/route/' + r.id);
      });
    },
    deleteRoute(rid) { P.mutate(S => { S.routes = S.routes.filter(r => r.id !== rid); (S.deletedRoutes = S.deletedRoutes || []).push(rid); }); },
    createRoute(stops, driverId, win) {
      return P.mutate(S => {
        const id = 'r' + S.routeCounter; const code = 'RT-' + S.routeCounter; S.routeCounter++;
        const r = { id, code, driverId: driverId || null, status: driverId ? 'ASSIGNED' : 'CREATED', window: win, createdAt: new Date().toISOString(), startedAt: null, finishedAt: null, stops };
        S.routes.unshift(r);
        if (driverId) notify(S, driverId, 'Nova rota atribuída', `A rota ${code} foi atribuída a você (${win}).`, '#/d/route/' + id);
        return r;
      });
    },
    createDriver(data) {
      return P.mutate(S => {
        const u = Object.assign({ id: P.uid('u'), role: 'DRIVER', rating: 5, ratingsCount: 0, since: new Date().toISOString() }, data);
        S.users.push(u); return u;
      });
    },
    updateVehicle(uid, vi) {
      P.mutate(S => {
        const u = S.users.find(x => x.id === uid); vi.updatedAt = new Date().toISOString(); u.vehicleInfo = vi; u.vehicle = `${vi.model} · ${vi.plate}`;
        notify(S, uid, 'Veículo atualizado pela gestão', `${vi.ownership === 'COMPANY' ? 'Da empresa' : 'Particular'}: ${u.vehicle}.`, '#/d/vehicle');
      });
    },
    fleetInsurance(ins) {
      return P.mutate(S => {
        S.company.fleetInsurance = ins; let n = 0;
        S.users.filter(u => u.role === 'DRIVER').forEach(u => { const v = u.vehicleInfo; if (v && v.ownership === 'COMPANY') { v.company = Object.assign({}, ins); v.updatedAt = new Date().toISOString(); n++; notify(S, u.id, 'Seguro da frota atualizado', `${ins.insurer} · assistência ${ins.phone}`, '#/d/vehicle'); } });
        return n;
      });
    },
    company(data) { P.mutate(S => { Object.assign(S.company, data); }); },
    addManager(data) { return P.mutate(S => { const u = Object.assign({ id: P.uid('u'), role: 'ADMIN', company: S.company.name }, data); S.users.push(u); return u; }); },
    readNotif(id) { P.mutate(S => { const n = S.notifications.find(x => x.id === id); if (n) n.read = true; }); },
    readAllNotifs() { P.mutate(S => { const id = P.session().userId; S.notifications.forEach(n => { if (n.userId === id) n.read = true; }); }); }
  };

  // ==========================================================================
  // Ícones
  // ==========================================================================
  const IC = {
    grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    radar: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/><path d="M12 12 19 5"/>',
    route: '<circle cx="6" cy="19" r="2.5"/><circle cx="18" cy="5" r="2.5"/><path d="M8.5 19H16a3.5 3.5 0 0 0 0-7H8a3.5 3.5 0 0 1 0-7h7.5"/>',
    users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14a6.5 6.5 0 0 1 3.5 6"/>',
    alert: '<path d="M12 3 2 20h20z"/><path d="M12 10v4M12 17h.01"/>',
    chart: '<path d="M3 3v18h18"/><path d="M7 15l4-4 3 3 5-6"/>',
    bars: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
    chat: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
    bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    check: '<path d="M20 6 9 17l-5-5"/>',
    close: '<path d="M18 6 6 18M6 6l12 12"/>',
    menu: '<path d="M3 6h18M3 12h18M3 18h18"/>',
    chev: '<path d="m9 6 6 6-6 6"/>',
    chevd: '<path d="m6 9 6 6 6-6"/>',
    back: '<path d="M19 12H5M12 19l-7-7 7-7"/>',
    truck: '<path d="M3 6h11v10H3zM14 9h4l3 3v4h-7"/><circle cx="7" cy="18" r="2"/><circle cx="17" cy="18" r="2"/>',
    pin: '<path d="M12 21s-7-6.1-7-11.5A7 7 0 0 1 19 9.5C19 14.9 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    box: '<path d="M21 8 12 3 3 8v8l9 5 9-5z"/><path d="M3 8l9 5 9-5M12 13v8"/>',
    zap: '<path d="M13 2 3 14h9l-1 8 10-12h-9z"/>',
    shield: '<path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z"/><path d="M12 8v4M12 16h.01"/>',
    phone: '<path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z"/>',
    send: '<path d="M22 2 11 13M22 2l-7 20-4-9-9-4z"/>',
    star: '<path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z"/>',
    skip: '<path d="M5 4l10 8-10 8z"/><path d="M19 5v14"/>',
    xcircle: '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6M9 9l6 6"/>',
    download: '<path d="M12 3v12M7 10l5 5 5-5M5 21h14"/>',
    printer: '<path d="M6 9V3h12v6"/><rect x="3" y="9" width="18" height="8" rx="2"/><path d="M7 14h10v7H7z"/>',
    moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
    lock: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
    mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/>',
    eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    building: '<path d="M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16M16 9h2a2 2 0 0 1 2 2v10M8 7h4M8 11h4M8 15h4M3 21h18"/>',
    trash: '<path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"/>',
    edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
    sparkle: '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/>',
    expand: '<path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/>',
    minus: '<path d="M5 12h14"/>',
    target: '<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/>',
    filter: '<path d="M3 5h18l-7 8v6l-4-2v-4z"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 16v-4M12 8h.01"/>',
    refresh: '<path d="M21 12a9 9 0 1 1-2.6-6.4L21 8"/><path d="M21 3v5h-5"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
    trend: '<path d="M3 17 9 11l4 4 8-8"/><path d="M15 7h6v6"/>',
    wallet: '<rect x="3" y="6" width="18" height="14" rx="2"/><path d="M3 10h18M16 15h2"/>',
    link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
    tv: '<rect x="2" y="5" width="20" height="13" rx="2"/><path d="M8 21h8"/>',
    keyboard: '<rect x="2" y="6" width="20" height="12" rx="2"/><path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M7 14h10"/>'
  };
  P.ic = (name, cls) => `<svg class="ic ${cls || ''}" viewBox="0 0 24 24" aria-hidden="true">${IC[name] || ''}</svg>`;
  P.LOGO = '<svg viewBox="0 0 48 48" aria-hidden="true"><circle cx="24" cy="24" r="9" fill="currentColor"/><ellipse cx="24" cy="24" rx="20" ry="8" fill="none" stroke="currentColor" stroke-width="3" transform="rotate(-25 24 24)"/><circle cx="41.5" cy="15.5" r="3.6" fill="#38BDF8"/></svg>';

  // ==========================================================================
  // Componentes de interface: toast, modal, gaveta, dica flutuante
  // ==========================================================================
  P.toast = (msg, icon) => {
    let h = P.$('#toasts'); if (!h) { h = document.createElement('div'); h.id = 'toasts'; document.body.appendChild(h); }
    const t = document.createElement('div'); t.className = 'toast'; t.innerHTML = (icon ? P.ic(icon, 'sm') : '') + `<span>${P.esc(msg)}</span>`;
    h.appendChild(t); setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 300); }, 2800);
  };
  let layer = null;
  P.closeLayer = () => { if (layer) { layer.remove(); layer = null; document.body.classList.remove('locked'); } };
  // modal centralizado (no celular vira folha inferior)
  P.modal = (title, body, mount, opts) => {
    P.closeLayer();
    opts = opts || {};
    layer = document.createElement('div'); layer.className = 'layer';
    layer.innerHTML = `<div class="scrim" data-close></div><div class="modal ${opts.wide ? 'wide' : ''}" role="dialog" aria-modal="true" aria-label="${P.esc(title)}">
      <div class="modal-head"><h2>${P.esc(title)}</h2><button class="icon-btn" data-close aria-label="Fechar">${P.ic('close')}</button></div><div class="modal-body">${body}</div></div>`;
    document.body.appendChild(layer); document.body.classList.add('locked');
    P.$$('[data-close]', layer).forEach(b => b.addEventListener('click', P.closeLayer));
    const first = P.$('input, textarea, select', layer); if (first && !opts.noFocus) setTimeout(() => first.focus(), 60);
    if (mount) mount(P.$('.modal', layer), P.closeLayer);
  };
  P.confirm = (title, text, okLabel, danger, run) => {
    P.modal(title, `<p class="muted" style="margin:0 0 20px">${P.esc(text)}</p><div class="row end"><button class="btn ghost" data-close>Cancelar</button><button class="btn ${danger ? 'danger' : 'primary'}" id="ok">${P.esc(okLabel)}</button></div>`,
      (m, close) => { P.$('[data-close].btn', m).onclick = close; P.$('#ok', m).onclick = () => { close(); run(); }; }, { noFocus: true });
  };
  document.addEventListener('keydown', e => { if (e.key === 'Escape') P.closeLayer(); });

  // dica flutuante para gráficos (data-tip)
  let tip = null;
  document.addEventListener('pointermove', e => {
    const t = e.target.closest && e.target.closest('[data-tip]');
    if (!t) { if (tip) { tip.remove(); tip = null; } return; }
    if (!tip) { tip = document.createElement('div'); tip.className = 'chart-tip'; document.body.appendChild(tip); }
    tip.innerHTML = t.getAttribute('data-tip');
    const w = tip.offsetWidth, h = tip.offsetHeight;
    let x = e.clientX + 14, y = e.clientY - h - 12;
    if (x + w > innerWidth - 8) x = e.clientX - w - 14;
    if (y < 8) y = e.clientY + 18;
    tip.style.left = x + 'px'; tip.style.top = y + 'px';
  });
  document.addEventListener('scroll', () => { if (tip) { tip.remove(); tip = null; } }, true);

  // CSV
  P.downloadCSV = (name, rows) => {
    const csv = rows.map(r => r.map(v => { const s = String(v == null ? '' : v); return /[;"\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s; }).join(';')).join('\n');
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; document.body.appendChild(a); a.click();
    setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 500);
  };

  P.D = D; P.M = M;
})();
