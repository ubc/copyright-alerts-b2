package ca.ubc.ctlt.copyalerts.db.entities;

import blackboard.data.AbstractIdentifiable;
import blackboard.persist.DataType;
import blackboard.persist.impl.mapping.annotation.Column;
import blackboard.persist.impl.mapping.annotation.Table;

@Table("ubc_ctlt_ca_hosts")
public class Host extends AbstractIdentifiable {
    public static final DataType DATA_TYPE = new DataType(Host.class);

    @Column({"host"})
    private String host;

    @Column({"leader"})
    private boolean leader;

    public Host() {
        this("");
    }

    public Host(String host) {
        this(host, false);
    }

    public Host(String host, boolean leader) {
        this.host = host;
        this.leader = leader;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public boolean isLeader() {
        return leader;
    }

    public void setLeader(boolean leader) {
        this.leader = leader;
    }
}
