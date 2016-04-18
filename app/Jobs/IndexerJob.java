package Jobs;

import models.Indexer;
import play.jobs.Every;
import play.jobs.Job;
import play.jobs.OnApplicationStart;

@OnApplicationStart
@Every("3s")
public class IndexerJob extends Job {
    public void doJob() {
        Indexer.index();
    }
}
