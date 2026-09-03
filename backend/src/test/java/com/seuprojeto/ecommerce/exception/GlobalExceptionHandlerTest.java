package com.seuprojeto.ecommerce.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testa cada @ExceptionHandler diretamente: cada tipo de exceção precisa
 * virar o status HTTP e o formato de ErrorResponse corretos. Cobre em
 * especial os handlers adicionados na revisão de código — antes deles,
 * HttpMediaTypeNotSupportedException, HttpMessageNotReadableException e
 * DataIntegrityViolationException caíam todos no handler genérico como 500.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFoundVira404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new ResourceNotFoundException("não achei"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("não achei");
    }

    @Test
    void emailJaEmUsoVira409() {
        ResponseEntity<ErrorResponse> response = handler.handleEmailInUse(new EmailAlreadyInUseException("a@b.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void estoqueInsuficienteVira409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInsufficientStock(new InsufficientStockException("Produto", 1, 5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void carrinhoVazioVira400() {
        ResponseEntity<ErrorResponse> response = handler.handleEmptyCart(new EmptyCartException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void statusDePedidoInvalidoVira409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidOrderStatus(new InvalidOrderStatusException("já pago"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void assinaturaDeWebhookInvalidaVira400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidWebhookSignature(new InvalidWebhookSignatureException("assinatura inválida"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void falhaNoGatewayDePagamentoVira502ESemVazarDetalheInterno() {
        ResponseEntity<ErrorResponse> response = handler.handlePaymentGateway(
                new PaymentGatewayException("erro interno do SDK Stripe", new RuntimeException("causa")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().message())
                .isEqualTo("Não foi possível iniciar o pagamento no momento. Tente novamente mais tarde.");
    }

    @Test
    void violacaoDeIntegridadeDoBancoVira409EmVezDe500() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key value"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
    }

    @Test
    void conflitoDeVersaoOtimistaVira409() {
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Product", 1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void credenciaisInvalidasVira401ComMensagemGenerica() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadCredentials(new BadCredentialsException("senha errada no banco"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // A mensagem exposta ao cliente não deve vazar detalhes internos da exceção original.
        assertThat(response.getBody().message()).isEqualTo("E-mail ou senha inválidos");
    }

    @Test
    void acessoNegadoVira403() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(new AccessDeniedException("sem permissão"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void erroDeValidacaoVira400ComOsCamposInvalidos() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("productRequest", "price", "não pode ser negativo")));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsEntry("price", "não pode ser negativo");
    }

    @Test
    void corpoDaRequisicaoIlegivelVira400EmVezDe500() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotReadable(new HttpMessageNotReadableException("json quebrado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void contentTypeNaoSuportadoVira415EmVezDe500() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnsupportedMediaType(new HttpMediaTypeNotSupportedException("sem content-type"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void excecaoGenericaVira500ESemVazarDetalheInterno() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new RuntimeException("detalhe interno sensível"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Ocorreu um erro inesperado");
    }
}
