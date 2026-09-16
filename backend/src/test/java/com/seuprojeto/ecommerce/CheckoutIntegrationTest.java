package com.seuprojeto.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuprojeto.ecommerce.entity.Role;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ponta a ponta do fluxo de compra contra um Postgres real: carrinho,
 * checkout (débito de estoque, preço congelado, carrinho vazio, estoque
 * insuficiente, produto desativado) e a proteção contra IDOR em
 * GET /orders/{id}. Autentica via /auth/register + Bearer token de verdade
 * (não via mock de usuário) porque os controllers usam
 * @AuthenticationPrincipal com a entidade User, que só existe quando o
 * JwtAuthFilter carrega o usuário real do banco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CheckoutIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private String registerAndGetAccessToken(String email) throws Exception {
        String body = """
                {"name":"Usuário","email":"%s","password":"senha12345"}
                """.formatted(email);
        MvcResult result = mockMvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void promoteToAdmin(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);
    }

    private Long createCategory(String adminToken, String name) throws Exception {
        String body = """
                {"name":"%s","description":"desc"}
                """.formatted(name);
        MvcResult result = mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createProduct(String adminToken, Long categoryId, String name, String price, int stock) throws Exception {
        String body = """
                {"name":"%s","description":"desc","price":%s,"stockQuantity":%d,"imageUrl":null,"weightKg":0.5,"heightCm":10,"widthCm":10,"lengthCm":10,"categoryId":%d}
                """.formatted(name, price, stock, categoryId);
        MvcResult result = mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void addToCart(String token, Long productId, int quantity) throws Exception {
        String body = """
                {"productId":%d,"quantity":%d}
                """.formatted(productId, quantity);
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    @Test
    void deveFinalizarCompraDebitandoEstoqueECongelandoPreco() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-checkout1@teste.com");
        promoteToAdmin("admin-checkout1@teste.com");
        Long categoryId = createCategory(adminToken, "Categoria-Checkout-" + System.nanoTime());
        Long productId = createProduct(adminToken, categoryId, "Produto Checkout", "50.00", 10);

        String buyerToken = registerAndGetAccessToken("comprador-checkout1@teste.com");
        addToCart(buyerToken, productId, 3);

        MvcResult checkoutResult = mockMvc.perform(post("/orders").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(150.00))
                .andExpect(jsonPath("$.items[0].unitPriceAtPurchase").value(50.00))
                .andReturn();
        long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString()).get("id").asLong();

        // Estoque realmente debitado: 10 - 3 = 7.
        mockMvc.perform(get("/products/" + productId))
                .andExpect(jsonPath("$.stockQuantity").value(7));

        // Preço muda depois da compra: o pedido já feito não deve refletir isso.
        String updateBody = """
                {"name":"Produto Checkout","description":"desc","price":999.99,"stockQuantity":7,"imageUrl":null,"weightKg":0.5,"heightCm":10,"widthCm":10,"lengthCm":10,"categoryId":%d}
                """.formatted(categoryId);
        mockMvc.perform(put("/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(999.99));

        mockMvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + buyerToken))
                .andExpect(jsonPath("$.items[0].unitPriceAtPurchase").value(50.00));
    }

    @Test
    void deveRecusarCheckoutComCarrinhoVazio() throws Exception {
        String buyerToken = registerAndGetAccessToken("comprador-vazio@teste.com");

        mockMvc.perform(post("/orders").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRecusarCheckoutComEstoqueInsuficiente() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-checkout2@teste.com");
        promoteToAdmin("admin-checkout2@teste.com");
        Long categoryId = createCategory(adminToken, "Categoria-Checkout2-" + System.nanoTime());
        Long productId = createProduct(adminToken, categoryId, "Produto Escasso", "10.00", 5);

        String buyerToken = registerAndGetAccessToken("comprador-checkout2@teste.com");
        addToCart(buyerToken, productId, 5);

        // O carrinho já recusa quantidades acima do estoque, então o cenário
        // real é o estoque cair DEPOIS de o item estar no carrinho (outra
        // venda, ajuste do admin): o checkout precisa revalidar.
        String reduceStockBody = """
                {"name":"Produto Escasso","description":"desc","price":10.00,"stockQuantity":1,"imageUrl":null,"weightKg":0.5,"heightCm":10,"widthCm":10,"lengthCm":10,"categoryId":%d}
                """.formatted(categoryId);
        mockMvc.perform(put("/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(reduceStockBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void carrinhoRecusaQuantidadeAcimaDoEstoqueEQuantidadeZero() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-checkout5@teste.com");
        promoteToAdmin("admin-checkout5@teste.com");
        Long categoryId = createCategory(adminToken, "Categoria-Checkout5-" + System.nanoTime());
        Long productId = createProduct(adminToken, categoryId, "Produto Limitado", "10.00", 2);

        String buyerToken = registerAndGetAccessToken("comprador-checkout5@teste.com");
        String body = """
                {"productId":%d,"quantity":3}
                """.formatted(productId);
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());

        addToCart(buyerToken, productId, 1);
        MvcResult cartResult = mockMvc.perform(get("/cart").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andReturn();
        long itemId = objectMapper.readTree(cartResult.getResponse().getContentAsString())
                .get("items").get(0).get("id").asLong();

        mockMvc.perform(put("/cart/items/" + itemId).param("quantity", "0")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRecusarCheckoutDeProdutoDesativadoDepoisDeAdicionadoAoCarrinho() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-checkout3@teste.com");
        promoteToAdmin("admin-checkout3@teste.com");
        Long categoryId = createCategory(adminToken, "Categoria-Checkout3-" + System.nanoTime());
        Long productId = createProduct(adminToken, categoryId, "Produto Descontinuado", "10.00", 5);

        String buyerToken = registerAndGetAccessToken("comprador-checkout3@teste.com");
        addToCart(buyerToken, productId, 1);

        mockMvc.perform(delete("/products/" + productId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/orders").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void donoConsegueVerOProprioPedidoMasEstranhoRecebeNotFound() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-checkout4@teste.com");
        promoteToAdmin("admin-checkout4@teste.com");
        Long categoryId = createCategory(adminToken, "Categoria-Checkout4-" + System.nanoTime());
        Long productId = createProduct(adminToken, categoryId, "Produto Dono", "20.00", 5);

        String donoToken = registerAndGetAccessToken("dono-pedido@teste.com");
        addToCart(donoToken, productId, 1);

        MvcResult checkoutResult = mockMvc.perform(post("/orders").header("Authorization", "Bearer " + donoToken))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode order = objectMapper.readTree(checkoutResult.getResponse().getContentAsString());
        long orderId = order.get("id").asLong();

        mockMvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + donoToken))
                .andExpect(status().isOk());

        String estranhoToken = registerAndGetAccessToken("estranho-pedido@teste.com");
        mockMvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + estranhoToken))
                .andExpect(status().isNotFound());

        // Admin consegue ver qualquer pedido.
        mockMvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void naoAutenticadoNaoConsegueAcessarOCarrinho() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().isForbidden());
    }
}
