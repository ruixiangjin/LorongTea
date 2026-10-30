package com.lorongtea.order;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lorong.campaign.mode", havingValue = "async")
class ConfirmedCampaignPublisher {
    private final RabbitTemplate rabbit;

    ConfirmedCampaignPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    void publish(String eventId, String claimId, String route) {
        var properties = new MessageProperties();
        properties.setMessageId(eventId);
        properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        var correlation = new CorrelationData(eventId);
        rabbit.send(CampaignBrokerConfiguration.EXCHANGE, route,
                new Message(claimId.getBytes(StandardCharsets.US_ASCII), properties), correlation);
        try {
            var confirm = correlation.getFuture().get(2, TimeUnit.SECONDS);
            if (correlation.getReturned() != null) {
                throw new AmqpException("Campaign message was returned as unroutable");
            }
            if (!confirm.isAck()) {
                throw new AmqpException("RabbitMQ rejected the campaign message");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for RabbitMQ confirmation", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AmqpException("Campaign message was not confirmed", exception);
        }
    }
}
