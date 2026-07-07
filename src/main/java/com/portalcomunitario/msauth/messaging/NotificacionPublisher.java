package com.portalcomunitario.msauth.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Publica eventos de notificación en el exchange portal.events. Resiliente: si el broker falla, solo loguea. */
@Component
public class NotificacionPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificacionPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public NotificacionPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(String routingKey, NotificacionEvento evento) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, evento);
            int n = evento.destinatarios() != null ? evento.destinatarios().size() : 0;
            log.info("Evento publicado: {} · tipo={} · {} destinatario(s)", routingKey, evento.tipo(), n);
        } catch (Exception ex) {
            log.error("No se pudo publicar el evento {} (¿broker caído?): {}", routingKey, ex.getMessage());
        }
    }
}
