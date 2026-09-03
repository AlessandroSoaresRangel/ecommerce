package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.entity.Order;
import com.seuprojeto.ecommerce.entity.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    // O envio de e-mail é um efeito colateral, não uma regra de negócio: uma
    // falha aqui (SMTP fora do ar, etc.) nunca deve derrubar a operação que
    // disparou a notificação (pagamento aprovado, status atualizado pelo
    // admin) — por isso a exceção é só logada, nunca propagada.
    public void sendOrderStatusChangedEmail(Order order, OrderStatus previousStatus) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(order.getUser().getEmail());
            message.setSubject("Atualização do pedido #" + order.getId());
            message.setText("""
                    Olá, %s!

                    O status do seu pedido #%d mudou de %s para %s.

                    Valor total: R$ %s
                    """.formatted(
                    order.getUser().getName(),
                    order.getId(),
                    previousStatus,
                    order.getStatus(),
                    order.getTotalAmount()));

            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Falha ao enviar e-mail de mudança de status do pedido {}", order.getId(), ex);
        }
    }
}
