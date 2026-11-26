package com.datasphere.migration.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datasphere.migration.config.StorageProperties;
import com.datasphere.migration.db.ConnectionRequest;
import com.datasphere.migration.db.ConnectionRole;
import com.datasphere.migration.db.CredentialVault;
import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.mapping.model.ColumnMapping;
import com.datasphere.migration.mapping.model.MappingStatus;
import com.datasphere.migration.metadata.TableRef;
import com.datasphere.migration.project.MappingChangeRequest.Action;
import com.datasphere.migration.project.MappingChangeRequest.Change;
import com.datasphere.migration.support.AbstractWebTest;
import com.datasphere.migration.support.TestJdbcConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End-to-end tests of the discover -> suggest -> review -> approve -> preview workflow against real JDBC
 * metadata (in-memory H2 playing a SQL Server source and an Oracle target). No production or sample data.
 */
class MappingWorkflowIntegrationTest extends AbstractWebTest {

    @Autowired ProjectService projects;
    @Autowired DiscoveryService discovery;
    @Autowired MappingWorkflowService workflow;
    @Autowired MigrationGateService gate;
    @Autowired com.datasphere.migration.engine.MigrationEngine engine;
    @Autowired com.datasphere.migration.validation.ReconciliationService reconciliation;
    @Autowired CredentialVault vault;
    @Autowired StorageProperties storage;
    @Autowired ProjectRepository repository;

    private String sourceDb;
    private String targetDb;

    @BeforeEach
    void createDatabases() {
        String id = UUID.randomUUID().toString().replace("-", "");
        sourceDb = "src" + id;
        targetDb = "tgt" + id;
        TestJdbcConfig.create(DatabaseType.SQLSERVER, sourceDb,
                "CREATE SCHEMA BANK",
                "CREATE TABLE BANK.EMPLOYEE (EMP_ID INT NOT NULL PRIMARY KEY, EMP_NM VARCHAR(100) NOT NULL, "
                        + "DOB DATE, SAL DECIMAL(12,2), DEPT_CD VARCHAR(10), HIRE_DT DATETIME, EMAIL NVARCHAR(200))",
                "CREATE TABLE BANK.CUSTOMER (CUST_ID BIGINT NOT NULL PRIMARY KEY, CUST_NM VARCHAR(120) NOT NULL, "
                        + "ADDR VARCHAR(250), STS CHAR(1))");
        TestJdbcConfig.create(DatabaseType.ORACLE, targetDb,
                "CREATE SCHEMA APP",
                "CREATE TABLE APP.EMPLOYEE (EMPLOYEE_ID NUMBER(10) NOT NULL PRIMARY KEY, "
                        + "EMPLOYEE_NAME VARCHAR2(100) NOT NULL, DATE_OF_BIRTH DATE, MONTHLY_SALARY NUMBER(12,2), "
                        + "DEPARTMENT_CODE VARCHAR2(10), HIRE_DATE TIMESTAMP, EMAIL NVARCHAR2(200))",
                "CREATE TABLE APP.CUSTOMER (CUSTOMER_ID NUMBER(19) NOT NULL PRIMARY KEY, "
                        + "CUSTOMER_NAME VARCHAR2(120) NOT NULL, ADDRESS VARCHAR2(250), STATUS CHAR(1))");
    }

    // ------------------------------------------------------------------ helpers

    private ConnectionRequest request(DatabaseType type, String database) {
        return new ConnectionRequest(type, "localhost", type.defaultPort(), database, null, TestJdbcConfig.USER,
                TestJdbcConfig.PASSWORD.toCharArray(), false);
    }

    private MigrationProject connectedProject() {
        MigrationProject p = projects.create("Test migration");
        projects.saveConnection(p.getId(), ConnectionRole.SOURCE, request(DatabaseType.SQLSERVER, sourceDb));
        return projects.saveConnection(p.getId(), ConnectionRole.TARGET, request(DatabaseType.ORACLE, targetDb));
    }

