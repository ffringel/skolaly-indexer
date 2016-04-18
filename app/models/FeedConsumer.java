package models;

import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.util.Date;

public class FeedConsumer implements Runnable {
    private KafkaStream mStream;
    private int mThreadNumber;

    CloudSolrClient solrClient;
    String zkHostString = "172.31.4.190:2181,172.31.4.189:2181";
    private static final String DEFAULT_COLLECTION = "skolaly";

    public FeedConsumer(KafkaStream stream, int threadNumber) {
        mThreadNumber = threadNumber;
        mStream = stream;
    }

    public void run() {
        solrClient = new CloudSolrClient(zkHostString);
        solrClient.setDefaultCollection(DEFAULT_COLLECTION);

        ConsumerIterator<byte[], byte[]> iterator = mStream.iterator();
        while (iterator.hasNext()) {
            String msg = new String(iterator.next().message());
            if (msg.contains("~")) {
                String[] parts = msg.split("~");

                String url = parts[0];
                String title = parts[1];
                String details = parts[2];
                String md5 = parts[3];
                String published_date = parts[4];
                Date indexed_date = new Date();

                SolrQuery hashQuery = new SolrQuery(md5);
                hashQuery.addFilterQuery("id:" + md5);
                long count = 0;
                try {
                    count = solrClient.query(hashQuery).getResults().getNumFound();

                } catch (SolrServerException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (count == 0) {
                    SolrInputDocument document = new SolrInputDocument();
                    document.addField("id", md5);
                    document.addField("md5", md5);
                    document.addField("title", title);
                    document.addField("url", url);
                    document.addField("description", details);
                    document.addField("indexed_date", indexed_date);
                    document.addField("published_date", published_date);

                    try {
                        UpdateResponse response = solrClient.add(document);
                        response.getStatus();
                        solrClient.commit();
                    } catch (SolrServerException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("Shutting down Thread:" + mThreadNumber);
            }
        }
    }
}