# 📘 Documentação da Orbi API

A **Orbi API** é uma plataforma back-end robusta desenvolvida em **Spring Boot (Java 21)** voltada para a gestão de logística inteligente, controle e otimização de rotas, acompanhamento do ciclo de vida de entregas, gerenciamento de usuários/motoristas e avaliação de serviços.

Esta documentação destina-se a desenvolvedores e sistemas externos (aplicativos móveis, dashboards web, microsserviços e parceiros) que necessitam integrar e consumir os serviços expostos pela API.

---

## 📑 Sumário

1. [Visão Geral e Arquitetura](#-visão-geral-e-arquitetura)
2. [Autenticação e Segurança](#-autenticação-e-segurança)
   - [Padrão de Autenticação JWT](#padrão-de-autenticação-jwt)
   - [Perfis de Acesso (Roles)](#perfis-de-acesso-roles)
   - [Headers Obrigatórios](#headers-obrigatórios)
3. [Máquinas de Estado e Ciclo de Vida](#-máquinas-de-estado-e-ciclo-de-vida)
   - [Ciclo de Vida da Entrega (DeliveryStatus)](#ciclo-de-vida-da-entrega-deliverystatus)
   - [Ciclo de Vida da Rota (RouteStatus)](#ciclo-de-vida-da-rota-routestatus)
4. [Fluxos Principais de Integração](#-fluxos-principais-de-integração)
5. [Referência Completa de Endpoints](#-referência-completa-de-endpoints)
   - [1. Autenticação (`/api/auth`)](#1-autenticação-apiauth)
   - [2. Usuários e Motoristas (`/api/user`)](#2-usuários-e-motoristas-apiuser)
   - [3. Rotas e Otimização (`/api/route`)](#3-rotas-e-otimização-apiroute)
   - [4. Entregas (`/api/deliveries`)](#4-entregas-apideliveries)
   - [5. Avaliações / Ratings (`/api/ratings`)](#5-avaliações--ratings-apiratings)
6. [Integração com OpenRouteService (ORS)](#-integração-com-openrouteservice-ors)
7. [Modelos de Dados (DTOs e Entidades)](#-modelos-de-dados-dtos-e-entidades)
8. [Códigos de Status HTTP e Tratamento de Erros](#-códigos-de-status-http-e-tratamento-de-erros)
9. [Exemplos Práticos de Consumo (cURL, Fetch e Axios)](#-exemplos-práticos-de-consumo)

---

## 🌐 Visão Geral e Arquitetura

- **Base URL Padrão:** `http://localhost:8080` (ou endereço do ambiente de produção/homologação).
- **Formato dos Dados:** `application/json` (UTF-8).
- **CORS Configurado:** Permite origens locais configuradas (`http://localhost:8081`, `http://127.0.0.1:8081`, `http://localhost:3000`, `http://127.0.0.1:3000`) com credenciais (`allowCredentials: true`).
- **Motor de Otimização e Roteirização:** Integração com **OpenRouteService (ORS)** para cálculo de matrizes de distância e geração de trajetos geográficos no padrão GeoJSON.

---

## 🔒 Autenticação e Segurança

### Padrão de Autenticação JWT

A API utiliza **Spring Security OAuth2 Resource Server** baseado em tokens **JWT (JSON Web Token)** assinados com chaves assimétricas **RSA (chaves pública e privada)**.

1. **Obtenção do Token:** O cliente envia credenciais para `POST /api/auth/login`.
2. **Resposta:** A API retorna o token no payload JSON (`accessToken`) e também emite um cookie seguro `HttpOnly` nomeado `token`.
3. **Consumo de Endpoints Protegidos:** O cliente deve incluir o token no cabeçalho HTTP de todas as chamadas autenticadas:
   ```http
   Authorization: Bearer <seu_access_token>
   ```
4. **Validação:** A assinatura do token é validada pela chave pública RSA da aplicação. O tempo de expiração padrão do token é de **1440 segundos (24 minutos)**.
5. **Claims Contidas no Token:**
   - `sub`: Identificador único (UUID) do usuário autenticado.
   - `iss`: Emissor do token (`orbi`).
   - `scope`: Permissões do usuário (ex: `ADMIN`, `BASIC`, `DRIVER`).
   - `exp`: Timestamp de expiração.

### Perfis de Acesso (Roles)

| Role | Identificador no Token | Descrição e Nível de Acesso |
| :--- | :--- | :--- |
| **ADMIN** | `SCOPE_ADMIN` | Acesso irrestrito a todos os endpoints, listagem completa de usuários, exclusão de rotas, exclusão de avaliações e alteração forçada de status de entregas. |
| **BASIC** | `SCOPE_BASIC` | Usuário padrão do sistema. Permissão para visualizar/atualizar seu próprio perfil, cadastrar perfil de motorista e registrar avaliações. |
| **DRIVER** | `SCOPE_DRIVER` | Usuário habilitado como motorista parceiro. Permissão para ser vinculado a rotas e atualizar o status das entregas sob sua responsabilidade. |

### Headers Obrigatórios

Para endpoints autenticados e requisições com corpo:
```http
Content-Type: application/json
Authorization: Bearer <access_token>
```

---

## 🔄 Máquinas de Estado e Ciclo de Vida

### Ciclo de Vida da Entrega (DeliveryStatus)

Cada entrega vinculada a uma rota percorre etapas controladas:

```
[TO_BE_DELIVERED] ──(next-status)──> [IN_TRANSIT] ──(next-status)──> [OUT_FOR_DELIVERY] ──(next-status)──> [DELIVERED]
        │                                   │                                    │
        └──────────────────(cancel)─────────┴────────────────────────────────────┴──> [CANCELLED]
```

- **Transição de Status (`PATCH /api/deliveries/{id}/next-status`):**
  - `TO_BE_DELIVERED` ➔ `IN_TRANSIT` (Inicia o deslocamento para coleta; preenche automaticamente a data/hora `pickedUpAt`).
  - `IN_TRANSIT` ➔ `OUT_FOR_DELIVERY` (Pacote coletado e em trânsito para o destinatário).
  - `OUT_FOR_DELIVERY` ➔ `DELIVERED` (Entrega finalizada com sucesso; preenche automaticamente a data/hora `deliveredAt`).
  - Entregas já finalizadas (`DELIVERED` ou `CANCELLED`) não podem mudar de status.
- **Automação de Conclusão da Rota:**
  - Sempre que uma entrega transiciona para `DELIVERED` ou `CANCELLED`, o sistema verifica todas as entregas da rota associada. Se todas estiverem finalizadas (`DELIVERED` ou `CANCELLED`), a rota é automaticamente atualizada para o status `COMPLETED` e seu campo `finalizedAt` é preenchido com a data/hora da alteração.
- **Cancelamento (`PATCH /api/deliveries/{id}/cancel`):**
  - Pode ocorrer a qualquer momento antes do status `DELIVERED`. Entregas já finalizadas não podem ser canceladas.
  - **Independência de Motorista:** O cancelamento de entregas independe de haver motorista atribuído à rota.
- **Regra de Propriedade:** Motoristas com papel `SCOPE_DRIVER` atribuídos à rota, o criador da rota (`createdBy`) ou administradores (`SCOPE_ADMIN`) possuem autorização para alterar status ou cancelar.
- **Obrigatoriedade de Motorista para Avanço:** Para avançar status (`/next-status`), é obrigatório que a rota possua um motorista atribuído (`route.driver != null`). Se não houver motorista, a requisição é rejeitada com `400 Bad Request` (inclusive para administradores).

### Ciclo de Vida da Rota (RouteStatus)

```
[CREATED] ──(assign / next-status)──> [ASSIGNED] ──(next-status)──> [IN_PROGRESS] ──(next-status / auto)──> [COMPLETED]
    │                                     │                                      │
    └───────────────────(cancel)──────────┴──────────────────────────────────────┴──> [CANCELLED]
```

- `CREATED`: Rota otimizada gerada sem motorista definido no momento da chamada (ou criada inicialmente).
- `ASSIGNED`: Rota com motorista ativo atribuído (preenche automaticamente `assignedAt`).
- `IN_PROGRESS`: Motorista em execução das entregas da rota.
- `COMPLETED`: Todas as entregas vinculadas à rota foram finalizadas (`DELIVERED` ou `CANCELLED`), ou a rota foi avançada até a etapa final (preenche automaticamente `finalizedAt`).
- `CANCELLED`: Rota cancelada operacionalmente (disponível a qualquer momento antes de ser concluída).

---

## 🚀 Fluxos Principais de Integração

### Fluxo 1: Onboarding e Login
1. A aplicação cliente registra o usuário via `POST /api/auth/register`.
2. Em seguida, solicita autenticação via `POST /api/auth/login`.
3. Armazena o `accessToken` recebido para ser enviado nos cabeçalhos `Authorization: Bearer <token>`.
4. Pode validar a sessão ativa via `GET /api/auth/validate`.

### Fluxo 2: Habilitação de Motorista
1. Um usuário autenticado cadastra sua CNH via `POST /api/user/{userId}/driver-profile`.
2. A API cria o perfil de motorista e atualiza a role do usuário para `DRIVER`.
3. O `userId` agora pode ser utilizado no campo `driverId` durante o planejamento de rotas.

### Fluxo 3: Otimização e Despacho de Rotas
1. A aplicação envia a requisição para `POST /api/route/optimize`, contendo `driverId` (opcional/nulo) e a lista de `locations`.
2. A API calcula a melhor sequência (TSP + fatores de pavimento rodoviário), persiste a `Route` com seu status (`ASSIGNED` se informado motorista, ou `CREATED` se nulo), armazena `totalStops` e cria as entidades `Delivery` ordenadas (iniciando em `TO_BE_DELIVERED`).
3. Para renderizar o traçado e direções turn-by-turn no mapa do app (ex: Leaflet, Google Maps, Mapbox), chama `GET /api/route/{routeId}/directions`.

### Fluxo 4: Execução da Entrega pelo Motorista
1. O motorista avança o status da entrega atual via `PATCH /api/deliveries/{deliveryId}/next-status`.
2. O status progride de `TO_BE_DELIVERED` para `IN_TRANSIT` (registrando `pickedUpAt`), depois `OUT_FOR_DELIVERY` e finalmente `DELIVERED` (registrando `deliveredAt`).
3. Quando a última entrega da rota for finalizada (entregue ou cancelada), a rota é automaticamente marcada como `COMPLETED` e seu `finalizedAt` é preenchido.

### Fluxo 5: Avaliação do Serviço
1. O destinatário ou avaliador envia uma nota (1 a 5) e comentário via `POST /api/ratings`.

---

## 📡 Referência Completa de Endpoints

---

### 1. Autenticação (`/api/auth`)

#### 1.1 Registrar Usuário
Cria uma nova conta de usuário no sistema com perfil inicial `BASIC`.

- **Método:** `POST`
- **Caminho:** `/api/auth/register`
- **Autenticação:** Nenhuma (Endpoint Público)
- **Headers:** `Content-Type: application/json`

**Corpo da Requisição (Request Body):**
```json
{
  "name": "Carlos Silva",
  "email": "carlos.silva@exemplo.com",
  "password": "SenhaSegura123!",
  "phoneNumber": "11988887777"
}
```

**Parâmetros do Body:**
| Campo | Tipo | Obrigatório | Descrição |
| :--- | :--- | :--- | :--- |
| `name` | String | Sim | Nome completo do usuário. |
| `email` | String | Sim | Endereço de e-mail único. |
| `password` | String | Sim | Senha em texto puro (será hasheada com BCrypt). |
| `phoneNumber` | String | Não | Telefone/celular do usuário. |

**Respostas:**
- `200 OK`: Usuário cadastrado com sucesso (sem corpo).
- `422 Unprocessable Entity`: E-mail já cadastrado no banco de dados.
- `400 Bad Request`: Payload mal formatado ou campos inválidos.

---

#### 1.2 Autenticar / Login
Autentica o usuário, emite o token JWT no payload e adiciona o cookie de sessão `token`.

- **Método:** `POST`
- **Caminho:** `/api/auth/login`
- **Autenticação:** Nenhuma (Endpoint Público)
- **Headers:** `Content-Type: application/json`

**Corpo da Requisição (Request Body):**
```json
{
  "email": "carlos.silva@exemplo.com",
  "password": "SenhaSegura123!"
}
```

**Respostas:**
- `200 OK`: Login bem-sucedido.
  **Headers de Resposta:**
  `Set-Cookie: token=<jwt_value>; Path=/; Max-Age=1440; HttpOnly; SameSite=Strict`
  
  **Corpo da Resposta:**
  ```json
  {
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 1440
  }
  ```
- `401 Unauthorized` / `BadCredentialsException`: Usuário inexistente ou senha incorreta (`user or password is invalid`).

---

#### 1.3 Validar Token
Verifica a validade e a expiração do token armazenado no cookie `token` da requisição HTTP.

- **Método:** `GET`
- **Caminho:** `/api/auth/validate`
- **Autenticação:** Cookie de sessão `token`
- **Headers:** `Cookie: token=<jwt>`

**Respostas:**
- `200 OK`: O token é válido e ainda está dentro do período de expiração (sem corpo).
- `401 Unauthorized`: Cookie ausente, token corrompido, com assinatura inválida ou já expirado.

---

### 2. Usuários e Motoristas (`/api/user`)

#### 2.1 Listar Todos os Usuários
Retorna uma lista paginada de todos os usuários cadastrados, ordenados pelos mais recentes.

- **Método:** `GET`
- **Caminho:** `/api/user`
- **Autenticação:** Requer Role `ADMIN` (`SCOPE_ADMIN`)
- **Headers:** `Authorization: Bearer <token>`

**Parâmetros de Consulta (Query Parameters):**
| Parâmetro | Tipo | Padrão | Descrição |
| :--- | :--- | :--- | :--- |
| `page` | Integer | `0` | Número da página desejada (inicia em 0). |
| `pageSize` | Integer | `10` | Quantidade de registros por página. |

**Exemplo de Resposta (`200 OK`):**
```json
{
  "users": [
    {
      "id": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0",
      "name": "Carlos Silva",
      "email": "carlos.silva@exemplo.com",
      "createdAt": "2026-10-15T10:30:00",
      "role": [
        {
          "roleId": 2,
          "name": "BASIC"
        }
      ]
    }
  ],
  "page": 0,
  "pageSize": 10,
  "totalPages": 1,
  "totalElements": 1
}
```
- `403 Forbidden`: Token sem perfil de administrador.

---

#### 2.2 Buscar Usuário por ID
Recupera os detalhes completos do usuário especificado.

- **Método:** `GET`
- **Caminho:** `/api/user/{userId}`
- **Autenticação:** Requer Role `ADMIN` OU o próprio usuário autenticado (`sub == userId`).
- **Headers:** `Authorization: Bearer <token>`

**Parâmetros de Caminho (Path Parameters):**
| Parâmetro | Tipo | Descrição |
| :--- | :--- | :--- |
| `userId` | UUID | Identificador único do usuário. |

**Exemplo de Resposta (`200 OK`):**
```json
{
  "id": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0",
  "name": "Carlos Silva",
  "email": "carlos.silva@exemplo.com",
  "phone": "11988887777",
  "createdAt": "2026-10-15T10:30:00",
  "company": null,
  "roles": [
    {
      "roleId": 2,
      "name": "BASIC"
    }
  ]
}
```
- `403 Forbidden`: Não autorizado a consultar dados de terceiros.
- `404 Not Found`: Usuário não encontrado.

---

#### 2.3 Atualizar Usuário
Atualiza as informações cadastrais (nome, email e papéis) do usuário.

- **Método:** `PUT`
- **Caminho:** `/api/user/{userId}`
- **Autenticação:** Requer Role `ADMIN` OU o próprio usuário autenticado (`sub == userId`).
- **Headers:** `Authorization: Bearer <token>`, `Content-Type: application/json`

**Corpo da Requisição (Request Body):**
```json
{
  "name": "Carlos Silva Junior",
  "email": "carlos.jr@exemplo.com",
  "role": [
    {
      "roleId": 2,
      "name": "BASIC"
    }
  ]
}
```

**Respostas:**
- `200 OK`: Retorna o objeto `User` atualizado.
- `403 Forbidden`: Sem permissão para alterar o usuário.
- `404 Not Found`: Usuário não encontrado.

---

#### 2.4 Excluir Usuário
Remove um usuário do sistema e revoga suas permissões de papéis.

- **Método:** `DELETE`
- **Caminho:** `/api/user/{userId}`
- **Autenticação:** Requer Role `ADMIN` OU o próprio usuário autenticado.
- **Headers:** `Authorization: Bearer <token>`

**Respostas:**
- `204 No Content`: Usuário excluído com sucesso.
- `403 Forbidden`: Sem permissão.
- `404 Not Found`: Usuário não encontrado.

---

#### 2.5 Criar Perfil de Motorista
Cria o perfil de motorista (`Driver`) associado ao usuário informado e atualiza o seu papel principal para `DRIVER`.

- **Método:** `POST`
- **Caminho:** `/api/user/{userId}/driver-profile`
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`, `Content-Type: application/json`

**Corpo da Requisição (Request Body):**
```json
{
  "cnh": "12345678900"
}
```

**Respostas:**
- `200 OK`: Perfil de motorista criado e papel atualizado para `DRIVER`.
- `404 Not Found`: Usuário não localizado para vincular o perfil.
- `422 Unprocessable Entity`: CNH já cadastrada no sistema (a coluna `driver_cnh` é única).

---

#### 2.6 Obter Perfil de Motorista
Consulta os dados do perfil de motorista e as informações do usuário atrelado.

- **Método:** `GET`
- **Caminho:** `/api/user/{userId}/driver-profile`
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`

**Exemplo de Resposta (`200 OK`):**
```json
{
  "user": {
    "id": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0",
    "name": "Carlos Silva",
    "email": "carlos.silva@exemplo.com",
    "phone": "11988887777",
    "createdAt": "2026-10-15T10:30:00",
    "roles": [
      {
        "roleId": 3,
        "name": "DRIVER"
      }
    ]
  },
  "licenseNumber": "12345678900",
  "profileCreatedAt": "2026-10-15T11:00:00"
}
```
- `404 Not Found`: Usuário ou perfil de motorista não encontrado.

---

#### 2.7 Excluir Perfil de Motorista
Exclui o registro de perfil de motorista mantendo o usuário base.

- **Método:** `DELETE`
- **Caminho:** `/api/user/{userId}/driver-profile`
- **Autenticação:** Requer Role `ADMIN` OU o próprio usuário autenticado.
- **Headers:** `Authorization: Bearer <token>`

**Respostas:**
- `204 No Content`: Perfil de motorista removido com sucesso.
- `403 Forbidden`: Sem permissão.
- `404 Not Found`: Perfil de motorista inexistente.

---

### 3. Rotas e Otimização (`/api/route`)

#### 3.1 Otimizar e Criar Rota
Recebe uma lista de coordenadas geográficas e endereços, consulta a matriz de distâncias do OpenRouteService, aplica ponderação pelo tipo de pavimento da rodovia (quando habilitado), calcula a rota ótima pelo algoritmo TSP (Vizinho Mais Próximo), cria a entidade `Route` com suas `Delivery` no banco de dados e retorna a rota reordenada.

> **Transacionalidade e Resiliência:** Toda a criação de rota e de entregas é atômica (`@Transactional(rollbackFor = Exception.class)`). Caso o cliente aborte a requisição ou ocorra qualquer erro durante o processamento, nada é persistido no banco de dados (`tb_routes` e `tb_deliveries`). O usuário autenticado criador da rota tem seu ID persistido no campo `created_by`.

- **Método:** `POST`
- **Caminho:** `/api/route/optimize`
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`, `Content-Type: application/json`

**Parâmetros de Consulta (Query Parameters):**
| Parâmetro | Tipo | Padrão | Descrição |
| :--- | :--- | :--- | :--- |
| `startIndex` | Integer | `0` | Índice do ponto de partida na lista enviada. |

**Corpo da Requisição (Request Body):**
> **Atenção:** O motorista (`driverId`) é atribuído no corpo da requisição uma única vez (fora da lista de paradas) e pode ser nulo (`null`). Se informado e válido, a rota é criada com status `ASSIGNED` e `assignedAt` preenchido. Caso seja nulo, a rota é criada com status `CREATED` e `assignedAt` como nulo. O número total de paradas é armazenado em `totalStops`.
```json
{
  "driverId": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0",
  "locations": [
    {
      "address": "Centro de Distribuição - Av. das Nações Unidas, 1000",
      "latitude": -23.550520,
      "longitude": -46.633308
    },
    {
      "address": "Entrega 1 - Rua Augusta, 500",
      "latitude": -23.553200,
      "longitude": -46.652800
    },
    {
      "address": "Entrega 2 - Av. Paulista, 1500",
      "latitude": -23.561414,
      "longitude": -46.655881
    }
  ]
}
```

**Exemplo de Resposta (`200 OK`):**
Retorna a lista de pontos ordenada pela melhor sequência de entrega e incluindo o retorno ao ponto de partida no fim:
```json
[
  {
    "address": "Centro de Distribuição - Av. das Nações Unidas, 1000",
    "latitude": -23.550520,
    "longitude": -46.633308
  },
  {
    "address": "Entrega 1 - Rua Augusta, 500",
    "latitude": -23.553200,
    "longitude": -46.652800
  },
  {
    "address": "Entrega 2 - Av. Paulista, 1500",
    "latitude": -23.561414,
    "longitude": -46.655881
  },
  {
    "address": "Centro de Distribuição - Av. das Nações Unidas, 1000",
    "latitude": -23.550520,
    "longitude": -46.633308
  }
]
```

**Respostas de Erro:**
- `400 Bad Request`: Lista de pontos nula/vazia ou coordenadas fora dos limites geográficos (-90 <= lat <= 90; -180 <= lon <= 180).
- `404 Not Found`: `driverId` informado não existe na base de dados de motoristas.
- `500 Internal Server Error`: Falha na comunicação com o OpenRouteService ou chave de API ausente.

---

#### 3.2 Listar Todas as Rotas
Retorna todas as rotas gravadas, seus dados agregados (distância total, custos, número de paradas, timestamps) e entregas associadas.

- **Método:** `GET`
- **Caminho:** `/api/route`
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`

**Exemplo de Resposta (`200 OK`):**
```json
[
  {
    "id": 1,
    "driverId": {
      "userId": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0",
      "driverName": "Carlos Silva",
      "cnh": "12345678900",
      "phone": "11988887777",
      "profileCreatedAt": "2026-10-15T11:00:00"
    },
    "createdBy": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0",
    "driverName": "Carlos Silva",
    "status": "ASSIGNED",
    "totalDistance": 18.5,
    "totalCost": 45.20,
    "totalStops": 4,
    "createdAt": "2026-10-15T11:15:00",
    "assignedAt": "2026-10-15T11:15:00",
    "finalizedAt": null
  }
]
```

---

#### 3.3 Buscar Rota por ID
Recupera os detalhes de uma rota específica pelo seu identificador numérico.

- **Método:** `GET`
- **Caminho:** `/api/route/{id}`
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`

**Parâmetros de Caminho (Path Parameters):**
| Parâmetro | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | Long | ID numérico da rota. |

**Respostas:**
- `200 OK`: Objeto `RouteResponseDto`.
- `404 Not Found`: Rota não encontrada.

---

#### 3.4 Obter Direções e Traçado da Rota (Turn-by-Turn & GeoJSON)
Consome os pontos das entregas da rota ordenados por `order` e requisita ao OpenRouteService as instruções de direção completas, manobras de trânsito, distância total em metros, duração total em segundos e traçado geográfico em formato GeoJSON LineString.

- **Método:** `GET`
- **Caminho:** `/api/route/{id}/directions`
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`

---

#### 3.5 Listar Entregas da Rota (Get Deliveries by Route ID)
Recupera a lista de todas as entregas (`tb_deliveries`) associadas a uma rota específica, ordenadas pela sequência de entrega (`order`).

- **Método:** `GET`
- **Caminho:** `/api/route/{id}/deliveries` (disponível também em `/api/deliveries/route/{routeId}`)
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`

**Parâmetros de Caminho (Path Parameters):**
| Parâmetro | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | Long | ID numérico da rota. |

**Exemplo de Resposta (`200 OK`):**
```json
[
  {
    "id": 1,
    "order": 1,
    "status": "IN_TRANSIT",
    "address": "Av. Francisco Glicério - Campinas, SP",
    "latitude": -22.90410000,
    "longitude": -47.05990000,
    "recipientEmail": "marcos.silva@outlook.com",
    "pickedUpAt": "2026-10-25T14:30:00",
    "deliveredAt": null
  },
  {
    "id": 2,
    "order": 2,
    "status": "TO_BE_DELIVERED",
    "address": "Rua Barão de Jaguara - Campinas, SP",
    "latitude": -22.90720000,
    "longitude": -47.05730000,
    "recipientEmail": "ana.souza@gmail.com",
    "pickedUpAt": null,
    "deliveredAt": null
  }
]
```

**Respostas de Erro:**
- `404 Not Found`: Rota não encontrada.

**Parâmetros de Caminho (Path Parameters):**
| Parâmetro | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | Long | ID numérico da rota. |

**Exemplo de Resposta (`200 OK`):**
```json
{
  "routeId": 1,
  "distanceMeters": 14250.8,
  "durationSeconds": 1680.0,
  "geometry": {
    "type": "LineString",
    "coordinates": [
      [-46.633308, -23.550520],
      [-46.634100, -23.551200],
      [-46.652800, -23.553200]
    ]
  },
  "segments": [
    {
      "distanceMeters": 5200.0,
      "durationSeconds": 620.0,
      "steps": [
        {
          "name": "Av. Brigadeiro Luís Antônio",
          "instruction": "Vire à direita na Av. Brigadeiro Luís Antônio",
          "distanceMeters": 450.0,
          "durationSeconds": 60.0,
          "type": 1,
          "wayPoints": [0, 4]
        }
      ]
    }
  ],
  "extras": {
    "surface": {
      "values": [[0, 20, 1]],
      "summary": [
        {
          "value": 1,
          "distanceMeters": 14250.8,
          "amount": 100.0
        }
      ]
    }
  }
}
```

**Respostas de Erro:**
- `404 Not Found`: Rota não encontrada.
- `500 / IllegalStateException`: A rota possui menos de 2 paradas para cálculo de trajeto ou o ORS retornou erro.

---

#### 3.5 Excluir Rota
Exclui uma rota e remove em cascata todas as entregas vinculadas a ela.

- **Método:** `DELETE`
- **Caminho:** `/api/route/{id}`
- **Autenticação:** Requer Role `ADMIN` (`SCOPE_ADMIN`)
- **Headers:** `Authorization: Bearer <token>`

**Respostas:**
- `204 No Content`: Rota e entregas excluídas com sucesso.
- `403 Forbidden`: Usuário não possui perfil `ADMIN`.
- `404 Not Found`: Rota inexistente.

---

#### 3.6 Avançar Status da Rota (Next Status)
Avança o ciclo de vida da rota na sequência: `CREATED` ➔ `ASSIGNED` ➔ `IN_PROGRESS` ➔ `COMPLETED`.

- **Método:** `PATCH`
- **Caminho:** `/api/route/{id}/next-status`
- **Autenticação:** Requer Role `DRIVER`, `BASIC` ou `ADMIN` (administrador, motorista atribuído ou criador da rota).
- **Headers:** `Authorization: Bearer <token>`, `Content-Type: application/json`

**Corpo da Requisição (Opcional ao transicionar de CREATED para ASSIGNED):**
```json
{
  "driverId": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0"
}
```

**Regras de Transição:**
- `CREATED` ➔ `ASSIGNED`: Exige a atribuição de um motorista (seja passado no corpo ou já vinculado previamente). Define `assignedAt = LocalDateTime.now()`.
- `ASSIGNED` ➔ `IN_PROGRESS`: Motorista inicia a execução do trajeto.
- `IN_PROGRESS` ➔ `COMPLETED`: Conclusão manual da rota. Define `finalizedAt = LocalDateTime.now()`.
- Rotas já finalizadas (`COMPLETED` ou `CANCELLED`) não podem avançar de status.

**Respostas:**
- `204 No Content`: Status avançado com sucesso.
- `400 Bad Request`: Transição inválida, rota já finalizada ou ausência de motorista ao avançar para `ASSIGNED`.
- `403 Forbidden`: Sem permissão para alterar esta rota.
- `404 Not Found`: Rota ou motorista não encontrado.

---

#### 3.7 Atribuir Motorista à Rota (Assign Driver)
Vincula um motorista a uma rota existente. Atualiza o status de `CREATED` para `ASSIGNED` e registra `assignedAt`.

- **Método:** `PATCH`
- **Caminho:** `/api/route/{id}/assign-driver`
- **Autenticação:** Requer Role `DRIVER`, `BASIC` ou `ADMIN`.
- **Headers:** `Authorization: Bearer <token>`, `Content-Type: application/json`

**Corpo da Requisição (Request Body):**
```json
{
  "driverId": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0"
}
```

**Respostas:**
- `204 No Content`: Motorista atribuído com sucesso.
- `400 Bad Request`: Rota já finalizada (`COMPLETED` ou `CANCELLED`) ou `driverId` ausente.
- `403 Forbidden`: Sem permissão para alterar esta rota.
- `404 Not Found`: Rota ou motorista não encontrado.

---

#### 3.8 Cancelar Rota
Cancela a rota operacionalmente, alterando o status para `CANCELLED`. Pode ser executado em qualquer momento antes da rota estar finalizada.

- **Cancelamento em Cascata de Entregas:** Ao cancelar a rota, todas as entregas associadas que não estejam no status `DELIVERED` têm seu status automaticamente alterado para `CANCELLED` no banco de dados (`tb_deliveries`).

- **Método:** `PATCH`
- **Caminho:** `/api/route/{id}/cancel`
- **Autenticação:** Requer Role `DRIVER`, `BASIC` ou `ADMIN`.
- **Headers:** `Authorization: Bearer <token>`

**Respostas:**
- `204 No Content`: Rota cancelada com sucesso.
- `400 Bad Request`: Não é possível cancelar uma rota já finalizada (`COMPLETED`) ou já cancelada (`CANCELLED`).
- `403 Forbidden`: Sem permissão para alterar esta rota.
- `404 Not Found`: Rota não encontrada.

---

### 4. Entregas (`/api/deliveries`)

#### 4.1 Avançar Status da Entrega (Next Status)
Avança a entrega para a próxima fase do fluxo de transporte respeitando a ordem estrita.

- **Método:** `PATCH`
- **Caminho:** `/api/deliveries/{id}/next-status`
- **Autenticação:** Requer Role `DRIVER`, `ADMIN` ou `BASIC`.
- **Regra de Segurança:** O usuário requisitante deve ser `ADMIN`, o criador da rota (`route.createdBy.id == token.sub`) ou o motorista vinculado à rota da respectiva entrega (`route.driver.userId == token.sub`).
- **Obrigatoriedade de Motorista Atribuído:** É mandatório que a rota da entrega possua motorista atribuído (`route.driver != null`). Caso a rota não possua motorista, a requisição é rejeitada com `400 Bad Request` (inclusive para chamadas de administradores).

**Regra de Transição:**
- `TO_BE_DELIVERED` ➔ `IN_TRANSIT` (Inicia coleta/deslocamento; define a data e hora atual em `pickedUpAt`).
- `IN_TRANSIT` ➔ `OUT_FOR_DELIVERY` (Pacote coletado e em trânsito de entrega ao destinatário).
- `OUT_FOR_DELIVERY` ➔ `DELIVERED` (Entrega concluída; define a data e hora atual em `deliveredAt`).
- **Automação de Início da Rota (`IN_PROGRESS`):** Ao avançar qualquer entrega (iniciando o trajeto), se a rota estiver em `ASSIGNED` (ou `CREATED`), o status da rota em `tb_routes` é automaticamente atualizado para `IN_PROGRESS`.
- **Automação de Conclusão da Rota (`COMPLETED`):** Ao atingir `DELIVERED` (ou `CANCELLED`), se todos os outros pedidos da rota estiverem finalizados (`DELIVERED` ou `CANCELLED`), a rota correspondente é automaticamente alterada para `COMPLETED` e seu `finalizedAt` é preenchido.
- Se o status já for `DELIVERED` ou `CANCELLED` ➔ Retorna erro 400.

**Respostas:**
- `204 No Content`: Status avançado com sucesso (sem corpo).
- `400 Bad Request`: Transição inválida, entrega já finalizada ou rota sem motorista atribuído.
- `403 Forbidden`: O usuário logado não possui permissão para alterar esta entrega.
- `404 Not Found`: Entrega não encontrada.

---

#### 4.2 Cancelar Entrega
Altera o status da entrega para `CANCELLED`. Pode ser executado a qualquer momento enquanto a entrega não estiver concluída.

- **Método:** `PATCH`
- **Caminho:** `/api/deliveries/{id}/cancel`
- **Autenticação:** Requer Role `DRIVER`, `ADMIN` ou `BASIC`.
- **Regra de Segurança:** O usuário requisitante deve ser `ADMIN`, o criador da rota ou o motorista vinculado à rota da entrega.
- **Independência de Motorista:** O cancelamento pode ser efetuado mesmo que a rota não possua motorista atribuído.
- **Automação:** Ao cancelar, se todas as demais entregas da rota estiverem finalizadas (`DELIVERED` ou `CANCELLED`), a rota correspondente é automaticamente concluída (`COMPLETED`) e seu `finalizedAt` é preenchido.

**Respostas:**
- `204 No Content`: Entrega cancelada com sucesso.
- `400 Bad Request`: Não é permitido cancelar uma entrega que já foi finalizada (`DELIVERED`) ou já cancelada (`CANCELLED`).
- `403 Forbidden`: Sem permissão para alterar esta entrega.
- `404 Not Found`: Entrega não encontrada.

---

#### 4.3 Listar Entregas por Rota (Get Deliveries by Route ID)
Retorna todas as paradas/entregas vinculadas à rota informada por parâmetro de caminho.

- **Método:** `GET`
- **Caminho:** `/api/deliveries/route/{routeId}`
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`

**Parâmetros de Caminho (Path Parameters):**
| Parâmetro | Tipo | Descrição |
| :--- | :--- | :--- |
| `routeId` | Long | ID numérico da rota. |

**Respostas:**
- `200 OK`: Lista de objetos `DeliveryDto` ordenados por `order`.
- `404 Not Found`: Rota não encontrada.

---

### 5. Avaliações / Ratings (`/api/ratings`)

#### 5.1 Criar Avaliação
Permite registrar uma avaliação de serviço vinculada a uma entrega. O avaliador (`rater`) é identificado automaticamente pelo token JWT (`sub`).

- **Método:** `POST`
- **Caminho:** `/api/ratings`
- **Autenticação:** Requer Role `BASIC` ou `ADMIN`.
- **Headers:** `Authorization: Bearer <token>`, `Content-Type: application/json`

**Corpo da Requisição (Request Body):**
```json
{
  "deliveryId": 2,
  "ratedUserId": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0",
  "ratingValue": 5,
  "comment": "Entrega pontual e mercadoria em perfeito estado!"
}
```

**Parâmetros do Body:**
| Campo | Tipo | Obrigatório | Descrição |
| :--- | :--- | :--- | :--- |
| `deliveryId` | Long | Sim | ID da entrega avaliada. |
| `ratedUserId` | UUID | Sim | ID do usuário sendo avaliado (ex: o motorista). |
| `ratingValue` | Integer | Sim | Nota numérica de **1 a 5** (obrigatório respeitar o intervalo). |
| `comment` | String | Não | Comentário descritivo ou feedback textual. |

**Exemplo de Resposta (`201 Created`):**
```json
{
  "id": 1,
  "delivery": {
    "id": 2,
    "order": 2,
    "status": "DELIVERED",
    "address": "Rua Augusta, 500"
  },
  "ratingValue": 5,
  "comment": "Entrega pontual e mercadoria em perfeito estado!",
  "createdAt": "2026-10-15T12:00:00"
}
```

**Respostas de Erro:**
- `400 Bad Request`: Nota fora do limite (`A nota deve ser entre 1 e 5`).
- `404 Not Found`: Avaliador, usuário avaliado ou entrega não encontrados.

---

#### 5.2 Buscar Avaliação por ID
Recupera uma avaliação específica pelo seu identificador.

- **Método:** `GET`
- **Caminho:** `/api/ratings/{id}`
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`

**Respostas:**
- `200 OK`: Objeto `Rating`.
- `404 Not Found`: Avaliação não encontrada.

---

#### 5.3 Listar Avaliações por Entrega
Retorna todas as avaliações cadastradas para uma determinada entrega.

- **Método:** `GET`
- **Caminho:** `/api/ratings/delivery/{deliveryId}`
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`

**Respostas:**
- `200 OK`: Lista de avaliações (`List<Rating>`).
- `404 Not Found`: Entrega informada não existe.

---

#### 5.4 Listar Avaliações por Usuário Avaliado
Retorna todas as avaliações recebidas por um usuário específico (`rated`).

- **Método:** `GET`
- **Caminho:** `/api/ratings/user/{userId}`
- **Autenticação:** Requer Autenticação Bearer
- **Headers:** `Authorization: Bearer <token>`

**Respostas:**
- `200 OK`: Lista de avaliações recebidas (`List<Rating>`).

---

#### 5.5 Excluir Avaliação
Exclui permanentemente uma avaliação do banco de dados.

- **Método:** `DELETE`
- **Caminho:** `/api/ratings/{id}`
- **Autenticação:** Requer Role `ADMIN` (`SCOPE_ADMIN`)
- **Headers:** `Authorization: Bearer <token>`

**Respostas:**
- `204 No Content`: Avaliação removida com sucesso.
- `403 Forbidden`: Usuário não é administrador.
- `404 Not Found`: Avaliação inexistente.

---

## 🗺️ Integração com OpenRouteService (ORS)

A API integra-se diretamente ao **OpenRouteService** para prover inteligência geográfica:

1. **Matriz de Distâncias (`/v2/matrix/driving-car`):**
   - Coleta a matriz completa de distância em quilômetros entre todas as coordenadas submetidas em `/api/route/optimize`.
2. **Ponderação por Superfície da Via (Surface Factor):**
   - Quando `orbi.route.surface-weight-enabled=true`, a API calcula o custo real de cada segmento considerando penalidades de atrito ou condições da pista extraídas do ORS:
     - Asfalto liso / pavimentado: Fator base `1.00` a `1.02`.
     - Cascalho / compactado: Fatores de `1.10` a `1.20`.
     - Terra / lama / areia: Fatores de penalidade de `1.25` a `1.30`.
   - Essa ponderação garante que rotas em estradas precárias recebam custo superior, priorizando vias seguras e rápidas.
3. **Direções e Geometria GeoJSON (`/v2/directions/driving-car/geojson`):**
   - Retorna as coordenadas `[longitude, latitude]` prontas para serem renderizadas em componentes de mapa, acompanhadas dos passos de navegação e manobra (`steps`).

---

## 📦 Modelos de Dados (DTOs e Entidades)

### DTOs Principais

#### `RouteOptimizeRequestDto`
Payload enviado para calcular e persistir a rota otimizada.
```json
{
  "driverId": "UUID (Opcional/Nulo, motorista atribuído à rota)",
  "locations": [
    {
      "address": "String (Obrigatório)",
      "latitude": -23.550520,
      "longitude": -46.633308
    }
  ]
}
```

#### `RouteAssignDriverDto`
Payload para atribuir motorista à rota.
```json
{
  "driverId": "UUID (Obrigatório)"
}
```

#### `LocationDto`
Representa um ponto geográfico na rota.
```json
{
  "address": "String (Obrigatório)",
  "latitude": -23.550520,
  "longitude": -46.633308
}
```

#### `RouteResponseDto`
Payload detalhado retornado na consulta de rotas.
```json
{
  "id": 1,
  "driverId": { /* Objeto Driver */ },
  "createdBy": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0",
  "driverName": "Carlos Silva",
  "status": "ASSIGNED",
  "totalDistance": 18.5,
  "totalCost": 45.20,
  "totalStops": 4,
  "createdAt": "2026-10-15T11:15:00",
  "assignedAt": "2026-10-15T11:15:00",
  "finalizedAt": null
}
```

#### `DeliveryDto`
Detalhes de uma parada / entrega de uma rota.
```json
{
  "id": 10,
  "order": 1,
  "status": "TO_BE_DELIVERED",
  "address": "Rua das Flores, 123",
  "latitude": -22.90410000,
  "longitude": -47.05990000,
  "recipientEmail": "cliente@email.com",
  "pickedUpAt": null,
  "deliveredAt": null
}
```

#### `CreateRatingDto`
Dados para registrar uma nova avaliação.
```json
{
  "deliveryId": 10,
  "ratedUserId": "UUID",
  "ratingValue": 5,
  "comment": "Texto explicativo da nota"
}
```

---

## ⚠️ Códigos de Status HTTP e Tratamento de Erros

A API responde com códigos HTTP padrão:

| Código | Significado | Quando ocorre |
| :--- | :--- | :--- |
| `200 OK` | Sucesso | Requisições `GET`, `PUT` ou endpoints de processamento com retorno. |
| `201 Created` | Criado | Recurso criado com sucesso (ex: `POST /api/ratings`). |
| `204 No Content` | Sem Conteúdo | Operações de exclusão (`DELETE`) ou atualizações de status (`PATCH`) bem-sucedidas. |
| `400 Bad Request` | Requisição Inválida | Parâmetros inválidos, notas fora de 1-5, tentativa de cancelar entrega finalizada ou matriz geográfica inválida. |
| `401 Unauthorized` | Não Autenticado | Token JWT ausente, expirado ou credenciais de login inválidas. |
| `403 Forbidden` | Não Autorizado | Usuário autenticado não possui o papel exigido (`SCOPE_ADMIN`) ou tentou alterar entrega que pertence a outro motorista. |
| `404 Not Found` | Não Encontrado | Rota, entrega, usuário ou perfil de motorista inexistente no banco. |
| `422 Unprocessable` | Entidade Improcessável | Tentativa de registrar um usuário com e-mail já existente. |
| `500 Internal Error` | Erro do Servidor | Erro de comunicação com OpenRouteService ou chave de API não configurada. |

---

## 💻 Exemplos Práticos de Consumo

### 1. Chamada via cURL

#### Login e Captura de Token:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"carlos.silva@exemplo.com","password":"SenhaSegura123!"}'
```

#### Otimizar Rota (Autenticado):
```bash
curl -X POST "http://localhost:8080/api/route/optimize?startIndex=0" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <SEU_TOKEN_AQUI>" \
  -d '{
    "driverId": "a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0",
    "locations": [
      {
        "address": "Ponto de Partida",
        "latitude": -23.55052,
        "longitude": -46.633308
      },
      {
        "address": "Entrega 1",
        "latitude": -23.5532,
        "longitude": -46.6528
      }
    ]
  }'
```

#### Avançar Status da Entrega:
```bash
curl -X PATCH http://localhost:8080/api/deliveries/1/next-status \
  -H "Authorization: Bearer <SEU_TOKEN_AQUI>"
```

#### Avançar Status da Rota:
```bash
curl -X PATCH http://localhost:8080/api/route/1/next-status \
  -H "Authorization: Bearer <SEU_TOKEN_AQUI>"
```

---

### 2. Chamada via JavaScript (`fetch`)

```javascript
const API_BASE = 'http://localhost:8080/api';

// 1. Efetuar Login
async function login(email, password) {
  const response = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });

  if (!response.ok) {
    throw new Error('Falha no login');
  }

  const data = await response.json();
  localStorage.setItem('orbi_token', data.accessToken);
  return data.accessToken;
}

// 2. Consultar Direções da Rota
async function fetchRouteDirections(routeId) {
  const token = localStorage.getItem('orbi_token');
  const response = await fetch(`${API_BASE}/route/${routeId}/directions`, {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  if (!response.ok) {
    throw new Error('Erro ao buscar direções da rota');
  }

  const directions = await response.json();
  console.log('Traçado GeoJSON:', directions.geometry);
  return directions;
}
```

---

### 3. Chamada via Axios (Node.js / React / Vue)

```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api'
});

// Interceptor para injetar o Bearer Token automaticamente
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('orbi_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Exemplo: Criar Avaliação
export async function createRating(deliveryId: number, ratedUserId: string, ratingValue: number, comment: string) {
  const response = await api.post('/ratings', {
    deliveryId,
    ratedUserId,
    ratingValue,
    comment
  });
  return response.data;
}

// Exemplo: Avançar Entrega
export async function advanceDelivery(deliveryId: number) {
  await api.patch(`/deliveries/${deliveryId}/next-status`);
}
```

---

## ⚙️ Variáveis de Ambiente Necessárias para a Aplicação

Para executar a API Orbi localmente ou em contêiner Docker:

| Variável | Descrição | Exemplo |
| :--- | :--- | :--- |
| `DB_URL` | String de conexão JDBC com o banco MySQL | `jdbc:mysql://localhost:3306/orbi_db` |
| `DB_USER` | Usuário do banco de dados | `root` |
| `DB_PASS` | Senha do banco de dados | `secret123` |
| `ADMIN_PASS` | Senha padrão do usuário administrador gerado no boot (`admin`) | `AdminPassword123` |
| `JWT_PUBLIC_KEY_PATH` | Caminho para a chave pública RSA (opcional, padrão: `classpath:app.pub`) | `classpath:app.pub` |
| `JWT_PRIVATE_KEY_PATH`| Caminho para a chave privada RSA (opcional, padrão: `classpath:app.key`) | `classpath:app.key` |
| `ORS_API_KEY` | Chave de API do OpenRouteService para cálculos de matriz e rota | `5b3ce3597851110001cf6248...` |
| `orbi.route.surface-weight-enabled` | Ativa penalização de rotas com base na superfície da pista | `true` |
