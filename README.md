# E-commerce API

API REST de um e-commerce simplificado, construída como projeto de portfólio para demonstrar boas práticas de backend com Java e Spring Boot: modelagem de dados, autenticação/autorização, controle de concorrência, testes de integração e documentação de API.

## Stack

- **Java 17** + **Spring Boot 3.3**
- **Spring Data JPA** + **PostgreSQL**
- **Spring Security** com autenticação **JWT** (access + refresh token)
- **Redis** para cache (listagem de produtos mais acessados)
- **Spring Mail** para notificação por e-mail de mudança de status do pedido — em desenvolvimento aponta para o **Mailpit** (SMTP fake com UI web), sem precisar de credenciais reais
- **Flyway** para versionamento do schema do banco
- **MapStruct** para conversão Entity ↔ DTO
- **Springdoc OpenAPI** (Swagger UI) para documentação da API
- **JUnit 5 + Mockito + Testcontainers** para testes (testes de integração sobem Postgres, Redis e Mailpit reais em container, não usam banco/cache/SMTP em memória)
- **Docker / docker-compose** para rodar tudo localmente sem instalar nada além do Docker

## Por que este projeto

O objetivo não foi só fazer um CRUD, mas mostrar decisões técnicas que aparecem em produção:

- **Controle de concorrência no estoque**: a entidade `Product` usa `@Version` (optimistic locking). Se dois pedidos tentarem comprar o último item em estoque ao mesmo tempo, um deles falha com conflito em vez de gerar overselling silencioso.
- **Preço travado no pedido**: `OrderItem` guarda `unitPriceAtPurchase` — o histórico de um pedido não muda se o preço do produto mudar depois.
- **Transação atômica no checkout**: verificar estoque, debitar, criar o pedido e limpar o carrinho acontece em uma única transação (`@Transactional`). Qualquer falha no meio do processo desfaz tudo.
- **Autorização por dono do recurso**: um usuário só vê/paga os próprios pedidos por ID (não é possível adivinhar o ID de outro pedido e ver ou pagar algo de outra pessoa via IDOR); admins veem todos.
- **Tratamento de erro centralizado**: todas as exceções viram uma resposta JSON padronizada (`status`, `error`, `message`, `timestamp`, `fieldErrors` para erros de validação) — inclusive violações de constraint do banco (ex.: nome duplicado) e erros de parsing da requisição, que não viram `500` genérico.
- **Cache com TTL curto**: a listagem de produtos mais acessados (`GET /products/most-accessed`) fica em Redis por 5 minutos — troca deliberada entre um ranking perfeitamente atualizado e não recalculá-lo a cada requisição.
- **Notificação nunca derruba a operação de negócio**: o e-mail de mudança de status é um efeito colateral — se o envio falhar (SMTP fora do ar, etc.), o erro é só logado, nunca propagado, então um pagamento aprovado ou uma atualização de status pelo admin não falha por causa de um problema no serviço de e-mail.

## Como rodar

### Opção 1 — Docker (recomendado, não precisa instalar Java/Maven/Postgres)

```bash
docker compose up --build
```

A API sobe em `http://localhost:8080`. Os e-mails "enviados" (mudança de status do pedido) podem ser vistos em `http://localhost:8025` (UI do Mailpit).

### Opção 2 — Localmente

Pré-requisitos: Java 17, Maven, um Postgres, um Redis e um Mailpit rodando (pode usar só esses três serviços do docker-compose: `docker compose up postgres redis mailpit`).

```bash
mvn spring-boot:run
```

## Documentação da API (Swagger)

Com a aplicação rodando, acesse:

```
http://localhost:8080/swagger-ui.html
```

## Fluxo básico de uso

1. `POST /auth/register` — cria uma conta (já sai com um carrinho vazio associado)
2. `POST /auth/login` — retorna `accessToken` e `refreshToken`
3. Use o `accessToken` no header `Authorization: Bearer <token>` nas próximas chamadas
4. `GET /products` — navega o catálogo (público, não precisa de token)
5. `POST /cart/items` — adiciona produtos ao carrinho
6. `POST /orders` — fecha o pedido a partir do carrinho (verifica e debita estoque)
7. `POST /orders/{orderId}/payment` — simula o pagamento, marca o pedido como pago e envia um e-mail de notificação ao cliente

Toda mudança de status de um pedido (pagamento aprovado, ou `PATCH /admin/orders/{id}/status`) dispara um e-mail para o dono do pedido — em desenvolvimento, veja em `http://localhost:8025`.

Cada `GET /products/{id}` conta como uma visualização; `GET /products/most-accessed` retorna o catálogo ordenado pelas mais vistas (paginado, com cache de 5 min).