    private MigrationProject projectWithTables() {
        String id = connectedProject().getId();
        return discovery.selectTables(id, new TablePairRequest(List.of(
                new TablePairRequest.Pair(new TableRef("BANK", "EMPLOYEE"), new TableRef("APP", "EMPLOYEE")),
                new TablePairRequest.Pair(new TableRef("BANK", "CUSTOMER"), new TableRef("APP", "CUSTOMER")))));
    }

    private MigrationProject projectWithSuggestions() {
        return workflow.suggest(projectWithTables().getId(), false);
    }

    private static Change change(Action action, String pairId, String source, String target) {
        return new Change(action, pairId, source, target, null, null);
    }

    private MigrationProject edit(MigrationProject p, Change... changes) {
        return workflow.applyChanges(p.getId(), new MappingChangeRequest(p.getMappingVersion(), List.of(changes)));
    }

    private ColumnMapping column(MigrationProject p, String table, String source) {
        return p.getTablePairs().stream().filter(t -> t.getSourceTable().name().equals(table)).findFirst()
                .orElseThrow().columnFor(source).orElseThrow();
    }

    private String pairId(MigrationProject p, String table) {
        return p.getTablePairs().stream().filter(t -> t.getSourceTable().name().equals(table)).findFirst()
                .orElseThrow().getId();
    }

    /** Accepts everything the engine proposes, including entries that needed review. */
    private MigrationProject acceptEverything(MigrationProject p) {
        p = edit(p, new Change(Action.ACCEPT_ALL_SUGGESTED, null, null, null, null, null));
        for (TablePairMapping pair : p.getTablePairs()) {
            for (ColumnMapping m : pair.getColumns()) {
                if (m.getStatus() == MappingStatus.NEEDS_REVIEW) {
                    p = edit(p, change(Action.ACCEPT, pair.getId(), m.getSourceColumn(), null));
                }
            }
        }
        return projects.require(p.getId());
    }

