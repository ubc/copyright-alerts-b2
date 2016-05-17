package ca.ubc.ctlt.copyalerts.db.entities;

import blackboard.data.AbstractIdentifiable;
import blackboard.persist.DataType;
import blackboard.persist.impl.mapping.annotation.Column;
import blackboard.persist.impl.mapping.annotation.Table;

@Table("ubc_ctlt_ca_queue")
public class QueueItem extends AbstractIdentifiable {
    public static final DataType DATA_TYPE = new DataType(QueueItem.class);

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
