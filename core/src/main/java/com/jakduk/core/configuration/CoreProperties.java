package com.jakduk.core.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jang,Pyohwan on 2017. 6. 12..
 */

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "core")
public class CoreProperties {

    private Rabbitmq rabbitmq = new Rabbitmq();
    private Elasticsearch elasticsearch = new Elasticsearch();

    @Getter
    @Setter
    public class Rabbitmq {
        private String exchangeName;
        private List<RabbitmqQueue> queues = new ArrayList<>();
    }

    @Getter
    @Setter
    public class Elasticsearch {
        private Boolean enable;
        private String indexBoard;
        private String indexGallery;
        private String indexSearchWord;
        private Integer bulkActions;
        private Integer bulkConcurrentRequests;
        private Integer bulkFlushIntervalSeconds;
        private Integer bulkSizeMb;
    }

}