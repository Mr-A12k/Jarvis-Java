-- Run in pgAdmin Query Tool connected to the dedicated JARVIS database.
-- Ownership here is the authenticated PostgreSQL login (session_user).
-- This is NOT application multi-user isolation when all users share one DB login.
-- Never expose these functions as unrestricted model tools.
BEGIN;
CREATE SCHEMA jarvis;
REVOKE ALL ON SCHEMA jarvis FROM PUBLIC;

CREATE TABLE jarvis.memories (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 owner_name name NOT NULL DEFAULT session_user,
 title text NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 200),
 content text NOT NULL CHECK (length(btrim(content)) BETWEEN 1 AND 8000),
 version integer NOT NULL DEFAULT 1,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE jarvis.conversations (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 owner_name name NOT NULL DEFAULT session_user,
 title text NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 200),
 version integer NOT NULL DEFAULT 1,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE jarvis.messages (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 conversation_id uuid NOT NULL REFERENCES jarvis.conversations(id) ON DELETE CASCADE,
 role text NOT NULL CHECK (role IN ('user','assistant')),
 content text NOT NULL CHECK (length(btrim(content)) BETWEEN 1 AND 16000),
 created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ON jarvis.memories(owner_name, created_at DESC, id);
CREATE INDEX ON jarvis.conversations(owner_name, created_at DESC, id);
CREATE INDEX ON jarvis.messages(conversation_id,id);

ALTER TABLE jarvis.memories ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.memories FORCE ROW LEVEL SECURITY;

CREATE POLICY memory_owner ON jarvis.memories USING (owner_name=session_user) WITH CHECK (owner_name=session_user);

ALTER TABLE jarvis.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.conversations FORCE ROW LEVEL SECURITY;

CREATE POLICY conversation_owner ON jarvis.conversations USING (owner_name=session_user) WITH CHECK (owner_name=session_user);

ALTER TABLE jarvis.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.messages FORCE ROW LEVEL SECURITY;

CREATE POLICY message_owner ON jarvis.messages USING (
 EXISTS(SELECT 1 FROM jarvis.conversations c WHERE c.id=conversation_id AND c.owner_name=session_user)
) WITH CHECK (
 EXISTS(SELECT 1 FROM jarvis.conversations c WHERE c.id=conversation_id AND c.owner_name=session_user)
);

CREATE FUNCTION jarvis.memory_create(p_title text,p_content text) RETURNS jarvis.memories
LANGUAGE sql SECURITY INVOKER SET search_path=pg_catalog,jarvis AS $$
 INSERT INTO jarvis.memories(title,content) VALUES(p_title,p_content) RETURNING *;
$$;

CREATE FUNCTION jarvis.memory_read(p_limit integer DEFAULT 30,p_offset integer DEFAULT 0) RETURNS SETOF jarvis.memories
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,jarvis AS $$
BEGIN
 IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 100 OR p_offset IS NULL OR p_offset NOT BETWEEN 0 AND 100000 THEN RAISE EXCEPTION 'Invalid pagination' USING ERRCODE='22023'; END IF;
 RETURN QUERY SELECT * FROM jarvis.memories WHERE owner_name=session_user ORDER BY created_at DESC,id LIMIT p_limit OFFSET p_offset;
END $$;

CREATE FUNCTION jarvis.memory_update(p_id uuid,p_version integer,p_title text,p_content text) RETURNS jarvis.memories
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,jarvis AS $$
DECLARE result jarvis.memories;
BEGIN
 UPDATE jarvis.memories SET title=p_title,content=p_content,version=version+1,updated_at=now()
 WHERE id=p_id AND owner_name=session_user AND version=p_version RETURNING * INTO result;
 IF NOT FOUND THEN RAISE EXCEPTION 'Memory missing, inaccessible, or changed; refresh before retrying' USING ERRCODE='P0002'; END IF;
 RETURN result;
END $$;

CREATE FUNCTION jarvis.memory_delete(p_id uuid,p_version integer) RETURNS boolean
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,jarvis AS $$
BEGIN
 DELETE FROM jarvis.memories WHERE id=p_id AND owner_name=session_user AND version=p_version;
 IF NOT FOUND THEN RAISE EXCEPTION 'Memory missing, inaccessible, or changed' USING ERRCODE='P0002'; END IF;
 RETURN true;
END $$;

CREATE FUNCTION jarvis.conversation_create(p_title text) RETURNS jarvis.conversations
LANGUAGE sql SECURITY INVOKER SET search_path=pg_catalog,jarvis AS $$
 INSERT INTO jarvis.conversations(title) VALUES(p_title) RETURNING *;
$$;

CREATE FUNCTION jarvis.conversation_read(p_limit integer DEFAULT 30,p_offset integer DEFAULT 0) RETURNS SETOF jarvis.conversations
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,jarvis AS $$
BEGIN
 IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 100 OR p_offset IS NULL OR p_offset NOT BETWEEN 0 AND 100000 THEN RAISE EXCEPTION 'Invalid pagination' USING ERRCODE='22023'; END IF;
 RETURN QUERY SELECT * FROM jarvis.conversations WHERE owner_name=session_user ORDER BY created_at DESC,id LIMIT p_limit OFFSET p_offset;
END $$;

CREATE FUNCTION jarvis.conversation_update(p_id uuid,p_version integer,p_title text) RETURNS jarvis.conversations
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,jarvis AS $$
DECLARE result jarvis.conversations;
BEGIN
 UPDATE jarvis.conversations SET title=p_title,version=version+1,updated_at=now()
 WHERE id=p_id AND owner_name=session_user AND version=p_version RETURNING * INTO result;
 IF NOT FOUND THEN RAISE EXCEPTION 'Conversation missing, inaccessible, or changed' USING ERRCODE='P0002'; END IF;
 RETURN result;
END $$;

CREATE FUNCTION jarvis.conversation_delete(p_id uuid,p_version integer) RETURNS boolean
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,jarvis AS $$
BEGIN
 DELETE FROM jarvis.conversations WHERE id=p_id AND owner_name=session_user AND version=p_version;
 IF NOT FOUND THEN RAISE EXCEPTION 'Conversation missing, inaccessible, or changed' USING ERRCODE='P0002'; END IF;
 RETURN true;
END $$;

CREATE FUNCTION jarvis.message_append(p_conversation uuid,p_role text,p_content text) RETURNS jarvis.messages
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,jarvis AS $$
DECLARE result jarvis.messages;
BEGIN
 UPDATE jarvis.conversations SET version=version+1,updated_at=now() WHERE id=p_conversation AND owner_name=session_user;
 IF NOT FOUND THEN RAISE EXCEPTION 'Conversation missing or inaccessible' USING ERRCODE='P0002'; END IF;
 INSERT INTO jarvis.messages(conversation_id,role,content) VALUES(p_conversation,p_role,p_content) RETURNING * INTO result;
 RETURN result;
END $$;

CREATE FUNCTION jarvis.message_read(p_conversation uuid,p_after bigint DEFAULT 0,p_limit integer DEFAULT 50) RETURNS SETOF jarvis.messages
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,jarvis AS $$
BEGIN
 IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 100 OR p_after IS NULL OR p_after<0 THEN RAISE EXCEPTION 'Invalid pagination' USING ERRCODE='22023'; END IF;
 RETURN QUERY SELECT m.* FROM jarvis.messages m JOIN jarvis.conversations c ON c.id=m.conversation_id
 WHERE c.id=p_conversation AND c.owner_name=session_user AND m.id>p_after ORDER BY m.id LIMIT p_limit;
END $$;

REVOKE ALL ON ALL FUNCTIONS IN SCHEMA jarvis FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA jarvis FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA jarvis FROM PUBLIC;
COMMIT;
