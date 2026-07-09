package com.portalcomunitario.msauth.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificacionPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final NotificacionPublisher publisher = new NotificacionPublisher(rabbitTemplate);

    private NotificacionEvento evento() {
        return new NotificacionEvento("TIPO", "Título", "Mensaje",
                List.of(new Destinatario("Juan", "juan@example.com", "+56912345678", true)));
    }

    @Test
    void publicar_enviaAlExchangeConLaRoutingKey() {
        publisher.publicar(RabbitConfig.RK_PASSWORD_RESET, evento());
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_PASSWORD_RESET), any(Object.class));
    }

    @Test
    void publicar_siElBrokerFalla_noPropagaLaExcepcion() {
        doThrow(new RuntimeException("broker caído"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));
        assertThatCode(() -> publisher.publicar(RabbitConfig.RK_VECINO_REGISTRADO, evento()))
                .doesNotThrowAnyException();
    }
}
