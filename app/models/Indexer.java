package models;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Indexer {
    private final ConsumerConnector consumer;
    private final String topic;
    private ExecutorService executor;

    public Indexer(String mZookeeper, String mGroupId, String mTopic) {
        consumer = kafka.consumer.Consumer.createJavaConsumerConnector(
               createConsumerConfig(mZookeeper, mGroupId));
        this.topic = mTopic;
    }

    public void shutdown() {
        if (consumer != null) consumer.shutdown();
        if (executor != null) executor.shutdown();
        try {
            assert executor != null;
            if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                System.out.println("Timed out waiting for consumer threads to shutdown, exiting uncleanly");
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted during shutdown, exiting uncleanly");
        }
    }

    public void run(int numThreads) {
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(topic, numThreads);
        Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
        List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(topic);

        //we launch all the threads
        executor = Executors.newFixedThreadPool(numThreads);

        //we create an object to consume the messages
        int threadNumber = 0;
        for (final KafkaStream stream : streams) {
            executor.submit(new FeedConsumer(stream, threadNumber));
            threadNumber++;
        }
    }

    private static ConsumerConfig createConsumerConfig(String zookeeper, String groupId) {
        Properties props = new Properties();
        props.put("zookeeper.connect", zookeeper);
        props.put("group.id", groupId);
        props.put("auto.offset.reset", "smallest");
        props.put("zookeeper.session.timeout.ms", "3000");
        props.put("zookeeper.sync.time.ms", "1000");
        props.put("auto.commit.interval.ms", "1000");

        return new ConsumerConfig(props);
    }
    public static void index() {
        String[] args = {
                "172.31.4.190:2181,",
                "feeds",
                "scholarship-feed",
        };

        String zookeeper = args[0];
        String groupId = args[1];
        String topic = args[2];
        int threads = 2;

        Indexer indexer = new Indexer(zookeeper, groupId, topic);
        indexer.run(threads);

        try {
            Thread.sleep(10000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        indexer.shutdown();
    }
}
