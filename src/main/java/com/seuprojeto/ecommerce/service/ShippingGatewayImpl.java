package com.seuprojeto.ecommerce.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuprojeto.ecommerce.exception.ShippingGatewayException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ShippingGatewayImpl implements ShippingGateway {

    private final RestClient.Builder restClientBuilder;

    @Value("${shipping.melhor-envio.base-url}")
    private String baseUrl;

    @Value("${shipping.melhor-envio.token}")
    private String token;

    @Value("${shipping.melhor-envio.from-cep}")
    private String fromCep;

    @Value("${shipping.melhor-envio.user-agent}")
    private String userAgent;

    private RestClient restClient;

    @PostConstruct
    void init() {
        restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    @Override
    public List<ShippingQuoteResult> calculateShipping(String destinationCep, List<ShippingItem> items) {
        CalculateRequest requestBody = new CalculateRequest(
                new Address(onlyDigits(fromCep)),
                new Address(onlyDigits(destinationCep)),
                items.stream()
                        .map(item -> new ProductLine(
                                item.productId(),
                                item.widthCm(),
                                item.heightCm(),
                                item.lengthCm(),
                                item.weightKg(),
                                item.unitPrice(),
                                item.quantity()))
                        .toList());

        List<QuoteItemDto> response;
        try {
            response = restClient.post()
                    .uri("/api/v2/me/shipment/calculate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<QuoteItemDto>>() {});
        } catch (RestClientResponseException ex) {
            throw new ShippingGatewayException(
                    "Falha ao consultar frete no Melhor Envio: HTTP " + ex.getStatusCode().value()
                            + " - " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new ShippingGatewayException("Falha de comunicação com o Melhor Envio: " + ex.getMessage(), ex);
        }

        if (response == null) {
            return List.of();
        }

        return response.stream()
                .filter(item -> item.error() == null)
                .map(item -> new ShippingQuoteResult(
                        item.company() != null ? item.company().name() : null,
                        item.name(),
                        new BigDecimal(item.customPrice() != null ? item.customPrice() : item.price()),
                        item.deliveryTime()))
                .toList();
    }

    private String onlyDigits(String cep) {
        return cep.replaceAll("\\D", "");
    }

    private record CalculateRequest(Address from, Address to, List<ProductLine> products) {}

    private record Address(@JsonProperty("postal_code") String postalCode) {}

    private record ProductLine(String id, int width, int height, int length, BigDecimal weight,
                                @JsonProperty("insurance_value") BigDecimal insuranceValue, int quantity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QuoteItemDto(Long id, String name, String price,
                                 @JsonProperty("custom_price") String customPrice,
                                 @JsonProperty("delivery_time") Integer deliveryTime,
                                 Company company, String error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Company(Long id, String name) {}
}
