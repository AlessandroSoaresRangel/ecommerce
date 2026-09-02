# E-commerce API

API REST de um e-commerce simplificado, construída como projeto de portfólio para demonstrar boas práticas de backend com Java e Spring Boot: modelagem de dados, autenticação/autorização, controle de concorrência, testes de integração e documentação de API.

## Stack

- **Java 17** + **Spring Boot 3.3**
- **Spring Data JPA** + **PostgreSQL**
- **Spring Security** com autenticação **JWT** (access + refresh token)
- **Flyway** para versionamento do schema do banco
- **MapStruct** para conversão Entity ↔ DTO
- **Springdoc OpenAPI** (Swagger UI) para documentação da API
- **JUnit 5 + Mockito + Testcontainers** para testes (o teste de integração sobe um Postgres real em container, não usa banco em memória)
- **Docker / docker-compose** para rodar tudo localmente sem instalar nada além do Docker

## Por que este projeto

O objetivo não foi só fazer um CRUD, mas mostrar decisões técnicas que aparecem em produção:

- **Controle de concorrência no estoque**: a entidade `Product` usa `@Version` (optimistic locking). Se dois pedidos tentarem comprar o último item em estoque ao mesmo tempo, um deles falha com conflito em vez de gerar overselling silencioso.
- **Preço travado no pedido**: `OrderItem` guarda `unitPriceAtPurchase` — o histórico de um pedido não muda se o preço do produto mudar depois.
- **Transação atômica no checkout**: verificar estoque, debitar, criar o pedido e limpar o carrinho acontece em uma única transação (`@Transactional`). Qualquer falha no meio do processo desfaz tudo.
- **Autorização por dono do recurso**: um usuário só vê os próprios pedidos por ID (não é possível adivinhar o ID de outro pedido e ver dados de outra pessoa via IDOR); admins veem todos.
- **Tratamento de erro centralizado**: todas as exceções viram uma resposta JSON padronizada (`status`, `error`, `message`, `timestamp`, `fieldErrors` para erros de validação).

## Como rodar

### Opção 1 — Docker (recomendado, não precisa instalar Java/Maven/Postgres)

```bash
docker compose up --build
```

A API sobe em `http://localhost:8080`.

### Opção 2 — Localmente

Pré-requisitos: Java 17, Maven, um Postgres rodando (pode usar só o serviço `postgres` do docker-compose: `docker compose up postgres`).

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
7. `POST /orders/{orderId}/payment` — simula o pagamento e marca o pedido como pago

Para ações de admin (criar produto, ver todos os pedidos, etc.), é preciso que o usuário tenha `role = ADMIN`. Por padrão todo cadastro novo é `CUSTOMER` — para testar como admin, promova um usuário direto no banco:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'seu-email@exemplo.com';
```

## Rodando os testes

```bash
mvn test
```

O teste de integração (`ProductIntegrationTest`) usa Testcontainers, então é necessário ter o Docker rodando na máquina — ele sobe um Postgres real, roda as migrations do Flyway e testa os endpoints de ponta a ponta via MockMvc, incluindo cenários de autorização (usuário comum tentando criar produto → 403) e validação (preço negativo → 400).

## Estrutura do projeto

```
src/main/java/com/seuprojeto/ecommerce/
├── config/          → SecurityConfig
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

- Paginação e cache (Redis) na listagem de produtos mais acessados
- Notificação por e-mail quando o status do pedido muda
- Testes de integração cobrindo carrinho e checkout (hoje cobrem produtos)
- Rate limiting nos endpoints de autenticação
- CI/CD com GitHub Actions rodando os testes a cada push
