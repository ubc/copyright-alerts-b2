package ca.ubc.ctlt.copyalerts.db;

import blackboard.persist.PersistenceRuntimeException;
import blackboard.persist.dao.impl.SimpleDAO;
import blackboard.persist.impl.mapping.DbObjectMap;
import blackboard.persist.impl.mapping.annotation.AnnotationMappingFactory;
import ca.ubc.ctlt.copyalerts.db.entities.Status;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;

public class StatusTable extends SimpleDAO<Status>
{
    private static final DbObjectMap STATUS_EXT_MAP = AnnotationMappingFactory.getMap(Status.class);

    private final static Logger logger = LoggerFactory.getLogger(StatusTable.class);
    private static StatusTable instance;

    private Status status;

    public static StatusTable getInstance() {
        if (instance == null) {
            instance = new StatusTable();
        }

        return instance;
    }

    private StatusTable()
    {
        super(STATUS_EXT_MAP);
        List<Status> statusList = this.loadAll();
        if (statusList.size() == 0) {
            status = new Status();
            this.persist(status);
            logger.info("No status data, created a new one");
        } else {
            this.status = statusList.get(0);
            logger.info("Status info loaded");
        }
    }

    /**
     * Save the run stats to the database
     * @throws PersistenceRuntimeException
     */
    public void saveRunStats(String status, Timestamp start, Timestamp end)
            throws PersistenceRuntimeException
    {
        this.status.setStatus(status);
        this.status.setRunstart(start);
        this.status.setRunend(end);

        this.persist(this.status);
    }

    /**
     * Load the schedule & metadata template configuration
     * @return String config
     */
    public String loadConfig()
    {
        return status.getConfig();
    }

    public void saveConfig(String config)
    {
        status.setConfig(config);
        this.persist(status);
    }

    public String getRuntime(Timestamp start, Timestamp end)
    {
        Duration duration = new Duration(end.getTime() - start.getTime()); // in milliseconds
        PeriodFormatter formatter = new PeriodFormatterBuilder()
                .appendDays()
                .appendSuffix("d ")
                .appendHours()
                .appendSuffix("h ")
                .appendMinutes()
                .appendSuffix("m ")
                .appendSeconds()
                .appendSuffix("s")
                .toFormatter();
        String formattedDuration = formatter.print(duration.toPeriod());
        if (formattedDuration.isEmpty())
        {
            return "0s";
        }
        return formattedDuration;
    }

    /**
     * Save the data necessary for resuming from an interrupted queue generation.
     * @param queueOffset How many table entries we've already processed
     * @param lastQueueFileid The id of the last file we processed.
     * @throws PersistenceRuntimeException
     */
    public void saveQueueResumeData(long queueOffset, long lastQueueFileid) throws PersistenceRuntimeException
    {
        status.setQueueOffset(queueOffset);
        status.setLastQueueFileID(lastQueueFileid);
        this.persist(status);
    }

    public long getQueueOffset()
    {
        return status.getQueueOffset();
    }

    public long getLastQueueFileid()
    {
        return status.getLastQueueFileID();
    }

    public void saveFileResumeData(long offset, long pk1) throws PersistenceRuntimeException
    {
        status.setFilesOffset(offset);
        status.setLastFilesPk1(pk1);
        this.persist(status);
    }

    public long getFilesOffset()
    {
        return status.getFilesOffset();
    }

    public long getLastFilesPk1()
    {
        return status.getLastFilesPk1();
    }

    public void saveStage(String stage) throws PersistenceRuntimeException
    {
        status.setStage(stage);
        this.persist(status);
    }

    public String getStage()
    {
        return status.getStage();
    }

    public String getStatus() {
        return status.getStatus();
    }

    public Timestamp getEnd() {
        return status.getEnd();
    }

    public Timestamp getStart() {
        return status.getStart();
    }

    public String toString() {
        return status.toString();
    }

    public void reset() {
        status.reset();
        this.persist(status);
    }
}
