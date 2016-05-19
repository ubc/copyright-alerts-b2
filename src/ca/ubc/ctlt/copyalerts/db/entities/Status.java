package ca.ubc.ctlt.copyalerts.db.entities;

import blackboard.data.AbstractIdentifiable;
import blackboard.persist.DataType;
import blackboard.persist.impl.mapping.annotation.Column;
import blackboard.persist.impl.mapping.annotation.Table;

import java.sql.Timestamp;
import java.util.Calendar;

@Table(Status.TABLE_NAME)
public class Status extends AbstractIdentifiable {
    public static final DataType DATA_TYPE = new DataType(Status.class);
    public static final String TABLE_NAME = "ubc_ctlt_ca_status";

    // all the possible values for the "status" field
    public final static String STATUS_RUNNING = "running";
    public final static String STATUS_STAGE_QUEUE = "queue";
    public final static String STATUS_STAGE_NEWFILES = "newfiles";
    public final static String STATUS_STAGE_UPDATE = "update";
    public final static String STATUS_STOPPED = "stopped";
    public final static String STATUS_LIMIT = "limit";
    public final static String STATUS_ERROR = "error";

    // the column names for the hosts table
    public final static String STATUS_RUNNING_KEY = "status"; // running status of this host
    public final static String STATUS_STAGE_KEY = "stage"; // at which stage we're at
    public final static String STATUS_RUNTIME_KEY = "runtime"; // how long did the last run take
    public final static String STATUS_START_KEY = "runstart"; // when did the last run start
    public final static String STATUS_END_KEY = "runend"; // when did the last run end
    public final static String STATUS_CURHOST_KEY = "host"; // the host name that we use to identify this node
    public final static String STATUS_LEADHOST_KEY = "leader"; // whether this host is selected to run alerts generation

    public final static String QUEUE_OFFSET_KEY = "queue_offset"; // number of files the queue generation stage has gone through this run
    public final static String LAST_QUEUE_FILEID_KEY = "last_queue_fileid"; // what was the last file the queue generator committed
    // if 0, then queue generation is finished
    public final static String FILES_OFFSET_KEY = "files_offset";
    public final static String LAST_FILES_PK1_KEY = "last_files_pk1";

    @Column({"status"})
    private String status;

    @Column({"stage"})
    private String stage;

    @Column({"queue_offset"})
    private long queueOffset;

    @Column({"files_offset"})
    private long filesOffset;

    @Column({"last_queue_fileid"})
    private long lastQueueFileID;

    @Column({"last_files_pk1"})
    private long lastFilesPk1;

    @Column({"runstart"})
    private Calendar runstart;

    @Column({"runend"})
    private Calendar runend;

    @Column({"config"})
    private String config;

    public Status() {
        reset();
    }

    public void reset() {
        this.status = STATUS_STOPPED;
        this.stage = STATUS_STAGE_QUEUE;
        this.queueOffset = 0;
        this.filesOffset = 0;
        this.lastQueueFileID = 0;
        this.lastFilesPk1 = 0;
        this.runstart = Calendar.getInstance();
        this.runend = Calendar.getInstance();
        this.config = "";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public long getQueueOffset() {
        return queueOffset;
    }

    public void setQueueOffset(long queueOffset) {
        this.queueOffset = queueOffset;
    }

    public long getFilesOffset() {
        return filesOffset;
    }

    public void setFilesOffset(long filesOffset) {
        this.filesOffset = filesOffset;
    }

    public long getLastQueueFileID() {
        return lastQueueFileID;
    }

    public void setLastQueueFileID(long last_queue_fileid1) {
        this.lastQueueFileID = last_queue_fileid1;
    }

    public long getLastFilesPk1() {
        return lastFilesPk1;
    }

    public void setLastFilesPk1(long lastFilesPk1) {
        this.lastFilesPk1 = lastFilesPk1;
    }

    public Calendar getRunstart() {
        return runstart;
    }

    public void setRunstart(Calendar runstart) {
        this.runstart = runstart;
    }

    public void setRunstart(Timestamp runstart) {
        this.runstart =  Calendar.getInstance();
        this.runstart.setTimeInMillis(runstart.getTime());
    }

    public Timestamp getStart() {
        return this.runstart == null ? null : new Timestamp(this.runstart.getTimeInMillis());
    }

    public Calendar getRunend() {
        return runend;
    }

    public void setRunend(Calendar runend) {
        this.runend = runend;
    }

    public void setRunend(Timestamp runend) {
        this.runend =  Calendar.getInstance();
        this.runend.setTimeInMillis(runend.getTime());
    }

    public Timestamp getEnd() {
        return this.runend == null ? null : new Timestamp(this.runend.getTimeInMillis());
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    @Override
    public String toString() {
        return "Status{" +
                "status='" + status + '\'' +
                ", stage='" + stage + '\'' +
                ", queueOffset=" + queueOffset +
                ", filesOffset=" + filesOffset +
                ", lastQueueFileID=" + lastQueueFileID +
                ", lastFilesPk1=" + lastFilesPk1 +
                ", runstart=" + runstart.getTime() +
                ", runend=" + runend.getTime() +
                ", config='" + config + '\'' +
                '}';
    }
}
