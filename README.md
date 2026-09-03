# E-commerce API

API REST de um e-commerce simplificado, construída como projeto de portfólio para demonstrar boas práticas de backend com Java e Spring Boot: modelagem de dados, autenticação/autorização, controle de concorrência, testes de integração e documentação de API.

## Stack

- **Java 17** + **Spring Boot 3.3**
- **Spring Data JPA** + **PostgreSQL**
- **Spring Security** com autenticação **JWT** (access + refresh token)
- **Redis** para cache (listagem de produtos mais acessados)
- **Spring Mail** para notificação por e-mail de mudança de status do pedido — em desenvolvimento aponta para o **Mailpit** (SMTP fake com UI web), sem precisar de credenciais reais
- **Stripe Checkout** (modo teste) para pagamento — sem processar cartão de verdade nem custo algum
- **Melhor Envio** (sandbox) para cotação de frete — sem custo, sem gerar etiqueta de verdade
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
7. `POST /orders/{orderId}/payment` — cria uma Stripe Checkout Session (modo teste) e devolve a URL de pagamento; o pedido só vira `PAID` quando o Stripe confirma via webhook (ver seção "Pagamentos" abaixo)

Toda mudança de status de um pedido (pagamento aprovado, ou `PATCH /admin/orders/{id}/status`) dispara um e-mail para o dono do pedido — em desenvolvimento, veja em `http://localhost:8025`.

Cada `GET /products/{id}` conta como uma visualização; `GET /products/most-accessed` retorna o catálogo ordenado pelas mais vistas (paginado, com cache de 5 min).

Para ações de admin (criar produto, ver todos os pedidos, etc.), é preciso que o usuário tenha `role = ADMIN`. Por padrão todo cadastro novo é `CUSTOMER` — para testar como admin, promova um usuário direto no banco:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'seu-email@exemplo.com';
```

## Pagamentos (Stripe, modo teste)

O pagamento usa o **Stripe Checkout** em modo teste — sem processar cartão de verdade, sem custo. O fluxo é: o backend cria uma Checkout Session e devolve a URL; você abre essa URL num navegador e paga com um cartão de teste; o Stripe confirma o pagamento chamando um webhook no backend, que então marca o pedido como `PAID` e dispara o e-mail de notificação.

**1. Chaves de teste do Stripe**

Crie uma conta no [Stripe](https://dashboard.stripe.com/register) (gratuita) e, com o modo **Test mode** ativado no Dashboard, vá em *Developers → API keys* e copie a *Secret key* (começa com `sk_test_...`). Exporte como `STRIPE_SECRET_KEY`.

**2. Webhook local com o Stripe CLI**

Como este backend roda em `localhost`, o Stripe não consegue chamá-lo diretamente — use o [Stripe CLI](https://docs.stripe.com/stripe-cli) para encaminhar os eventos:

```bash
stripe login
stripe listen --forward-to localhost:8080/webhooks/stripe
```

O comando imprime um `whsec_...` — exporte como `STRIPE_WEBHOOK_SECRET`. Deixe esse comando rodando enquanto testa.

**3. Rodando com as chaves configuradas**

```bash
export STRIPE_SECRET_KEY=sk_test_...
export STRIPE_WEBHOOK_SECRET=whsec_...
mvn spring-boot:run
```

(ou passe as mesmas variáveis para `docker compose up`)

**4. Testando o fluxo**

`POST /orders/{orderId}/payment` devolve um `checkoutUrl` — abra essa URL no navegador e pague com o cartão de teste `4242 4242 4242 4242`, qualquer validade futura e qualquer CVC. O terminal do Stripe CLI mostra o evento `checkout.session.completed` sendo encaminhado; em seguida, `GET /orders/{orderId}` já mostra `status: PAID`, e o e-mail de notificação chega no Mailpit (`http://localhost:8025`).

Sem as chaves configuradas (variáveis não exportadas), o endpoint de pagamento continua funcionando na estrutura, mas a chamada real ao Stripe falha — isso é esperado fora de um ambiente com credenciais de teste válidas.

## Frete (Melhor Envio, sandbox)

A cotação de frete usa a API do **Melhor Envio** (um agregador de transportadoras, incluindo Correios) em ambiente **sandbox** — sem custo e sem gerar etiqueta de verdade. Diferente do Stripe, os Correios não têm mais uma API pública gratuita própria (o antigo serviço "Calcular Preço e Prazo" foi desativado em 2023 e a API oficial atual exige contrato comercial), então o Melhor Envio é usado como alternativa com sandbox de verdade.

**1. Conta sandbox e token**

