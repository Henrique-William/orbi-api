---
name: project-context
description: >-
  Provides architectural, technological, and domain context about the Orbi API project.
  Use this skill automatically whenever generating code, making architecture decisions, or analyzing code in the Orbi API workspace.
---
# Orbi API - Contexto do Projeto e Arquitetura

Esta documentação serve como base de conhecimento estática sobre o projeto **Orbi API**. Recorra a este documento sempre que precisar de contexto para evitar buscas desnecessárias arquivo por arquivo (salvo necessidade de lógica muito específica).

## 1. 🎯 Visão Geral do Domínio
A **Orbi API** é uma solução de back-end de gestão logística que lida com rotas, entregas, veículos e controle de usuários. O projeto adota uma arquitetura madura para atender cenários de rastreamento de trajetos, ciclo de vida de pacotes e autenticação.

## 2. 🛠 Stack Tecnológico Base
- **Linguagem**: Java 21+
- **Framework Base**: Spring Boot 3.5.4
- **Segurança**: Spring Security com OAuth2 Resource Server
- **Persistência**: Spring Data JPA (Hibernate)
- **Banco de Dados**: MySQL (Produção/Dev) / H2 (Testes)
- **Mensageria/Real-time**: Spring Boot WebSocket
- **Utilitários**: Lombok, Validation (Hibernate Validator)
- **Build Tool**: Maven

## 3. 🏗 Arquitetura (Layered Architecture)
O projeto segue o padrão clássico e coeso de camadas (Layered Architecture).
O pacote base unificado é `com.tech.orbi`.

### Estrutura e Papéis das Camadas:
* `com.tech.orbi.controller` (APIs REST)
  * Endpoints REST HTTP. Nenhuma regra de negócio reside aqui. O foco é exclusivamente em receber e retornar requisições/DTOs e lidar com status HTTP corretos.
  * *Controllers notáveis*: `AuthController`, `DeliveryController`, `RouteController`, `UserController`.
* `com.tech.orbi.service` (Regras de Negócio)
  * Centraliza toda a lógica, orquestração e fluxo de processos da aplicação. Transaciona os Repositories e possíveis APIs externas.
  * Exemplo: `RouteService`.
* `com.tech.orbi.Repository` (Persistência)
  * Interfaces estendendo os recursos do Spring Data (ex: `JpaRepository`). Isolam toda a comunicação com o banco relacional de forma segura.
  * Exemplos: `DeliveryRepository`, `UserRepository`.
* `com.tech.orbi.entity` (Modelo de Domínio)
  * Representam as tabelas e relações do banco de dados (anotadas com `@Entity`, `@Table`, etc.).
  * Exemplos: `User`, `Route`, `Delivery`, `Vehicle`.
* `com.tech.orbi.dto` (Data Transfer Objects)
  * Classes puras para tráfego seguro de dados Request/Response (incluindo o sub-pacote `com.tech.orbi.dto.ors`). Entidades do banco NUNCA devem ser expostas nas controllers.
* `com.tech.orbi.config` e `com.tech.orbi.security`
  * Configurações transversais, Beans estendidos do Spring Boot e configurações avançadas de segurança (Filtros, Cors, RBAC - Role-Based Access Control) localizados no pacote `security` e `security.util`.

## 4. 🔑 Funcionalidades e Domínios Core
- **Autenticação (Auth):** Gestão de usuários, senhas e geração/validação de Tokens (OAuth2), dividindo o controle de acessos através de múltiplos *Roles* de domínio.
- **Entregas (Deliveries):** Orquestração completa do pacote da criação a entrega final, vinculando motoristas e veículos designados.
- **Rotas (Routes):** Mapeamento e cálculo focado nos trajetos para as entregas de cada veículo/motorista.
- **Avaliações (Rating):** Sistema onde os usuários avaliam os serviços recebidos de entrega.

## 5. ⚠️ Diretrizes e Padrões da Orbi API (Para IAs Codificadoras)
- **Desacoplamento:** Sempre converta Entity <-> DTO (evite DTOs dentro da camada Repository e Entities nos retornos da Controller).
- **Lombok:** Utilize anotações limpas do Lombok (ex: `@Data`, `@Getter`, `@Setter`, `@Builder`) para reduzir boilerplate em Entities e DTOs.
- **Injeção de Dependências:** Utilize construtores preferencialmente invocados via anotação `@RequiredArgsConstructor` sobre a classe ao invés do clássico `@Autowired` nos atributos.
- **Validações:** Realize validação forte nos payloads de entrada utilizando anotações nativas (ex: `@NotNull`, `@NotBlank`, `@Email`) no DTO combinado com `@Valid` a nível de Controller.
