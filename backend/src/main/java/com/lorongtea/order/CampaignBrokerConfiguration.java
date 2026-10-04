package com.lorongtea.order;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "lorong.campaign.async.enabled", havingValue = "true")
class CampaignBrokerConfiguration {
    static final String EXCHANGE = "lorong.campaign";
    static final String CREATE_ROUTE = "order.create";
    static final String WORK_QUEUE = "lorong.campaign.orders";
    static final String DEAD_ROUTE = "order.quarantine";
    static final String DEAD_QUEUE = "lorong.campaign.quarantine";

    @Bean
    Declarables campaignTopology() {
        var exchange = new DirectExchange(EXCHANGE);
        var work = QueueBuilder.durable(WORK_QUEUE)
                .deadLetterExchange(EXCHANGE).deadLetterRoutingKey(DEAD_ROUTE).build();
        var dead = QueueBuilder.durable(DEAD_QUEUE).build();
        return new Declarables(exchange, work, dead,
                BindingBuilder.bind(work).to(exchange).with(CREATE_ROUTE),
                BindingBuilder.bind(dead).to(exchange).with(DEAD_ROUTE));
    }
}
