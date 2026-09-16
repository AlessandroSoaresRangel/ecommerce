# Plano de Implementação: Correção de Bugs e Vulnerabilidades no E-commerce

Este documento detalha o plano de ação para corrigir os 12 bugs e vulnerabilidades identificados no sistema, garantindo a integridade do estoque, segurança de autenticação JWT, validações de requisição e sincronização entre backend e frontend.

---

## User Review Required

> [!IMPORTANT]
> **Alterações no Comportamento de Tokens JWT:**
> - Os tokens agora conterão uma claim explícita `type` (`"ACCESS"` ou `"REFRESH"`).
> - Tokens antigos emitidos sem essa claim não serão aceitos como válidos após a atualização, exigindo novo login dos usuários em sessão.
>
> **Reposição Automática de Estoque:**
> - Quando um pedido for cancelado pelo administrador (`PATCH /admin/orders/{id}/status` com `status: CANCELED`) ou por expiração/falha de pagamento no Stripe (`checkout.session.expired` / `checkout.session.async_payment_failed`), o estoque de cada produto no pedido será recomposto no banco de dados.

---

## Proposed Changes

### Backend - Validação e Carrinho

#### [MODIFY] [CartController.java](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/backend/src/main/java/com/seuprojeto/ecommerce/controller/CartController.java)
- Adicionar anotação `@Validated` na classe `CartController` para que validações de parâmetros de rota/query (como `@Min(1)`) sejam executadas pelo Spring Validation.

#### [MODIFY] [CartService.java](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/backend/src/main/java/com/seuprojeto/ecommerce/service/CartService.java)
- Em `updateItemQuantity`: validar se `quantity < 1`; caso seja 0 ou menor, lançar `IllegalArgumentException` ou remover o item se for 0.
- Em `addItem`: validar se o produto está ativo e se a quantidade total solicitada não excede `product.getStockQuantity()`, evitando colocar quantidades impossíveis no carrinho.

---

### Backend - Integridade de Estoque e Ciclo de Vida do Pedido

#### [MODIFY] [PaymentService.java](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/backend/src/main/java/com/seuprojeto/ecommerce/service/PaymentService.java)
- No método `rejectPayment`:
  - Se o pagamento já foi aprovado, ignorar.
  - Atualizar o status do pagamento para `PaymentStatus.REJECTED`.
  - Atualizar o status do pedido associado para `OrderStatus.CANCELED`.
  - Devolver a quantidade de cada `OrderItem` para o estoque (`product.setStockQuantity(product.getStockQuantity() + item.getQuantity())`).
  - Notificar via e-mail a alteração de status do pedido.

#### [MODIFY] [OrderService.java](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/backend/src/main/java/com/seuprojeto/ecommerce/service/OrderService.java)
- No método `updateStatus`:
  - Validar transições de status válidas (ex.: não permitir mudar pedido de `SHIPPED` ou `CANCELED` de volta para `PENDING`).
  - Ao transitar para `OrderStatus.CANCELED` (caso o status anterior não fosse `CANCELED`), repor o estoque de todos os itens do pedido.
- No método `resolveShippingCost`: proteger contra valores nulos de dimensões (`weightKg`, `heightCm`, `widthCm`, `lengthCm`) antes do unboxing para tipos primitivos.

---

### Backend - Segurança JWT e Charset

#### [MODIFY] [JwtUtil.java](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/backend/src/main/java/com/seuprojeto/ecommerce/security/JwtUtil.java)
- Em `signingKey()`: utilizar `secret.getBytes(StandardCharsets.UTF_8)` garantindo integridade entre sistemas operacionais (Windows vs Linux).
- Em `generateToken`: adicionar claim `claim("type", "ACCESS")`.
- Em `generateRefreshToken`: adicionar claim `claim("type", "REFRESH")`.
- Criar métodos utilitários `extractTokenType(String token)` e `isTokenType(String token, String expectedType)`.

#### [MODIFY] [JwtAuthFilter.java](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/backend/src/main/java/com/seuprojeto/ecommerce/security/JwtAuthFilter.java)
- No filtro, checar se `jwtUtil.isTokenType(token, "ACCESS")` é verdadeiro antes de autenticar a requisição, impedindo que um Refresh Token funcione como credencial de acesso direto.

#### [MODIFY] [AuthService.java](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/backend/src/main/java/com/seuprojeto/ecommerce/service/AuthService.java)
- No método `refresh`: validar se o token fornecido possui `jwtUtil.isTokenType(refreshToken, "REFRESH")`, rejeitando Access Tokens fornecidos como refresh.

---

### Backend - Validação de Categorias e Concorrência

#### [MODIFY] [CategoryController.java](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/backend/src/main/java/com/seuprojeto/ecommerce/controller/CategoryController.java)
- Adicionar `@Valid` no parâmetro `@RequestBody CategoryRequest request` no endpoint de criação `POST /categories`.
- No método `delete`: verificar se existem produtos vinculados à categoria antes da exclusão e lançar exceção de conflito (409) amigável caso haja vínculos.

#### [MODIFY] [ProductRepository.java](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/backend/src/main/java/com/seuprojeto/ecommerce/repository/ProductRepository.java)
- Adicionar `clearAutomatically = true` na anotação `@Modifying` do método `incrementAccessCount`.

---

### Configuração e Frontend

#### [MODIFY] [application.yml](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/backend/src/main/resources/application.yml)
- Corrigir as URLs padrão do Stripe:
  - `success-url: ${STRIPE_SUCCESS_URL:http://localhost:5173/checkout/success?session_id={CHECKOUT_SESSION_ID}}`
  - `cancel-url: ${STRIPE_CANCEL_URL:http://localhost:5173/checkout/cancel}`

#### [MODIFY] [CheckoutSuccessPage.tsx](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/frontend/src/pages/CheckoutSuccessPage.tsx)
- Suportar recuperação resiliente quando `PENDING_ORDER_KEY` não estiver no `sessionStorage` (ex.: se o cliente abrir o redirect em nova aba/navegador), informando adequadamente o status sem travar imediatamente em erro.

#### [MODIFY] [ProductDetailPage.tsx](file:///c:/Users/aless/Downloads/ecommerce-api/ecommerce-api/frontend/src/pages/ProductDetailPage.tsx)
- Tratar `Number(product.weightKg || 0).toFixed(3)` para prevenir `TypeError` de quebra de renderização.

---

## Verification Plan

### Automated Tests
- Executar testes unitários do frontend:
  ```powershell
  cmd /c npm run build
  ```
- Validar se os testes unitários do backend e integração continuam íntegros e compatíveis com a nova claim JWT e com as validações de carrinho e transições de status.

### Manual Verification
- Testar endpoints via curl / Swagger / Postman:
  - Tentar atualizar item no carrinho com quantidade `<= 0` e confirmar retorno 400 Bad Request.
  - Tentar usar um Refresh Token como `Bearer` no `/users/me` e confirmar recusa 401/403.
  - Testar cancelamento de pedido e verificar reposição de estoque do produto.
