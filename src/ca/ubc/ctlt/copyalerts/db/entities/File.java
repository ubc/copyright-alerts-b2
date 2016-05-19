package ca.ubc.ctlt.copyalerts.db.entities;

import blackboard.data.AbstractIdentifiable;
import blackboard.persist.DataType;
import blackboard.persist.impl.mapping.annotation.Column;
import blackboard.persist.impl.mapping.annotation.Table;

@Table(File.TABLE_NAME)
public class File extends AbstractIdentifiable {
    public static final DataType DATA_TYPE = new DataType(File.class);
    public final static String TABLE_NAME = "ubc_ctlt_ca_files";

    @Column({"userid"})
    private String userId;

    @Column({"course"})
    private String course;

    @Column({"filepath"})
    private String filePath;

    @Column({"fileid"})
    private String fileId;

    public File() {
        this("", "", "", "");
    }

    public File(String userId, String course, String filePath, String fileId) {
        this.userId = userId;
        this.course = course;
        this.filePath = filePath;
        this.fileId = fileId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
}
