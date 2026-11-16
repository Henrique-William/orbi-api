# 📋 Plano de Testes: Novas Máquinas de Estado, Ciclos de Vida e Otimização de Rotas

Este documento define a estratégia, matriz de cenários, procedimentos de execução e critérios de aceitação para homologação das alterações implementadas na **Orbi API**.

---

## 🎯 1. Objetivos e Escopo

### 1.1 Objetivos
- Garantir que as máquinas de estado de **Deliveries** e **Routes** funcionem em estrita aderência à sequência de negócio definida.
- Validar o preenchimento automático e preciso dos campos de auditoria temporal (`picked_up_at`, `assigned_at`, `finalized_at`) e métricas (`total_stops`).
- Homologar a desacoplação do motorista (`driverId`) no payload de otimização de rotas e o suporte a valores nulos.
- Validar o gatilho automático de conclusão da rota (`COMPLETED`) quando todos os pedidos forem finalizados (`DELIVERED` ou `CANCELLED`).
- Garantir a integridade dos dados existentes via script de migração `patch.sql`.
- Assegurar que os controles de acesso (RBAC e propriedade de motorista/administrador) permaneçam invioláveis.

### 1.2 Fora do Escopo
- Alterações no algoritmo TSP interno do OpenRouteService.
- Fluxos de autenticação primária (login/registro), exceto no que tange à validação dos tokens nos endpoints modificados.

---

## 🏗️ 2. Ambientes e Estratégia de Teste

| Nível de Teste | Ferramentas | Foco |
| :--- | :--- | :--- |
| **Unitário** | JUnit 5, Mockito | Regras de negócio, transições de estado, validação de timestamps e erros 400/403/404 em controladores e serviços isolados. |
| **Integração / API** | Spring MockMvc, cURL, Postman | Serialização JSON, query parameters, persistência JPA, transações e validações de cabeçalho Bearer. |
| **Banco de Dados** | MySQL Server / Docker | Aplicação do script `patch.sql`, idempotência de DDL e consistência de dados migrados. |

---

## 🧪 3. Matriz de Cenários de Teste

### 3.1 Ciclo de Vida de Deliveries (`/api/deliveries`)

