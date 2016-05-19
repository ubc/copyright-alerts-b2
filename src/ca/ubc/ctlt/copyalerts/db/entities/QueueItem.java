package ca.ubc.ctlt.copyalerts.db.entities;

import blackboard.data.AbstractIdentifiable;
import blackboard.persist.DataType;
import blackboard.persist.impl.mapping.annotation.Column;
import blackboard.persist.impl.mapping.annotation.Table;

@Table(QueueItem.TABLE_NAME)
public class QueueItem extends AbstractIdentifiable {
    public static final DataType DATA_TYPE = new DataType(QueueItem.class);
    public static final String TABLE_NAME = "ubc_ctlt_ca_queue";

    @Column({"filepath"})
    private String filePath;

    public QueueItem() {
        this(null);
    }

    public QueueItem(String filePath) {
        this.filePath = filePath;
    }
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String toString() {
        return filePath;
    }
}
