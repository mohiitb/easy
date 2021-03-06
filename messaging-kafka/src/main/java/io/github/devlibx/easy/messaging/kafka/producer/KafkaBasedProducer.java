package io.github.devlibx.easy.messaging.kafka.producer;

import com.google.common.base.Strings;
import io.gitbub.devlibx.easy.helper.Safe;
import io.gitbub.devlibx.easy.helper.map.StringObjectMap;
import io.gitbub.devlibx.easy.helper.metrics.IMetrics;
import io.gitbub.devlibx.easy.helper.string.StringHelper;
import io.github.devlibx.easy.messaging.producer.IProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.utils.Bytes;

import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;

@Slf4j
public class KafkaBasedProducer implements IProducer {
    private final StringObjectMap config;
    private Producer<String, Object> producer;
    private final StringHelper stringHelper;
    private final IMetrics metrics;
    private final String metricsPrefix;
    private final boolean metricsEnabled;

    public KafkaBasedProducer(StringObjectMap config, StringHelper stringHelper, IMetrics metrics) {
        this.config = config;
        this.stringHelper = stringHelper;
        this.metrics = metrics;
        this.metricsPrefix = config.getString("name", UUID.randomUUID().toString());
        this.metricsEnabled = config.getBoolean("metrics.enabled", Boolean.TRUE);
    }

    @Override
    public void start() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", config.getString("brokers", "localhost:9092"));
        properties.put("retries", config.getInt("retries", 1));
        properties.put("key.serializer", config.getString("key.serializer", "org.apache.kafka.common.serialization.StringSerializer"));
        properties.put("value.serializer", config.getString("value.serializer", "org.apache.kafka.common.serialization.StringSerializer"));
        properties.put("acks", config.getString("acks", "1"));
        properties.put("partition.assignment.strategy", config.getString("partition.assignment.strategy", "org.apache.kafka.clients.consumer.RangeAssignor"));
        if (!Strings.isNullOrEmpty(config.getString("partitioner.class"))) {
            properties.put("partitioner.class", config.getString("partitioner.class"));
        }
        producer = new KafkaProducer<>(properties);
    }

    private boolean sendSyncKafkaMessage(String topic, String key, Object value) {
        long start = System.currentTimeMillis();
        Future<RecordMetadata> ret = producer.send(new ProducerRecord<>(topic, key, value));
        boolean success = false;
        try {
            ret.get();
            success = true;
        } catch (Exception e) {
            log.error("(sync) error in sending message to topic={}, key={}", topic, key);
        }
        if (success) {
            log.debug("(sync) message sent to topic={}, key={}, value={}", topic, key, value);
            if (metricsEnabled) {
                metrics.observe(metricsPrefix + "_success_time_taken", (System.currentTimeMillis() - start));
                metrics.inc(metricsPrefix + "_success");
            }
            return true;
        } else {
            log.error("(sync) failed to send message to topic={}, key={}, value={}", topic, key, value);
            if (metricsEnabled) {
                metrics.observe(metricsPrefix + "_failure_time_taken", (System.currentTimeMillis() - start));
                metrics.inc(metricsPrefix + "_failure");
            }
            return false;
        }
    }

    private boolean sendAsyncKafkaMessage(String topic, String key, Object value) {
        long start = System.currentTimeMillis();
        producer.send(new ProducerRecord<>(topic, key, value), (metadata, exception) -> {
            if (exception != null) {
                log.error("(async) failed to send message to topic={}, key={}, value={}", topic, key, value);
                if (log.isErrorEnabled()) {
                    exception.printStackTrace();
                }
                if (metricsEnabled) {
                    metrics.observe(metricsPrefix + "_failure_time_taken", (System.currentTimeMillis() - start));
                    metrics.inc(metricsPrefix + "_failure");
                }
            } else {
                log.debug("(async) message sent to topic={}, key={}, value={}", topic, key, value);
                if (metricsEnabled) {
                    metrics.observe(metricsPrefix + "_success_time_taken", (System.currentTimeMillis() - start));
                    metrics.inc(metricsPrefix + "_success");
                }
            }
        });
        return true;
    }

    @Override
    public void shutdown() {
        Safe.safe(() -> producer.close());
    }

    @Override
    public boolean send(String key, Object value) {

        // See if we need to send it it sync/async
        boolean sync = config.getBoolean("sync") != null ? config.getBoolean("sync") : true;

        // Make sure we have topic defined
        String topic = config.getString("topic");
        if (Strings.isNullOrEmpty(topic)) {
            throw new RuntimeException("topic is not specified in kafka producer config");
        }

        // Convert value to byte array
        if (value.getClass().isAssignableFrom(byte[].class)) {
            value = new Bytes((byte[]) value);
        } else if (value instanceof String) {
            value = stringHelper.stringify(value);
            if (Objects.equals("org.apache.kafka.common.serialization.BytesSerializer", config.getString("value.serializer", ""))) {
                value = ((String) value).getBytes();
                value = new Bytes((byte[]) value);
            }
        }

        // Send final message
        if (sync) {
            return sendSyncKafkaMessage(topic, key, value);
        } else {
            return sendAsyncKafkaMessage(topic, key, value);
        }
    }
}