| ID | Cenário | Passos | Entrada / Condição | Resultado Esperado | Prioridade |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DEL-01** | Inicialização da Entrega | Rota criada via optimize | N/A | Entrega criada com `status = TO_BE_DELIVERED`, `picked_up_at = null`, `delivered_at = null`. | Alta |
| **DEL-02** | Transição 1: `TO_BE_DELIVERED` $\rightarrow$ `IN_TRANSIT` | `PATCH /api/deliveries/{id}/next-status` | Status atual: `TO_BE_DELIVERED` | Status torna-se `IN_TRANSIT`, `picked_up_at` preenchido com timestamp atual (`now()`), `delivered_at = null`. HTTP 204. | Crítica |
| **DEL-03** | Transição 2: `IN_TRANSIT` $\rightarrow$ `OUT_FOR_DELIVERY` | `PATCH /api/deliveries/{id}/next-status` | Status atual: `IN_TRANSIT` | Status torna-se `OUT_FOR_DELIVERY`, `picked_up_at` preservado. HTTP 204. | Crítica |
| **DEL-04** | Transição 3: `OUT_FOR_DELIVERY` $\rightarrow$ `DELIVERED` (Parcial) | `PATCH /api/deliveries/{id}/next-status` | Outras entregas da rota ainda ativas | Status torna-se `DELIVERED`, `delivered_at` preenchido (`now()`). Rota permanece em seu status atual (`IN_PROGRESS`). HTTP 204. | Crítica |
| **DEL-05** | Transição 3: `OUT_FOR_DELIVERY` $\rightarrow$ `DELIVERED` (Última entrega) | `PATCH /api/deliveries/{id}/next-status` | Todas as demais entregas já estão em `DELIVERED` ou `CANCELLED` | Entrega torna-se `DELIVERED` com `delivered_at`. **Rota é automaticamente movida para `COMPLETED` e seu `finalized_at` é preenchido**. HTTP 204. | Crítica |
| **DEL-06** | Cancelamento a partir de `TO_BE_DELIVERED` | `PATCH /api/deliveries/{id}/cancel` | Status atual: `TO_BE_DELIVERED` | Status torna-se `CANCELLED`. Se todas da rota estiverem concluídas/canceladas, move rota para `COMPLETED`. HTTP 204. | Alta |
| **DEL-07** | Cancelamento a partir de `IN_TRANSIT` | `PATCH /api/deliveries/{id}/cancel` | Status atual: `IN_TRANSIT` | Status torna-se `CANCELLED`. HTTP 204. | Alta |
| **DEL-08** | Cancelamento a partir de `OUT_FOR_DELIVERY` | `PATCH /api/deliveries/{id}/cancel` | Status atual: `OUT_FOR_DELIVERY` | Status torna-se `CANCELLED`. HTTP 204. | Alta |
| **DEL-09** | Bloqueio de cancelamento em entrega `DELIVERED` | `PATCH /api/deliveries/{id}/cancel` | Status atual: `DELIVERED` | HTTP 400 Bad Request ("Não é possível cancelar uma entrega já finalizada."). Status inalterado. | Alta |
| **DEL-10** | Bloqueio de avanço em entrega `DELIVERED` | `PATCH /api/deliveries/{id}/next-status` | Status atual: `DELIVERED` | HTTP 400 Bad Request ("A entrega já foi finalizada."). | Média |
| **DEL-11** | Bloqueio de avanço em entrega `CANCELLED` | `PATCH /api/deliveries/{id}/next-status` | Status atual: `CANCELLED` | HTTP 400 Bad Request ("A entrega já foi finalizada."). | Média |
| **DEL-12** | Controle de Acesso: Motorista Não Autorizado | `PATCH /api/deliveries/{id}/next-status` | Token JWT de motorista diferente do atribuído à rota | HTTP 403 Forbidden. Nenhuma alteração persistida. | Alta |
| **DEL-13** | Bloqueio de avanço de entrega sem motorista atribuído | `PATCH /api/deliveries/{id}/next-status` | Rota sem motorista atribuído (`driver = null`), chamado por `ADMIN` ou usuário | HTTP 400 Bad Request ("Não é possível avançar o status da entrega pois a rota não possui motorista atribuído."). Status inalterado. | Crítica |
| **DEL-14** | Permissão de cancelamento de entrega sem motorista | `PATCH /api/deliveries/{id}/cancel` | Rota sem motorista atribuído (`driver = null`), chamado por `ADMIN` ou criador | Status atualizado para `CANCELLED`. Conclui rota se todas estiverem finalizadas. HTTP 204. | Alta |
| **DEL-15** | Acesso com Role `DRIVER` (`SCOPE_DRIVER`) | `PATCH /api/deliveries/{id}/next-status` ou `/cancel` | Token com `SCOPE_DRIVER` atribuído à rota | HTTP 204. Transição efetuada com sucesso (corrige erro 403 anterior). | Crítica |
| **DEL-16** | Início Automático da Rota (`IN_PROGRESS`) | `PATCH /api/deliveries/{id}/next-status` | Rota em `ASSIGNED` e primeira entrega avança para `IN_TRANSIT` | Status da rota em `tb_routes` passa automaticamente para `IN_PROGRESS`. HTTP 204. | Crítica |
| **DEL-17** | Listar Entregas por Rota | `GET /api/deliveries/route/{routeId}` | `routeId` existente | Retorna lista de `DeliveryDto` ordenados por `order` com coordenadas e status. HTTP 200. Se inexistente, HTTP 404. | Crítica |

---

### 3.2 Otimização e Ciclo de Vida de Routes (`/api/route`)

