package com.jarvis.database;

import com.jarvis.common.ApiException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Small, bounded JDBC adapter. Call only on the blocking IO scheduler. */
@Service
public class DatabaseStore {
    private final PGSimpleDataSource source;
    private final Semaphore slots = new Semaphore(2);
    public DatabaseStore(@Value("${jarvis.db-url:}") String url,
                         @Value("${jarvis.db-user:}") String user,
                         @Value("${jarvis.db-password:}") String password) {
        if (url.isBlank() || user.isBlank() || password.isBlank()) { source = null; return; }
        source = new PGSimpleDataSource();
        source.setUrl(url); source.setUser(user); source.setPassword(password);
        source.setConnectTimeout(3); source.setSocketTimeout(10);
        source.setOptions("-c statement_timeout=5000 -c lock_timeout=3000");
    }
    public boolean configured() { return source != null; }
    @FunctionalInterface public interface Work<T> { T run(Connection c) throws SQLException; }
    public <T> T execute(Work<T> work) { return transaction(work); }
    private <T> T transaction(Work<T> work) {
        if (!configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DATABASE_NOT_CONFIGURED",
            "Set JARVIS_DB_URL, JARVIS_DB_USER and JARVIS_DB_PASSWORD in .env, then restart the backend.");
        if (!slots.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"DATABASE_BUSY","Database is busy. Retry shortly.");
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try { T result = work.run(c); c.commit(); return result; }
            catch (SQLException | RuntimeException e) { try { c.rollback(); } catch (SQLException ignored) {} throw e; }
        } catch (SQLException e) {
            String state = e.getSQLState();
            if ("P0002".equals(state)) throw new ApiException(HttpStatus.CONFLICT,"DATABASE_CONFLICT","Record missing or changed. Refresh before retrying.");
            if (state != null && (state.startsWith("22") || state.startsWith("23")))
                throw new ApiException(HttpStatus.BAD_REQUEST,"DATABASE_INVALID","Invalid record data.");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DATABASE_UNAVAILABLE","Check the database connection and install database/001_tables_and_functions.sql in the dedicated JARVIS database.");
        } finally { slots.release(); }
    }
    private List<Map<String,Object>> query(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setQueryTimeout(5);
            for (int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            try (ResultSet r = s.executeQuery()) {
                var rows = new ArrayList<Map<String,Object>>(); var meta = r.getMetaData();
                while(r.next()) {
                    var row = new LinkedHashMap<String,Object>();
                    for(int i=1;i<=meta.getColumnCount();i++) {
                        String key=meta.getColumnLabel(i);
                        if (key.equals("owner_name")) continue;
                        Object value=r.getObject(i);
                        if(value instanceof Timestamp t) value=t.toInstant().toString();
                        row.put(key,value);
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }
    public enum Kind { memories("memory"), conversations("conversation");
        final String function; Kind(String function) { this.function=function; }
    }
    public List<Map<String,Object>> list(Kind kind,int limit,int offset) {
        return transaction(c -> query(c,"select * from jarvis."+kind.function+"_read(?,?)",limit,offset));
    }
    public Map<String,Object> create(Kind kind,String title,String content) {
        return transaction(c -> kind==Kind.memories
            ? query(c,"select * from jarvis.memory_create(?,?)",title,content).getFirst()
            : query(c,"select * from jarvis.conversation_create(?)",title).getFirst());
    }
    public Map<String,Object> update(Kind kind,UUID id,int version,String title,String content) {
        return transaction(c -> kind==Kind.memories
            ? query(c,"select * from jarvis.memory_update(?,?,?,?)",id,version,title,content).getFirst()
            : query(c,"select * from jarvis.conversation_update(?,?,?)",id,version,title).getFirst());
    }
    public void delete(Kind kind,UUID id,int version) {
        transaction(c -> query(c,"select jarvis."+kind.function+"_delete(?,?)",id,version));
    }
    public List<Map<String,Object>> messages(UUID id,long after,int limit) {
        return transaction(c -> query(c,"select * from jarvis.message_read(?,?,?)",id,after,limit));
    }
    public record Turn(String role,String content) {}
    /** Save the entire supplied snapshot atomically; failures leave no partial conversation. */
    public Map<String,Object> save(String title,List<Turn> turns) {
        return transaction(c -> {
            var conversation=query(c,"select * from jarvis.conversation_create(?)",title).getFirst();
            UUID id=(UUID)conversation.get("id");
            for(var turn:turns) query(c,"select * from jarvis.message_append(?,?,?)",id,turn.role(),turn.content());
            // message_append increments the version, so return the current version for edits/deletion.
            return query(c,"select id,title,version,created_at,updated_at from jarvis.conversations where id=? and owner_name=session_user",id).getFirst();
        });
    }
    public void check() { transaction(c -> query(c,"select * from jarvis.conversation_read(1,0)")); }
    public List<Map<String,Object>> exportMemories() {
        return transaction(c -> query(c,"select id,title,content,version,created_at,updated_at from jarvis.memories where owner_name=session_user order by created_at,id"));
    }

    public void saveGoalRun(String id, String title, String objective, String executionMode, List<String> activeAgents, String status, String finalReport) {
        if (!configured()) return;
        transaction(c -> {
            Array agentsArray = c.createArrayOf("text", activeAgents != null ? activeAgents.toArray() : new Object[0]);
            query(c, "SELECT * FROM jarvis.goal_run_upsert(?, ?, ?, ?, ?, ?, ?)",
                    id, title, objective, executionMode, agentsArray, status, finalReport);
            return null;
        });
    }

    public void saveGoalStep(String goalId, int stepIndex, int stage, String assignedAgent, String agentName,
                             String title, String actionType, String inputJson, String status,
                             String output, String error, Long durationMs, List<Integer> dependencies, int parallelGroup) {
        if (!configured()) return;
        transaction(c -> {
            Array depsArray = c.createArrayOf("int4", dependencies != null ? dependencies.toArray() : new Object[0]);
            query(c, "SELECT * FROM jarvis.goal_step_upsert(?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)",
                    goalId, stepIndex, stage, assignedAgent, agentName, title, actionType,
                    inputJson != null ? inputJson : "{}", status, output, error, durationMs, depsArray, parallelGroup);
            return null;
        });
    }

    public List<Map<String, Object>> listGoalRuns(int limit, int offset) {
        if (!configured()) return List.of();
        return transaction(c -> query(c, "SELECT * FROM jarvis.goal_run_read(?, ?)", limit, offset));
    }

    public List<Map<String, Object>> listGoalSteps(String goalId) {
        if (!configured()) return List.of();
        return transaction(c -> query(c, "SELECT * FROM jarvis.goal_steps_read(?)", goalId));
    }
}