Para ações de admin (criar produto, ver todos os pedidos, etc.), é preciso que o usuário tenha `role = ADMIN`. Por padrão todo cadastro novo é `CUSTOMER` — para testar como admin, promova um usuário direto no banco:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'seu-email@exemplo.com';
```

## Rodando os testes

```bash
mvn test
```

### Testes unitários

Cobrem a camada de `service/`, o `GlobalExceptionHandler` e o `ProductMapper` isoladamente com Mockito (sem Spring context, sem banco — rodam em segundos):

- `AuthServiceTest`, `OrderServiceTest`, `PaymentServiceTest`, `CartServiceTest`, `ProductServiceTest`
- `JwtUtilTest` — geração/validação de token, incluindo token expirado, adulterado e malformado
- `GlobalExceptionHandlerTest` — cada `@ExceptionHandler` mapeado para o status HTTP correto
- `ProductMapperTest` — mapeamento MapStruct entity → DTO
- `EmailServiceTest` — conteúdo do e-mail de mudança de status, e a garantia de que uma falha no envio nunca propaga exceção

```bash
mvn test -Dtest=AuthServiceTest,OrderServiceTest,PaymentServiceTest,CartServiceTest,ProductServiceTest,JwtUtilTest,GlobalExceptionHandlerTest,ProductMapperTest,EmailServiceTest
```

### Testes de integração

Usam Testcontainers, então é necessário ter o Docker rodando na máquina — cada classe sobe um Postgres, um Redis e um Mailpit reais (compartilhados entre as classes no mesmo processo via `AbstractIntegrationTest`), roda as migrations do Flyway e exercita a API de ponta a ponta via MockMvc com autenticação real (registra usuário, usa o JWT emitido):

- `AuthIntegrationTest` — registro, login, refresh (incluindo refresh token inválido)
- `CheckoutIntegrationTest` — carrinho → checkout, débito de estoque, preço congelado, estoque insuficiente, produto desativado, autorização por dono do pedido
- `PaymentIntegrationTest` — proteção contra IDOR, validação de status do pedido e confirmação (via API HTTP do Mailpit) de que o e-mail de notificação realmente chegou
- `CategoryIntegrationTest` — nome duplicado e categoria com produto vinculado (constraints do banco)
- `ProductIntegrationTest` — catálogo público, autorização de admin, validação

> Em algumas máquinas Windows com Docker Desktop, o Testcontainers pode não conseguir se conectar ao daemon a partir do cliente Java (uma fricção conhecida do Docker Desktop, não do código dos testes). Se isso acontecer, os testes rodam normalmente em CI (Linux) ou em outra máquina Linux/Mac.

## CI

Todo push e pull request roda a suíte completa de testes (unitários + integração com Testcontainers) via GitHub Actions — ver [.github/workflows/ci.yml](.github/workflows/ci.yml). Como o runner é Linux, não há a fricção de Docker Desktop mencionada acima.

## Estrutura do projeto

```
src/main/java/com/seuprojeto/ecommerce/
├── config/          → SecurityConfig, CacheConfig
├── controller/      → REST controllers
├── dto/             → request/response DTOs, organizados por módulo
├── entity/          → entidades JPA
├── repository/       → interfaces Spring Data JPA
├── service/         → regras de negócio
├── exception/       → exceptions customizadas + GlobalExceptionHandler
├── security/        → JwtUtil, JwtAuthFilter, UserDetailsServiceImpl
└── mapper/          → MapStruct mappers
```

## Endpoints principais

| Método | Rota | Autenticação |
|---|---|---|
| POST | `/auth/register` | Pública |
| POST | `/auth/login` | Pública |
| POST | `/auth/refresh` | Pública |
| GET | `/products` | Pública |
| GET | `/products/most-accessed` | Pública |
| GET | `/products/{id}` | Pública |
| POST/PUT/DELETE | `/products/**` | ADMIN |
| GET | `/categories` | Pública |
| GET/POST/PUT/DELETE | `/cart/**` | Usuário logado |
| POST | `/orders` | Usuário logado (checkout) |
| GET | `/orders` | Usuário logado (próprios pedidos) |
| GET | `/orders/{id}` | Dono do pedido ou ADMIN |
| GET | `/admin/orders` | ADMIN |
| PATCH | `/admin/orders/{id}/status` | ADMIN |
| POST | `/orders/{id}/payment` | Usuário logado |

## Possíveis próximos passos

Ideias para evoluir o projeto além do escopo inicial:

- Rate limiting nos endpoints de autenticação
- Envio de e-mail assíncrono (`@Async`), para não segurar a resposta HTTP no round-trip do SMTP