| ID | Cenário | Passos | Entrada / Condição | Resultado Esperado | Prioridade |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **ROU-01** | Criar rota com motorista (`driverId` preenchido) | `POST /api/route/optimize` | Body: `{ "driverId": "<UUID>", "locations": [...] }` | Rota criada com `status = ASSIGNED`, `assigned_at = now()`, `finalized_at = null`, `total_stops = locations.size() + 1` (retorno à origem). HTTP 200 com trajeto otimizado. | Crítica |
| **ROU-02** | Criar rota sem motorista (`driverId = null`) | `POST /api/route/optimize` | Body: `{ "driverId": null, "locations": [...] }` | Rota criada com `status = CREATED`, `driver = null`, `assigned_at = null`, `total_stops` calculado. HTTP 200. | Crítica |
| **ROU-03** | Criar rota com `driverId` inexistente | `POST /api/route/optimize` | `driverId` inexistente no banco | HTTP 404 Not Found. Nenhuma rota ou entrega persistida. | Média |
| **ROU-04** | Atribuir motorista via endpoint dedicado | `PATCH /api/route/{id}/assign-driver` | Body: `{ "driverId": "<UUID>" }` em rota `CREATED` | Motorista associado, `assigned_at = now()`, status avança para `ASSIGNED`. HTTP 204. | Crítica |
| **ROU-05** | Avançar rota de `CREATED` para `ASSIGNED` via next-status | `PATCH /api/route/{id}/next-status` | Body: `{ "driverId": "<UUID>" }` | Rota passa para `ASSIGNED` com `assigned_at = now()`. HTTP 204. | Alta |
| **ROU-06** | Bloqueio de avanço de `CREATED` sem motorista | `PATCH /api/route/{id}/next-status` | Sem body e rota sem motorista prévio | HTTP 400 Bad Request informando necessidade de motorista. | Alta |
| **ROU-07** | Avançar rota de `ASSIGNED` para `IN_PROGRESS` | `PATCH /api/route/{id}/next-status` | Rota com status `ASSIGNED` | Status passa para `IN_PROGRESS`. HTTP 204. | Alta |
| **ROU-08** | Conclusão manual de `IN_PROGRESS` para `COMPLETED` | `PATCH /api/route/{id}/next-status` | Rota com status `IN_PROGRESS` | Status passa para `COMPLETED`, `finalized_at = now()`. HTTP 204. | Alta |
| **ROU-09** | Cancelamento de rota ativa | `PATCH /api/route/{id}/cancel` | Rota em `CREATED`, `ASSIGNED` ou `IN_PROGRESS` | Status passa para `CANCELLED`. Todas as entregas atreladas (não entregues) passam para `CANCELLED`. HTTP 204. | Crítica |
| **ROU-10** | Bloqueio de cancelamento em rota `COMPLETED` | `PATCH /api/route/{id}/cancel` | Rota com status `COMPLETED` | HTTP 400 Bad Request ("Não é possível cancelar uma rota já finalizada."). | Alta |
| **ROU-11** | Consulta de rota com novos campos | `GET /api/route/{id}` | Rota existente | Resposta contém `totalStops`, `assignedAt`, `finalizedAt` (sem a lista aninhada de deliveries). HTTP 200. | Alta |
| **ROU-12** | Atomicidade e Resiliência na Criação | `POST /api/route/optimize` | Requisição abortada/cancelada pelo cliente ou falha | Nenhuma rota ou entrega é persistida no banco (`rollbackFor = Exception.class`). HTTP 400 Bad Request. | Crítica |
| **ROU-13** | Persistência do Criador da Rota (`createdBy`) | `POST /api/route/optimize` | Chamada autenticada com token JWT | Coluna `created_by` da tabela `tb_routes` preenchida com o ID do usuário autenticado. Retornado no DTO como `createdBy`. | Crítica |
| **ROU-14** | Cancelamento em Cascata de Deliveries | `PATCH /api/route/{id}/cancel` | Rota com entregas em `TO_BE_DELIVERED` e `IN_TRANSIT` | Todas as entregas associadas são atualizadas para `CANCELLED` no banco. Entregas `DELIVERED` são preservadas. HTTP 204. | Crítica |
| **ROU-15** | Listar Entregas da Rota | `GET /api/route/{id}/deliveries` | `id` de rota existente | Retorna lista de `DeliveryDto` ordenados por `order`. HTTP 200. Se inexistente, HTTP 404. | Crítica |