    private MigrationProject approved() {
        MigrationProject p = acceptEverything(projectWithSuggestions());
        ValidationReport report = workflow.validate(p.getId());
        assertThat(report.valid()).as(report.issues().toString()).isTrue();
        return workflow.approve(p.getId(), p.getMappingVersion(), true, "reviewer");
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApplicationException) t).getErrorCode();
    }

    private void runOnTarget(String... sql) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:" + targetDb + ";MODE=Oracle;DB_CLOSE_DELAY=-1",
                TestJdbcConfig.USER, TestJdbcConfig.PASSWORD); Statement st = c.createStatement()) {
            for (String s : sql) {
                st.execute(s);
            }
        }
    }

    // ------------------------------------------------------------------ discovery and suggestions

    @Test
    void discoveryReadsSchemasTablesColumnsAndKeys() {
        MigrationProject p = connectedProject();

        assertThat(discovery.schemas(p.getId(), ConnectionRole.SOURCE)).contains("BANK");
        assertThat(discovery.tables(p.getId(), ConnectionRole.TARGET, "APP")).extracting("name")
                .contains("EMPLOYEE", "CUSTOMER");
        var employee = discovery.table(p.getId(), ConnectionRole.SOURCE, new TableRef("BANK", "EMPLOYEE"));
        assertThat(employee.columns()).hasSize(7);
        assertThat(employee.column("EMP_ID").orElseThrow().primaryKey()).isTrue();
        assertThat(employee.column("EMP_ID").orElseThrow().nullable()).isFalse();
        assertThat(employee.column("SAL").orElseThrow().scale()).isEqualTo(2);
    }

    @Test
    void suggestionsExplainThemselvesAndNothingIsApprovedAutomatically() {
        MigrationProject p = projectWithSuggestions();

        assertThat(p.getState()).isEqualTo(ProjectState.MAPPING_REVIEW_REQUIRED);
        assertThat(p.getApproval()).isNull();
        ColumnMapping name = column(p, "EMPLOYEE", "EMP_NM");
        assertThat(name.getTargetColumn()).isEqualTo("EMPLOYEE_NAME");
        assertThat(name.getStatus()).isEqualTo(MappingStatus.SUGGESTED);
        assertThat(name.getReasons()).isNotEmpty();
        assertThat(column(p, "EMPLOYEE", "SAL").getStatus()).isEqualTo(MappingStatus.NEEDS_REVIEW);
        assertThat(column(p, "CUSTOMER", "CUST_NM").getTargetColumn()).isEqualTo("CUSTOMER_NAME");
    }

    @Test
    void tablePairsMustBeUniqueAndTargetsMustBeTables() {
        String id = connectedProject().getId();
        TablePairRequest.Pair a = new TablePairRequest.Pair(new TableRef("BANK", "EMPLOYEE"),
                new TableRef("APP", "EMPLOYEE"));
        TablePairRequest.Pair b = new TablePairRequest.Pair(new TableRef("BANK", "CUSTOMER"),
                new TableRef("APP", "EMPLOYEE"));
        assertThatThrownBy(() -> discovery.selectTables(id, new TablePairRequest(List.of(a, b))))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> discovery.selectTables(id, new TablePairRequest(List.of())))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> discovery.selectTables(id, new TablePairRequest(List.of(
                new TablePairRequest.Pair(new TableRef("BANK", "NOPE"), new TableRef("APP", "EMPLOYEE"))))))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.DB_META_001));
    }

    // ------------------------------------------------------------------ manual changes and validation

    @Test
    void manualMappingIsRecordedAndRescored() {
        MigrationProject p = projectWithSuggestions();
        String pair = pairId(p, "EMPLOYEE");

        p = edit(p, change(Action.CLEAR, pair, "SAL", null), change(Action.SET_TARGET, pair, "SAL", "MONTHLY_SALARY"));

        ColumnMapping salary = column(p, "EMPLOYEE", "SAL");
        assertThat(salary.getTargetColumn()).isEqualTo("MONTHLY_SALARY");
        assertThat(salary.getStatus()).isEqualTo(MappingStatus.MODIFIED);
        assertThat(salary.getOrigin()).isEqualTo(ColumnMapping.ORIGIN_USER);
        assertThat(salary.getReasons().get(0)).contains("manually");
        assertThat(p.getState()).isEqualTo(ProjectState.MAPPING_MODIFIED);
    }

    @Test
    void manualMappingCannotUseAColumnThatDoesNotExist() {
        MigrationProject p = projectWithSuggestions();
        String pair = pairId(p, "EMPLOYEE");
        assertThatThrownBy(() -> edit(p, change(Action.SET_TARGET, pair, "SAL", "NO_SUCH_COLUMN")))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> edit(p, change(Action.SET_TARGET, pair, "NO_SUCH_SOURCE", "MONTHLY_SALARY")))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    void unreviewedSuggestionsBlockApproval() {
        MigrationProject p = projectWithSuggestions();

        ValidationReport report = workflow.validate(p.getId());

        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).anyMatch(i -> i.code().equals("REVIEW_REQUIRED"));
        assertThatThrownBy(() -> workflow.approve(p.getId(), p.getMappingVersion(), true, "reviewer"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.MAP_VAL_002));
        assertThat(projects.require(p.getId()).getApproval()).isNull();
        assertThat(projects.require(p.getId()).getState()).isEqualTo(ProjectState.MAPPING_VALIDATION_FAILED);
    }

    @Test
    void duplicateTargetIsAnError() {
        MigrationProject p = acceptEverything(projectWithSuggestions());
        String pair = pairId(p, "EMPLOYEE");
        p = edit(p, change(Action.SET_TARGET, pair, "DEPT_CD", "EMPLOYEE_NAME"));

        ValidationReport report = workflow.validate(p.getId());

        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).anyMatch(i -> i.code().equals("DUPLICATE_TARGET")
                && "EMPLOYEE_NAME".equals(i.targetColumn()));
    }

    @Test
    void requiredTargetColumnLeftUnmappedIsAnError() {
        MigrationProject p = acceptEverything(projectWithSuggestions());
        String pair = pairId(p, "EMPLOYEE");
        p = edit(p, change(Action.CLEAR, pair, "EMP_NM", null));

        ValidationReport report = workflow.validate(p.getId());

        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).anyMatch(i -> i.code().equals("REQUIRED_TARGET_UNMAPPED")
                && "EMPLOYEE_NAME".equals(i.targetColumn()));
    }

    @Test
    void unmappedSourceAndTargetColumnsAreReportedButDoNotBlock() {
        MigrationProject p = acceptEverything(projectWithSuggestions());
        String pair = pairId(p, "EMPLOYEE");
        p = edit(p, change(Action.CLEAR, pair, "EMAIL", null));

        ValidationReport report = workflow.validate(p.getId());

        assertThat(report.valid()).as(report.issues().toString()).isTrue();
        assertThat(report.issues()).anyMatch(i -> i.code().equals("UNMAPPED_SOURCE"));
    }

    @Test
    void incompatibleTypesCannotBeAcceptedOrApproved() {
        MigrationProject p = projectWithSuggestions();
        String pair = pairId(p, "EMPLOYEE");
        p = edit(p, change(Action.CLEAR, pair, "EMP_ID", null),
                change(Action.SET_TARGET, pair, "HIRE_DT", "EMPLOYEE_ID"));

        MigrationProject current = p;
        assertThatThrownBy(() -> edit(current, change(Action.ACCEPT, pair, "HIRE_DT", null)))
                .isInstanceOf(ApplicationException.class);
        assertThat(workflow.validate(p.getId()).issues()).anyMatch(i -> i.code().equals("TYPE_INCOMPATIBLE"));
    }

    @Test
    void staleVersionIsRejected() {
        MigrationProject p = projectWithSuggestions();
        String pair = pairId(p, "EMPLOYEE");
        edit(p, change(Action.CLEAR, pair, "EMAIL", null));

        assertThatThrownBy(() -> workflow.applyChanges(p.getId(), new MappingChangeRequest(p.getMappingVersion(),
                List.of(change(Action.CLEAR, pair, "DOB", null)))))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PRJ_CONFLICT_001));
    }

    @Test
    void regeneratingSuggestionsNeverSilentlyDiscardsReviewedWork() {
        MigrationProject p = acceptEverything(projectWithSuggestions());

        assertThatThrownBy(() -> workflow.suggest(p.getId(), false))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PRJ_STATE_001));
        assertThat(workflow.suggest(p.getId(), true).getState()).isEqualTo(ProjectState.MAPPING_REVIEW_REQUIRED);
    }

    // ------------------------------------------------------------------ approval

    @Test
    void approvalRecordsWhoWhenAndWhatWasApproved() {
        MigrationProject p = approved();

        ApprovalRecord a = p.getApproval();
        assertThat(p.getState()).isEqualTo(ProjectState.MAPPING_APPROVED);
        assertThat(a.approvedBy()).isEqualTo("reviewer");
        assertThat(a.approvedAt()).isNotNull();
        assertThat(a.projectId()).isEqualTo(p.getId());
        assertThat(a.projectName()).isEqualTo("Test migration");
        assertThat(a.mappingVersion()).isEqualTo(p.getMappingVersion());
        assertThat(a.mappingHash()).isEqualTo(p.mappingHash());
        assertThat(a.metadataFingerprint()).isNotBlank();
    }

    @Test
    void approvalNeedsTheExplicitConfirmation() {
        MigrationProject p = acceptEverything(projectWithSuggestions());
        assertThat(workflow.validate(p.getId()).valid()).isTrue();

        assertThatThrownBy(() -> workflow.approve(p.getId(), p.getMappingVersion(), false, "reviewer"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CFG_VAL_001));
        assertThat(projects.require(p.getId()).getApproval()).isNull();
    }

    @Test
    void approvalForAnOldVersionIsRefused() {
        MigrationProject p = acceptEverything(projectWithSuggestions());
        int oldVersion = p.getMappingVersion();
        edit(p, change(Action.CLEAR, pairId(p, "EMPLOYEE"), "EMAIL", null));

        assertThatThrownBy(() -> workflow.approve(p.getId(), oldVersion, true, "reviewer"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PRJ_CONFLICT_001));
    }

    @Test
    void changingAnApprovedMappingWithdrawsTheApprovalAndRaisesTheVersion() {
        MigrationProject approved = approved();
        int version = approved.getMappingVersion();

        MigrationProject changed = edit(approved, change(Action.CLEAR, pairId(approved, "EMPLOYEE"), "EMAIL", null));

        assertThat(changed.getApproval()).isNull();
        assertThat(changed.getMappingVersion()).isGreaterThan(version);
        assertThat(changed.getState()).isEqualTo(ProjectState.MAPPING_MODIFIED);
        assertThat(changed.getLastValidation()).isNull();
        assertThatThrownBy(() -> gate.preview(approved.getId()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.MAP_APPR_001));
    }

    @Test
    void approvalSurvivesNothingButTheExactMappingThatWasReviewed() {
        MigrationProject approved = approved();
        // Tamper with the stored mapping behind the service's back: the hash no longer matches.
        projects.modify(approved.getId(), p -> {
            column(p, "EMPLOYEE", "EMAIL").setTargetColumn(null);
            column(p, "EMPLOYEE", "EMAIL").setStatus(MappingStatus.UNMAPPED);
            projects.save(p);
            return null;
        });

        assertThatThrownBy(() -> gate.preview(approved.getId()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.MAP_APPR_002));
        assertThat(projects.require(approved.getId()).getApproval()).isNull();
    }

    // ------------------------------------------------------------------ migration gate

    @Test
    void migrationCannotBePreviewedOrStartedWithoutApproval() {
        MigrationProject p = projectWithSuggestions();

        assertThatThrownBy(() -> gate.preview(p.getId()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.MAP_APPR_001));
        assertThatThrownBy(() -> engine.start(p.getId(),
                new com.datasphere.migration.engine.StartMigrationRequest(100, null, true)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.MAP_APPR_001));
    }

    @Test
    void migrationCannotStartBeforeThePreviewEvenWhenApproved() {
        MigrationProject p = approved();

        assertThatThrownBy(() -> engine.start(p.getId(),
                new com.datasphere.migration.engine.StartMigrationRequest(100, null, true)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PRJ_STATE_001));
    }

    @Test
    void previewIsReadOnlyAndMakesTheProjectReady() {
        MigrationProject p = approved();

        MigrationPreview preview = gate.preview(p.getId());

        assertThat(preview.pairs()).hasSize(2);
        assertThat(preview.pairs().get(0).statementPreview()).startsWith("INSERT INTO");
        assertThat(preview.pairs().get(0).sourceRowCount()).isZero();
        assertThat(preview.notes()).anyMatch(n -> n.contains("Nothing has been written"));
        assertThat(projects.require(p.getId()).getState()).isEqualTo(ProjectState.READY_FOR_MIGRATION);
        assertThatThrownBy(() -> engine.start(p.getId(),
                new com.datasphere.migration.engine.StartMigrationRequest(100, null, false)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CFG_VAL_001));
        var run = engine.start(p.getId(),
                new com.datasphere.migration.engine.StartMigrationRequest(50, null, true));
        assertThat(run.getStatus()).isEqualTo(com.datasphere.migration.engine.MigrationRunStatus.COMPLETED);
        assertThat(run.getRowsInserted()).isZero();
        assertThat(projects.require(p.getId()).getState()).isEqualTo(ProjectState.MIGRATED);
    }

    @Test
    void aStructureChangeAfterApprovalWithdrawsTheApproval() throws Exception {
        MigrationProject p = approved();
        runOnTarget("ALTER TABLE APP.EMPLOYEE ADD LOCATION VARCHAR2(50)");

        assertThatThrownBy(() -> gate.preview(p.getId()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.MAP_META_001));

        MigrationProject after = projects.require(p.getId());
        assertThat(after.getApproval()).isNull();
        assertThat(after.getState()).isEqualTo(ProjectState.MAPPING_MODIFIED);
        assertThatThrownBy(() -> gate.preview(p.getId()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.MAP_APPR_001));
    }

    @Test
    void aDroppedTableIsDetected() throws Exception {
        MigrationProject p = approved();
        runOnTarget("DROP TABLE APP.CUSTOMER");

        assertThat(discovery.detectDrift(projects.require(p.getId())).changed()).isTrue();
        assertThatThrownBy(() -> gate.preview(p.getId()))
                .satisfies(e -> assertThat(codeOf(e)).isIn(ErrorCode.MAP_META_001, ErrorCode.MAP_VAL_002));
    }

    @Test
    void driftBetweenSuggestionAndApprovalBlocksApproval() throws Exception {
        MigrationProject p = acceptEverything(projectWithSuggestions());
        assertThat(workflow.validate(p.getId()).valid()).isTrue();
        runOnTarget("ALTER TABLE APP.EMPLOYEE ADD LOCATION VARCHAR2(50)");

        assertThatThrownBy(() -> workflow.approve(p.getId(), p.getMappingVersion(), true, "reviewer"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.MAP_META_001));
        assertThat(projects.require(p.getId()).getApproval()).isNull();
    }

    @Test
    void refreshingMetadataAdoptsTheChangeAndForcesANewReview() throws Exception {
        MigrationProject p = approved();
        runOnTarget("ALTER TABLE APP.EMPLOYEE ADD LOCATION VARCHAR2(50)");

        DiscoveryService.RefreshResult result = discovery.refreshMetadata(p.getId());

        assertThat(result.changed()).isTrue();
        MigrationProject after = projects.require(p.getId());
        assertThat(after.getApproval()).isNull();
        assertThat(after.getMappingVersion()).isGreaterThan(p.getMappingVersion());
        assertThat(discovery.detectDrift(after).changed()).isFalse();
        assertThat(after.metadata(ConnectionRole.TARGET).get("APP.EMPLOYEE").column("LOCATION")).isPresent();
    }

    // ------------------------------------------------------------------ persistence and secrets

    @Test
    void projectsSurviveARestartButPasswordsDoNot() {
        MigrationProject p = approved();

        FileProjectRepository reloaded = new FileProjectRepository(new StorageProperties(storage.dataDir()));
        MigrationProject loaded = reloaded.findById(p.getId()).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("Test migration");
        assertThat(loaded.getMappingVersion()).isEqualTo(p.getMappingVersion());
        assertThat(loaded.getApproval().approvedBy()).isEqualTo("reviewer");
        assertThat(loaded.getApproval().mappingHash()).isEqualTo(p.getApproval().mappingHash());
        assertThat(loaded.getTablePairs()).hasSize(2);
        assertThat(loaded.metadataFingerprint()).isEqualTo(p.metadataFingerprint());
        assertThat(loaded.mappingHash()).isEqualTo(p.mappingHash());

        // A restarted server has an empty vault: the user must enter the passwords again.
        vault.removeProject(p.getId());
        assertThatThrownBy(() -> discovery.schemas(p.getId(), ConnectionRole.SOURCE))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.DB_CRED_001));
        assertThatThrownBy(() -> gate.preview(p.getId())).isInstanceOf(ApplicationException.class);
    }

    @Test
    void noPasswordIsEverWrittenToDisk() throws IOException {
        approved();

        try (Stream<Path> files = Files.walk(Path.of(storage.dataDir()))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file);
                assertThat(content).as(file.toString()).doesNotContain(TestJdbcConfig.PASSWORD);
                assertThat(content.toLowerCase()).as(file.toString()).doesNotContain("\"password\"");
            }
        }
    }

    @Test
    void changingTheDatabaseOfAConnectionClearsTheSelection() {
        MigrationProject p = projectWithSuggestions();
        String otherDb = "other" + UUID.randomUUID().toString().replace("-", "");
        TestJdbcConfig.create(DatabaseType.ORACLE, otherDb, "CREATE SCHEMA APP");

        MigrationProject after = projects.saveConnection(p.getId(), ConnectionRole.TARGET,
                request(DatabaseType.ORACLE, otherDb));

        assertThat(after.getTablePairs()).isEmpty();
        assertThat(after.getApproval()).isNull();
        assertThat(after.getMappingVersion()).isGreaterThan(p.getMappingVersion());
    }

    @Test
    void anyDatabaseProductCanBeSourceOrTarget() {
        String id = UUID.randomUUID().toString().replace("-", "");
        // H2's MySQL mode does not expose a JDBC catalog matching the mem database name, so MySQL
        // discovery is covered by adapter/dialect tests rather than this H2 metadata loop.
        DatabaseType[] types = {DatabaseType.POSTGRESQL, DatabaseType.ORACLE, DatabaseType.SQLSERVER};
        for (DatabaseType from : types) {
            for (DatabaseType to : types) {
                String s = "s" + from.name().toLowerCase() + to.name().toLowerCase() + id;
                String t = "t" + from.name().toLowerCase() + to.name().toLowerCase() + id;
                String sourceSchema = from.databaseIsSchema() ? s : "SALES";
                String targetSchema = to.databaseIsSchema() ? t : "CRM";
                if (from.databaseIsSchema()) {
                    TestJdbcConfig.create(from, s, "CREATE TABLE CUSTOMER (CUSTOMER_ID INT NOT NULL PRIMARY KEY, "
                            + "CUSTOMER_NAME VARCHAR(50) NOT NULL, CUST_PH VARCHAR(20))");
                } else {
                    TestJdbcConfig.create(from, s, "CREATE SCHEMA SALES", "CREATE TABLE SALES.CUSTOMER "
                            + "(CUSTOMER_ID INT NOT NULL PRIMARY KEY, CUSTOMER_NAME VARCHAR(50) NOT NULL, CUST_PH VARCHAR(20))");
                }
                if (to.databaseIsSchema()) {
                    TestJdbcConfig.create(to, t, "CREATE TABLE CUSTOMER (CUSTOMER_ID INT NOT NULL PRIMARY KEY, "
                            + "CUSTOMER_NAME VARCHAR(50) NOT NULL, CUSTOMER_PHONE VARCHAR(20))");
                } else {
                    TestJdbcConfig.create(to, t, "CREATE SCHEMA CRM", "CREATE TABLE CRM.CUSTOMER "
                            + "(CUSTOMER_ID INT NOT NULL PRIMARY KEY, CUSTOMER_NAME VARCHAR(50) NOT NULL, "
                            + "CUSTOMER_PHONE VARCHAR(20))");
                }
                MigrationProject p = projects.create(from + " to " + to);
                projects.saveConnection(p.getId(), ConnectionRole.SOURCE, request(from, s));
                projects.saveConnection(p.getId(), ConnectionRole.TARGET, request(to, t));
                discovery.selectTables(p.getId(), new TablePairRequest(List.of(new TablePairRequest.Pair(
                        new TableRef(sourceSchema, "CUSTOMER"), new TableRef(targetSchema, "CUSTOMER")))));
                MigrationProject suggested = workflow.suggest(p.getId(), false);

                assertThat(column(suggested, "CUSTOMER", "CUSTOMER_ID").getTargetColumn())
                        .as(from + " -> " + to).isEqualTo("CUSTOMER_ID");
                assertThat(column(suggested, "CUSTOMER", "CUSTOMER_NAME").getTargetColumn())
                        .as(from + " -> " + to).isEqualTo("CUSTOMER_NAME");
            }
        }
    }

    @Test
    void copiesApprovedRowsAndReconciliationIsPartialNotAGuarantee() throws Exception {
        try (Connection c = DriverManager.getConnection(TestJdbcConfig.url(DatabaseType.SQLSERVER, sourceDb),
                TestJdbcConfig.USER, TestJdbcConfig.PASSWORD); Statement st = c.createStatement()) {
            st.execute("INSERT INTO BANK.CUSTOMER (CUST_ID, CUST_NM, ADDR, STS) VALUES (1, 'Ada Lovelace', 'X', 'A')");
        }
        MigrationProject p = approved();
        gate.preview(p.getId());
        var run = engine.start(p.getId(), new com.datasphere.migration.engine.StartMigrationRequest(10, null, true));
        assertThat(run.getStatus()).isEqualTo(com.datasphere.migration.engine.MigrationRunStatus.COMPLETED);
        assertThat(run.getRowsInserted()).isGreaterThanOrEqualTo(1);

        var report = reconciliation.validate(p.getId());
        assertThat(report.getOverall()).isNotEqualTo(com.datasphere.migration.validation.CheckOutcome.PASSED);
        assertThat(report.getNotes()).anyMatch(n -> n.toLowerCase().contains("identical")
                || n.toLowerCase().contains("partial"));
    }
}
