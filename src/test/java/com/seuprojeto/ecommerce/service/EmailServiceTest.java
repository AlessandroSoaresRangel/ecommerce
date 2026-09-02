package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.entity.Order;
import com.seuprojeto.ecommerce.entity.OrderStatus;
import com.seuprojeto.ecommerce.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Cobre o envio de e-mail de mudança de status do pedido e, principalmente,
 * a garantia mais importante desse serviço: uma falha no envio (SMTP fora do
 * ar, etc.) nunca pode propagar e derrubar a operação de negócio que
 * disparou a notificação (pagamento, atualização de status pelo admin).
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;

    private EmailService emailService;

    private Order order;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "from", "naoresponda@ecommerce.com");

        User user = User.builder().id(1L).name("Cliente Teste").email("cliente@teste.com").build();
        order = Order.builder().id(42L).user(user).status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("150.00")).build();
    }

    @Test
    void deveEnviarEmailComOsDadosCorretosDoPedido() {
        emailService.sendOrderStatusChangedEmail(order, OrderStatus.PENDING);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("cliente@teste.com");
        assertThat(sent.getFrom()).isEqualTo("naoresponda@ecommerce.com");
        assertThat(sent.getSubject()).contains("42");
        assertThat(sent.getText()).contains("PENDING").contains("PAID");
    }

    @Test
    void falhaNoEnvioNaoPropagaExcecao() {
        doThrow(new MailSendException("SMTP indisponível")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> emailService.sendOrderStatusChangedEmail(order, OrderStatus.PENDING))
                .doesNotThrowAnyException();
    }
}