---

### 3.3 Gestão de Motoristas e CNH Única (`/api/user/{userId}/driver-profile`)

| ID | Cenário | Passos | Entrada / Condição | Resultado Esperado | Prioridade |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **USR-01** | Validação de Unicidade de CNH | `POST /api/user/{userId}/driver-profile` | CNH já cadastrada para outro motorista | HTTP 422 Unprocessable Entity ("CNH já cadastrada no sistema."). Nenhum perfil duplicado persistido. | Crítica |
| **USR-02** | Cadastro de Motorista com CNH Válida | `POST /api/user/{userId}/driver-profile` | CNH inédita no sistema | HTTP 200 OK. Perfil gravado e papel do usuário atualizado para `DRIVER`. | Alta |

---

### 3.4 Patch e Migração de Banco de Dados (`patch.sql`)

| ID | Cenário | Passos | Entrada / Condição | Resultado Esperado | Prioridade |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DB-01** | Adição de colunas | Executar script `patch.sql` em banco com esquema prévio | Banco MySQL com tabelas sem as novas colunas | Colunas `assigned_at`, `finalized_at`, `total_stops`, `created_by` criadas com sucesso. | Crítica |
| **DB-02** | Compatibilidade de sintaxe MySQL | Executar `patch.sql` via cliente MySQL (Workbench, DBeaver, etc.) | Script executado com sintaxe nativa MySQL | Execução bem-sucedida sem erro 1064. | Alta |
| **DB-03** | Migração de status de rotas `PENDING` $\rightarrow$ `CREATED` | `patch.sql` | Rotas legadas com `status = 'PENDING'` | Todas passam para `status = 'CREATED'`. | Alta |
| **DB-04** | Preenchimento retroativo de `total_stops` | `patch.sql` | Rotas existentes com `total_stops = 0` | Atualizado para a quantidade real de entregas associadas. | Alta |
| **DB-05** | Migração de status legados de entregas | `patch.sql` | Entregas com `REQUESTED`, `ACCEPTED`, `AT_PICKUP` | Todas atualizadas para `TO_BE_DELIVERED`. | Alta |
| **DB-06** | Conclusão automática de rotas históricas | `patch.sql` | Rota ativa com todas as entregas já entregues/canceladas | Rota passa para `COMPLETED` com `finalized_at = MAX(delivered_at)`. | Média |
| **DB-07** | Migração retroativa de `created_by` | `patch.sql` | Rotas com `user_id` legado preenchido | Coluna `created_by` preenchida com o valor correspondente de `user_id`. | Alta |
| **DB-08** | Unicidade de CNH no Banco de Dados | `patch.sql` | Adição de constraint `uk_driver_cnh` | `ALTER TABLE tb_driver_profiles ADD CONSTRAINT uk_driver_cnh UNIQUE (driver_cnh);`. | Alta |

---

## 🛠️ 4. Guia Prático de Execução

### 4.1 Execução dos Testes Automatizados (Maven)

Execute no terminal do projeto:
```powershell
# Execução da suíte completa de testes unitários das novas funcionalidades
.\mvnw test "-Dtest=DeliveryControllerTest,RouteControllerTest,UserControllerTest"
```

