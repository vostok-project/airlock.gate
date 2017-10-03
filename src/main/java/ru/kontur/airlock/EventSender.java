package ru.kontur.airlock;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import ru.kontur.airlock.dto.AirlockMessage;
import ru.kontur.airlock.dto.EventGroup;
import ru.kontur.airlock.dto.EventRecord;

import java.io.IOException;
import java.util.Properties;

class EventSender {

    private final KafkaProducer<String, byte[]> kafkaProducer;

    EventSender(Properties properties) {
        properties.setProperty("key.serializer","org.apache.kafka.common.serialization.ByteArraySerializer");
        properties.setProperty("value.serializer","org.apache.kafka.common.serialization.ByteArraySerializer");
        this.kafkaProducer = new KafkaProducer<>(properties);
    }

    void send(String project, AirlockMessage message) throws IOException {
        for (EventGroup group: message.eventGroups) {
            String topic = project + "-" + group.eventType;
            //Log.info("Send " + group.eventRecords.size() + " records to " + topic);
            for (EventRecord record: group.eventRecords) {
                //Log.info("Send record, ts: " + record.timestamp);
                kafkaProducer.send(new ProducerRecord<>(topic, null, record.timestamp, null, record.data));
            }
        }
    }
}