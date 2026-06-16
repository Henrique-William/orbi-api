# 🚀 Orbi API

A **Orbi API** é uma solução de back-end robusta desenvolvida com **Spring Boot** voltada para o ecossistema de logística, gerenciamento de rotas, entregas e controle de usuários. Este projeto foi projetado seguindo as melhores práticas de mercado, demonstrando sólida maturidade em arquitetura de software, persistência de dados e segurança da informação.

---

## 🛠 Tecnologias e Ferramentas

* **Linguagem:** Java 17+
* **Framework Principal:** Spring Boot (Web, Data JPA, Security)
* **Banco de Dados:** H2 / MySQL (configurado via propriedades da aplicação)
* **Segurança:** Spring Security (com criptografia e controle de acesso)
* **Gerenciamento de Dependências:** Maven

---

## 🏗 Arquitetura do Projeto

A aplicação adota o padrão de **Arquitetura em Camadas (Layered Architecture)**, garantindo alta coesão, baixo acoplamento e facilidade na escrita de testes automatizados:

* **Controller:** Camada responsável por expor os endpoints REST da aplicação e gerenciar as requisições HTTP (`AuthController`, `DeliveryController`, `RouteController`, `UserController`).
* **Service:** Centraliza as regras de negócio e validações do sistema (como a lógica estruturada em `RouteService`).
* **Repository:** Interfaces que estendem o Spring Data JPA para realizar a comunicação direta e segura com o banco de dados (`DeliveryRepository`, `UserRepository`, etc.).
* **DTO (Data Transfer Object):** Padrão utilizado para transacionar dados entre as camadas sem expor diretamente as entidades do banco (`DeliveryDto`, `RouteResponseDto`, etc.).
* **Entity:** Modelagem do domínio do sistema refletida em tabelas relacionais (`User`, `Route`, `Delivery`, `Vehicle`, `Package`)[.
* **Config/Security:** Centralização das políticas de segurança e configurações globais do ecossistema (`SecurityConfig`, `AdminUserConfig`).

---

## 🔑 Funcionalidades Principais

* **Autenticação e Autorização:** Fluxo completo de login, registro de usuários e controle de permissões baseado em papéis (`Roles`).
* **Gestão de Rotas:** Endpoints focados no mapeamento e rastreamento de trajetos (`RouteController`)[.
* **Controle de Entregas:** Monitoramento do ciclo de vida dos pacotes, vinculando motoristas, veículos e atualizando status em tempo real.
* **Avaliações (Rating):** Sistema integrado para que usuários avaliem os serviços de entrega.

---

## 🚀 Como Executar o Projeto

### Pré-requisitos
* JDK 17 ou superior instalado.
* Maven instalado (opcional, devido ao uso do Maven Wrapper integrado).

### Passos para Execução
1. Clone este repositório para sua máquina local:
```bash
   git clone git@github.com:Henrique-William/orbi-api.git