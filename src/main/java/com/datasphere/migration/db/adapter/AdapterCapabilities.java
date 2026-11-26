package com.datasphere.migration.db.adapter;

import com.datasphere.migration.db.dialect.PaginationStyle;
import java.util.List;

/**
 * What this adapter actually does, used to build the compatibility matrix. Flags describe implemented
 * behaviour, not a marketing claim that every pair is production-ready.
 */
public record AdapterCapabilities(
        boolean jdbcDiscovery,
        boolean indexDiscovery,
        boolean identifierQuoting,
        boolean createTableSql,
        boolean insertSql,
        boolean batchedReads,
        boolean identityInsert,
        boolean sequences,
        boolean upsert,
        boolean transactions,
        PaginationStyle pagination,
        List<String> limitations) {

    public static AdapterCapabilities jdbcBaseline(PaginationStyle pagination, boolean identityInsert,
                                                   boolean sequences, List<String> limitations) {
        return new AdapterCapabilities(true, true, true, true, true, true, identityInsert, sequences, false, true,
                pagination, List.copyOf(limitations));
    }
}
