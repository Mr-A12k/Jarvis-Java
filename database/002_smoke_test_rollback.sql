-- Execute after 001 in a development JARVIS database. All sample changes roll back.
BEGIN;
DO $$
DECLARE m jarvis.memories; c jarvis.conversations; msg jarvis.messages; count_rows integer;
BEGIN
 SELECT * INTO m FROM jarvis.memory_create('Test preference','Use concise answers');
 SELECT * INTO m FROM jarvis.memory_update(m.id,m.version,'Test preference','Use detailed answers');
 IF m.version<>2 THEN RAISE EXCEPTION 'Version test failed'; END IF;
 BEGIN
  PERFORM jarvis.memory_update(m.id,1,'Stale','Must not overwrite');
  RAISE EXCEPTION 'Stale update accepted';
 EXCEPTION WHEN no_data_found THEN NULL;
 END;
 BEGIN
  PERFORM jarvis.memory_read(101,0);
  RAISE EXCEPTION 'Invalid pagination accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL;
 END;
 PERFORM jarvis.memory_delete(m.id,m.version);
 IF EXISTS(SELECT 1 FROM jarvis.memories WHERE id=m.id) THEN RAISE EXCEPTION 'Memory deletion failed'; END IF;
 SELECT * INTO c FROM jarvis.conversation_create('Test chat');
 BEGIN
  PERFORM jarvis.message_append(c.id,'system','Invalid role');
  RAISE EXCEPTION 'Invalid message accepted';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
 IF (SELECT version FROM jarvis.conversations WHERE id=c.id)<>1 THEN RAISE EXCEPTION 'Failed append changed version'; END IF;
 SELECT * INTO msg FROM jarvis.message_append(c.id,'user','Hello');
 SELECT count(*) INTO count_rows FROM jarvis.message_read(c.id);
 IF count_rows<>1 THEN RAISE EXCEPTION 'Message retrieval failed'; END IF;
 SELECT * INTO c FROM jarvis.conversation_update(c.id,2,'Renamed test chat');
 PERFORM jarvis.conversation_delete(c.id,c.version);
 IF EXISTS(SELECT 1 FROM jarvis.messages WHERE conversation_id=c.id) THEN RAISE EXCEPTION 'Cascade failed'; END IF;
 RAISE NOTICE 'CRUD smoke checks passed; transaction will roll back';
END $$;
ROLLBACK;