Crie uma conta no [sandbox do Melhor Envio](https://sandbox.melhorenvio.com.br) (cadastro simplificado, ganha um saldo virtual de R$10.000 para testes). No painel, vá em *Gerenciar → Tokens → Novo Token* e gere um token de acesso (não precisa implementar o fluxo OAuth2 completo para isso). Exporte como `MELHOR_ENVIO_TOKEN`.

**2. Rodando com o token configurado**

```bash
export MELHOR_ENVIO_TOKEN=seu_token_sandbox
mvn spring-boot:run
```

(ou passe a mesma variável para `docker compose up`)

**3. Testando o fluxo**

Depois de adicionar um produto ao carrinho (`POST /cart/items`), `POST /cart/shipping-quote` com um CEP de destino (`{"destinationCep": "20040-020"}`) devolve as opções de frete disponíveis (transportadora, serviço, preço, prazo em dias) calculadas a partir do peso/dimensões dos produtos no carrinho e do CEP de origem configurado (`MELHOR_ENVIO_FROM_CEP`).

Sem o token configurado, o endpoint continua funcionando na estrutura, mas a chamada real ao Melhor Envio falha (502) — isso é esperado fora de um ambiente com credenciais de sandbox válidas.

## Rodando os testes

```bash
mvn test
```

### Testes unitários

Cobrem a camada de `service/`, o `GlobalExceptionHandler` e o `ProductMapper` isoladamente com Mockito (sem Spring context, sem banco — rodam em segundos):

- `AuthServiceTest`, `OrderServiceTest`, `PaymentServiceTest`, `CartServiceTest`, `ProductServiceTest`, `ShippingServiceTest`
- `StripeGatewayImplTest` — verificação de assinatura e desserialização de eventos do webhook do Stripe (é tudo local/HMAC, sem chamada de rede, então é testado direto contra a implementação real, sem mock); cobre inclusive o fallback para `deserializeUnsafe()` quando a `apiVersion` do evento não bate com a da SDK
- `ShippingGatewayImplTest` — formato da requisição (CEP normalizado, snake_case) e desserialização da resposta do Melhor Envio, testado contra um `MockRestServiceServer` (sem tocar a rede real), inclusive o filtro de opções indisponíveis (campo `error`)
- `JwtUtilTest` — geração/validação de token, incluindo token expirado, adulterado e malformado
- `GlobalExceptionHandlerTest` — cada `@ExceptionHandler` mapeado para o status HTTP correto
- `ProductMapperTest` — mapeamento MapStruct entity → DTO
- `EmailServiceTest` — conteúdo do e-mail de mudança de status, e a garantia de que uma falha no envio nunca propaga exceção

```bash
mvn test -Dtest=AuthServiceTest,OrderServiceTest,PaymentServiceTest,ShippingServiceTest,StripeGatewayImplTest,ShippingGatewayImplTest,CartServiceTest,ProductServiceTest,JwtUtilTest,GlobalExceptionHandlerTest,ProductMapperTest,EmailServiceTest
```

### Testes de integração

Usam Testcontainers, então é necessário ter o Docker rodando na máquina — cada classe sobe um Postgres, um Redis e um Mailpit reais (compartilhados entre as classes no mesmo processo via `AbstractIntegrationTest`), roda as migrations do Flyway e exercita a API de ponta a ponta via MockMvc com autenticação real (registra usuário, usa o JWT emitido):

- `AuthIntegrationTest` — registro, login, refresh (incluindo refresh token inválido)
- `CheckoutIntegrationTest` — carrinho → checkout, débito de estoque, preço congelado, estoque insuficiente, produto desativado, autorização por dono do pedido
- `PaymentIntegrationTest` — proteção contra IDOR, validação de status do pedido, fluxo Checkout Session + webhook do Stripe (mockado via `StripeGateway`, sem tocar a rede real) e confirmação (via API HTTP do Mailpit) de que o e-mail de notificação realmente chegou
- `ShippingIntegrationTest` — cotação de frete do carrinho atual (`ShippingGateway` mockado, sem tocar a rede real do Melhor Envio), CEP inválido e carrinho vazio
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
| GET | `/payments/{id}` | Dono do pagamento ou ADMIN |
| POST | `/webhooks/stripe` | Pública (verificada por assinatura) |
| POST | `/cart/shipping-quote` | Usuário logado |

## Possíveis próximos passos

Ideias para evoluir o projeto além do escopo inicial:

- Rate limiting nos endpoints de autenticação
- Envio de e-mail assíncrono (`@Async`), para não segurar a resposta HTTP no round-trip do SMTP
