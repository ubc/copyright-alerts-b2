package ca.ubc.ctlt.copyalerts.db;

import blackboard.persist.impl.DeleteQuery;
import blackboard.persist.impl.mapping.DbObjectMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

class DeleteTopQuery extends DeleteQuery {
    private int num;
    private DbObjectMap _map;

    DeleteTopQuery(DbObjectMap map, int num) {
        super(map);
        this.num = num;
        this._map = map;
    }

    private String getSql(Connection conn) throws SQLException {
        StringBuilder subSql = new StringBuilder();
        switch(conn.getMetaData().getDatabaseProductName()) {
            case "PostgreSQL":
                // Postgres "DELETE FROM TABLENAME WHERE pk1 IN (SELECT pk1 FROM TABLENAME ORDER BY pk1 LIMIT num);"
                subSql.append("SELECT pk1 FROM ").append(this._map.getTableName()).append(" ");
                subSql.append("ORDER BY pk1 LIMIT ?");
                break;
            case "Oracle":
                // Oracle "DELETE FROM "+ TABLENAME +" WHERE pk1 IN (SELECT pk1 FROM (SELECT * FROM "+ TABLENAME +" ORDER BY pk1) WHERE rownum <= "+ num +")";
                subSql.append("SELECT pk1 FROM (SELECT * FROM ").append(this._map.getTableName()).append(" ORDER BY pk1) ");
                subSql.append("WHERE rownum <= ?");
                break;
            default:
                throw new RuntimeException("Unsupported database " + conn.getMetaData().getDatabaseProductName());
        }
        return "DELETE FROM " + this._map.getTableName() + " " +
                "WHERE pk1 IN (" + subSql.toString() + ")";
    }

    public Statement prepareStatement(Connection con) throws SQLException {
        PreparedStatement stmt = con.prepareStatement(this.getSql(con));
        stmt.setInt(1, this.num);
        return stmt;
    }
}
