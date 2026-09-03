package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.exception.ShippingGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.hamcrest.Matchers.is;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Testa ShippingGatewayImpl contra um servidor HTTP falso (MockRestServiceServer,
 * já disponível via spring-test — nenhuma dependência nova), sem tocar a rede
 * real do Melhor Envio: verifica o formato da requisição (snake_case, CEP
 * normalizado) e a desserialização da resposta, inclusive filtrando opções
 * com o campo "error" preenchido (frete indisponível para aquele transportador).
 */
class ShippingGatewayImplTest {

    private MockRestServiceServer server;
    private ShippingGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        gateway = new ShippingGatewayImpl(builder);
        ReflectionTestUtils.setField(gateway, "baseUrl", "https://sandbox.melhorenvio.com.br");
        ReflectionTestUtils.setField(gateway, "token", "token_sandbox_teste");
        ReflectionTestUtils.setField(gateway, "fromCep", "01310-100");
        ReflectionTestUtils.setField(gateway, "userAgent", "ecommerce-api (teste@teste.com)");
        gateway.init();
    }

    private List<ShippingGateway.ShippingItem> umItem() {
        return List.of(new ShippingGateway.ShippingItem(
                "9", new BigDecimal("0.700"), 8, 15, 20, new BigDecimal("49.90"), 2));
    }

    @Test
    void enviaRequisicaoComCepsNormalizadosEHeadersCorretos() {
        server.expect(requestTo("https://sandbox.melhorenvio.com.br/api/v2/me/shipment/calculate"))
                .andExpect(header("Authorization", "Bearer token_sandbox_teste"))
                .andExpect(header("User-Agent", "ecommerce-api (teste@teste.com)"))
                .andExpect(jsonPath("$.from.postal_code", is("01310100")))
                .andExpect(jsonPath("$.to.postal_code", is("20040020")))
                .andExpect(jsonPath("$.products[0].id", is("9")))
                .andExpect(jsonPath("$.products[0].insurance_value", is(49.9)))
                .andExpect(jsonPath("$.products[0].quantity", is(2)))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gateway.calculateShipping("20040-020", umItem());

        server.verify();
    }

    @Test
    void parseiaRespostaEFiltraOpcoesComErro() {
        String responseBody = """
                [
                  {"id":1,"name":"PAC","price":"37.79","custom_price":"35.70","delivery_time":9,
                   "company":{"id":1,"name":"Correios"}},
                  {"id":2,"name":"SEDEX","price":"58.20","delivery_time":2,
                   "company":{"id":1,"name":"Correios"}},
                  {"id":3,"name":"Indisponível","error":"Pacote excede o limite deste transportador",
                   "company":{"id":2,"name":"Jadlog"}}
                ]
                """;
        server.expect(requestTo("https://sandbox.melhorenvio.com.br/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        List<ShippingGateway.ShippingQuoteResult> result = gateway.calculateShipping("20040-020", umItem());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).carrierName()).isEqualTo("Correios");
        assertThat(result.get(0).serviceName()).isEqualTo("PAC");
        // custom_price deve ter prioridade sobre price quando presente.
        assertThat(result.get(0).price()).isEqualByComparingTo("35.70");
        assertThat(result.get(0).deliveryTimeDays()).isEqualTo(9);
        assertThat(result.get(1).serviceName()).isEqualTo("SEDEX");
        // Sem custom_price: cai para price.
        assertThat(result.get(1).price()).isEqualByComparingTo("58.20");
    }

    @Test
    void erroHttpDoMelhorEnvioViraShippingGatewayException() {
        server.expect(requestTo("https://sandbox.melhorenvio.com.br/api/v2/me/shipment/calculate"))
                .andRespond(withUnauthorizedRequest().body("{\"message\":\"Unauthenticated.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.calculateShipping("20040-020", umItem()))
                .isInstanceOf(ShippingGatewayException.class);
    }
}
