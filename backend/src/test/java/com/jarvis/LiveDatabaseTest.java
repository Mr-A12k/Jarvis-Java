package com.jarvis;

import com.jarvis.database.DatabaseStore;
import com.jarvis.database.DatabaseStore.Kind;
import com.jarvis.database.DatabaseStore.Turn;
import com.jarvis.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Only run through test-database.ps1 against its disposable cluster. */
@EnabledIfEnvironmentVariable(named="JARVIS_DATABASE_TEST", matches="true")
class LiveDatabaseTest {
    @Test void persistsCrudAndRollsBackInvalidSnapshots() {
        var db=new DatabaseStore("jdbc:postgresql://127.0.0.1:15432/postgres","jarvis_test",System.getenv("PGPASSWORD"));
        db.check();
        var memory=db.create(Kind.memories,"SQL literal","Robert'); DROP TABLE jarvis.memories; --");
        UUID id=(UUID)memory.get("id");
        assertThat(db.list(Kind.memories,100,0)).anySatisfy(m -> assertThat(m.get("id")).isEqualTo(id));
        var changed=db.update(Kind.memories,id,1,"Updated","Remember this");
        assertThat(changed.get("version")).isEqualTo(2);
        assertThatThrownBy(() -> db.update(Kind.memories,id,1,"Stale","Wrong")).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("DATABASE_CONFLICT"));
        db.delete(Kind.memories,id,2);
        int before=db.list(Kind.conversations,100,0).size();
        assertThatThrownBy(() -> db.save("Must rollback",List.of(new Turn("user","Hello"),new Turn("system","Invalid")))).isInstanceOf(ApiException.class);
        assertThat(db.list(Kind.conversations,100,0)).hasSize(before);
        var saved=db.save("Conversation",List.of(new Turn("user","Hello"),new Turn("assistant","Hi")));
        UUID conversation=(UUID)saved.get("id");
        assertThat(saved.get("version")).isEqualTo(3);
        var messages=db.messages(conversation,0,100);
        assertThat(messages).hasSize(2);
        assertThat(db.messages(conversation,((Number)messages.getFirst().get("id")).longValue(),100)).hasSize(1);
        db.delete(Kind.conversations,conversation,3);
        assertThat(db.messages(conversation,0,100)).isEmpty();
    }
}
