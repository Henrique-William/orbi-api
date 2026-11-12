/*
 * Configuração do portal.
 *   mode: 'api'  -> usa a Orbi API real (dados do app Android)
 *         'demo' -> usa os dados de demonstração (sem internet). Também dá para forçar com ?demo na URL.
 *   apiBase: '/api' -> o portal é servido pela própria Orbi API, então /api é o mesmo endereço (sem CORS).
 */
window.ORBI_CONFIG = {
  mode: /[?&]demo\b/.test(location.search) ? 'demo' : 'api',
  apiBase: '/api',
  apiPublicUrl: 'https://orbi-api-em6s.onrender.com',
  pollSeconds: 10,
  companyName: 'Orbi Logística',
  prototipoUrl: null
};