Critério de aprovação:
```
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

### 4.2 Roteiro de Testes Manuais com cURL

#### Passo 1: Autenticar e Obter Token
```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin","password":"<ADMIN_PASS>"}'
```
> Armazene o `accessToken` retornado em uma variável de ambiente: `TOKEN="<seu_token>"`.

---

#### Passo 2: Criar Rota sem Motorista (`driverId: null`)
```bash
curl -X POST "http://localhost:8080/api/route/optimize?startIndex=0" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "driverId": null,
    "locations": [
      {
        "address": "CD Central - São Paulo",
        "latitude": -23.55052,
        "longitude": -46.633308
      },
      {
        "address": "Entrega Pinheiros",
        "latitude": -23.561414,
        "longitude": -46.655881
      }
    ]
  }'
```
**Validação:**
- Consulte a rota gerada via `GET /api/route/{id}`:
  - `status`: `"CREATED"`
  - `assignedAt`: `null`
  - `totalStops`: `3` (origem $\rightarrow$ parada $\rightarrow$ retorno)
  - Entregas associadas com `status`: `"TO_BE_DELIVERED"` e `pickedUpAt`: `null`.

---

#### Passo 3: Atribuir Motorista à Rota
```bash
curl -X PATCH "http://localhost:8080/api/route/1/assign-driver" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "driverId": "<UUID_DO_MOTORISTA>"
  }'
```
**Validação:**
- Status da rota avança para `"ASSIGNED"`.
- `assignedAt` contém a data/hora atual.

---

#### Passo 4: Avançar Status da Rota para `IN_PROGRESS`
```bash
curl -X PATCH "http://localhost:8080/api/route/1/next-status" \
  -H "Authorization: Bearer $TOKEN"
```
**Validação:**
- Status da rota agora é `"IN_PROGRESS"`.

---

#### Passo 5: Executar Ciclo da Entrega 1
```bash
# 1. Avançar para IN_TRANSIT (gera pickedUpAt)
curl -X PATCH "http://localhost:8080/api/deliveries/1/next-status" \
  -H "Authorization: Bearer $TOKEN"

# 2. Avançar para OUT_FOR_DELIVERY
curl -X PATCH "http://localhost:8080/api/deliveries/1/next-status" \
  -H "Authorization: Bearer $TOKEN"

# 3. Finalizar como DELIVERED (gera deliveredAt)
curl -X PATCH "http://localhost:8080/api/deliveries/1/next-status" \
  -H "Authorization: Bearer $TOKEN"
```
**Validação:**
- Em `IN_TRANSIT`: `pickedUpAt` preenchido.
- Em `DELIVERED`: `deliveredAt` preenchido.
- Como as demais paradas da rota ainda estão pendentes, o status da rota permanece `"IN_PROGRESS"`.

---

#### Passo 6: Cancelar Entrega 2 e Validar Conclusão Automática da Rota
```bash
curl -X PATCH "http://localhost:8080/api/deliveries/2/cancel" \
  -H "Authorization: Bearer $TOKEN"
```
**Validação:**
- Status da entrega 2 torna-se `"CANCELLED"`.
- Finalizar a última entrega restante (entrega 3 - retorno):
```bash
curl -X PATCH "http://localhost:8080/api/deliveries/3/cancel" \
  -H "Authorization: Bearer $TOKEN"
```
- **Verificação Crítica:** Consulte a rota `GET /api/route/1`:
  - `status` da rota mudou automaticamente para `"COMPLETED"`.
  - `finalizedAt` foi preenchido com a data/hora exata da finalização do último pedido.

---

## 📊 5. Critérios de Aceite e Homologação

1. **Taxa de Sucesso nos Testes:** 100% dos testes unitários e de integração executados com status `PASSED`.
2. **Conformidade de Schema:** Nenhum atributo ausente ou divergente em relação a `doc.md`.
3. **Resiliência do Banco:** Script `patch.sql` executado em base de dados de homologação sem erros de sintaxe ou violação de integridade referencial.
4. **Segurança:** Bloqueio consistente com HTTP 403 para motoristas tentando alterar entregas ou rotas alheias.
