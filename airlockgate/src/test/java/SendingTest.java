import ru.kontur.airlock.Application;
import ru.kontur.airlock.dto.AirlockMessage;
import ru.kontur.airlock.dto.EventGroup;
import ru.kontur.airlock.dto.EventRecord;
import org.apache.http.HttpResponse;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.junit.Assert;
import org.junit.Test;
import org.apache.http.client.fluent.Request;
import org.rapidoid.log.Log;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SendingTest {
    @Test
    public void sendData() throws Exception {
        KafkaConsumer<byte[], byte[]> consumer = createConsumer();
        consumer.subscribe(Arrays.asList("extern-1"), new GoBackOnRebalance(consumer, 40));
        consumer.poll(0);

        AirlockMessage airlockMessage = SerializationTest.getAirlockMessage();
        Application.run();
        Request request = Request.Post("http://localhost:8888/send")
                .connectTimeout(1000)
                .socketTimeout(1000)
                //.addHeader("apikey", "8bb9d519-ae52-4c17-ad7a-d871dbd665fe")
                .bodyByteArray(airlockMessage.toByteArray());
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(401, response.getStatusLine().getStatusCode());
        response = request.addHeader("apikey", "8bb9d519-ae52-4c17-ad7a-d871dbd665fe").execute().returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Thread.sleep(5000);
        Application.shutdown();


        int recordIdx = 0;
        EventGroup eventGroup = airlockMessage.eventGroups.get(0);
        while (recordIdx < 10) {
            ConsumerRecords<byte[], byte[]> records = consumer.poll(10000);
            Log.info("Got " + records.count() + " records");
            if (records.count() == 0)
                break;
            for (ConsumerRecord<byte[], byte[]> record : records) {
                Log.info("Got " + record.value().length + " bytes, ts=" + record.timestamp());
                EventRecord sentRecord = eventGroup.eventRecords.get(recordIdx);
                byte[] data = sentRecord.data;
                Assert.assertEquals(sentRecord.timestamp, record.timestamp());
                Assert.assertArrayEquals(data, record.value());
                recordIdx++;
            }
        }
    }

    private KafkaConsumer<byte[], byte[]> createConsumer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092"); //icat-test01
        props.put("group.id", "groupid");
        props.put("auto.offset.reset", "latest");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", 1000);
        props.put("session.timeout.ms", 60000);
        props.put("max.poll.records", 100 * 1000);
        props.put("max.partition.fetch.bytes", 10485760);
        props.put("fetch.min.bytes", 1);
        props.put("fetch.max.bytes", 52428800);
        props.put("fetch.max.wait.ms", 500);
        props.put("key.deserializer","org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put("value.deserializer","org.apache.kafka.common.serialization.ByteArrayDeserializer");
        return new KafkaConsumer<byte[],byte[]>(props);
    }

    public class GoBackOnRebalance implements ConsumerRebalanceListener {
        private final int seconds;
        private Consumer<?, ?> consumer;

        public GoBackOnRebalance(Consumer<?, ?> consumer, int seconds) {
            this.consumer = consumer;
            this.seconds = seconds;
        }

        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            Log.info("Revoke " + String.join(",", partitions.stream().map(x -> x.toString()).collect(Collectors.toList())));
        }

        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            long startTimestamp = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(seconds);
            for (TopicPartition topicPartition : partitions) {
//                Long offset = consumer.beginningOffsets(Collections.singleton(topicPartition)).get(topicPartition);
//                consumer.seek(topicPartition, offset);
                OffsetAndTimestamp offsetAndTimestamp = consumer.offsetsForTimes(Collections.singletonMap(topicPartition, startTimestamp)).get(topicPartition);
                long offset;
                if (offsetAndTimestamp != null) {
                    offset = offsetAndTimestamp.offset();
                    Log.info("Rewind consumer for " + topicPartition + " to " + offsetAndTimestamp);
                } else {
                    offset = consumer.endOffsets(Arrays.asList(topicPartition)).get(topicPartition);
                    Log.info("Rewind consumer for " + topicPartition + " to the end");
                }
                consumer.seek(topicPartition, offset);
            }
        }
    }

}