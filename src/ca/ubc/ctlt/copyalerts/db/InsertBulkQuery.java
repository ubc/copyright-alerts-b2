package ca.ubc.ctlt.copyalerts.db;

import blackboard.db.DbTypeDML;
import blackboard.db.DbTypeProperties;
import blackboard.persist.Id;
import blackboard.persist.PersistenceException;
import blackboard.persist.impl.AbstractInsertQuery;
import blackboard.persist.impl.mapping.DbObjectMap;
import blackboard.persist.impl.mapping.LiteralDbMapping;
import blackboard.persist.impl.mapping.Mapping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;


class InsertBulkQuery extends AbstractInsertQuery {
    private static final String TEMPLATE = "INSERT INTO %s ( %s ) VALUES ( %s )";
    protected List<Object> _objs;

    InsertBulkQuery(DbObjectMap map) {
        this(map, new ArrayList<>());
    }

    InsertBulkQuery(DbObjectMap map, List<Object> objs) {
        super(map);
        this._objs = objs;
    }

    protected List<Object> getObjectsToInsert() {
        return this._objs;
    }

    void addObject(Object obj) {
        _objs.add(obj);
    }

    protected Statement prepareStatement(Connection con, StringBuilder columns, StringBuilder values) throws SQLException, PersistenceException {
        String[] generatedKeyColumns = EMPTY_COLUMN_LIST;
        Mapping idMapping = this.getDbObjectMap().getMapping("id");
        Id id = (Id)this.getDbObjectMap().getTargetValue(this._objs.get(0), "id");
        DbTypeProperties typeProperties = this.getBbDatabase().getType().getProperties();
        boolean useManualSequence = !typeProperties.usesAutoIncrementPrimaryKeys();
        if(this.getContainer().isValidId(id)) {
            this.addColumns(columns, values, idMapping);
        } else if(useManualSequence) {
            this.addSequenceColumns(columns, values, idMapping, this.getDbObjectMap().getPrimaryKeySequenceName());
            generatedKeyColumns = idMapping.getColumns();
        }

        String sql = String.format(TEMPLATE, this.getDbObjectMap().getTableName(), columns, values);
        PreparedStatement stmt;
        if(useManualSequence && (generatedKeyColumns == null || generatedKeyColumns.length == 0)) {
            stmt = con.prepareStatement(sql);
        } else {
            DbTypeDML nStartPos = this.getBbDatabase().getType().getDML();
            if(!nStartPos.requiresGetGeneratedKeyManually()) {
                stmt = con.prepareStatement(sql, generatedKeyColumns);
            } else {
                stmt = con.prepareStatement(String.format("%s %s", sql, nStartPos.getGeneratedKeyManually()));
            }
        }

        for(Object obj: this._objs) {
            int nStartPos1 = 1;

            for (Mapping mapping : this.getDbObjectMap().getMappingList()) {
                if (!mapping.equals(idMapping) && !(mapping instanceof LiteralDbMapping) && mapping.getInsertUse() == Mapping.Use.INPUT) {
                    nStartPos1 += mapping.marshall(this.getContainer(), stmt, nStartPos1, this.getDbObjectMap().getTargetValue(obj, mapping.getName()));
                }
            }

            if(this.getContainer().isValidId(id)) {
                Object obj1 = this.getDbObjectMap().getTargetValue(obj, idMapping.getName());
                idMapping.marshall(this.getContainer(), stmt, nStartPos1, obj1);
            }

            stmt.addBatch();
        }

        return stmt;
    }

    protected void doExecute(Connection con) throws SQLException, PersistenceException {
        this.getStatement().executeBatch();
    }
}

