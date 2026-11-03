-- Synthetic vectors test database retrieval without calling an embedding model.
BEGIN;
DO $$
DECLARE
  doc uuid;
  hits integer;
BEGIN
  INSERT INTO jarvis.documents(file_name,file_path,content_hash,status,chunk_count)
  VALUES ('verification.txt','jarvis-validation-' || gen_random_uuid(),gen_random_uuid()::text,'ready',1)
  RETURNING id INTO doc;
  INSERT INTO jarvis.document_chunks(document_id,chunk_index,content,embedding)
  VALUES (doc,0,'The verification code is ORCHID.',array_fill(0.1::real, ARRAY[384])::vector);
  SELECT count(*) INTO hits FROM jarvis.match_chunks(array_fill(0.1::real, ARRAY[384])::vector,100,0.99) WHERE document_id=doc;
  IF hits<>1 THEN RAISE EXCEPTION 'Known-answer retrieval failed'; END IF;
  SELECT count(*) INTO hits FROM jarvis.match_chunks(array_fill(-0.1::real, ARRAY[384])::vector,100,0.99) WHERE document_id=doc;
  IF hits<>0 THEN RAISE EXCEPTION 'Negative retrieval failed'; END IF;
  DELETE FROM jarvis.documents WHERE id=doc;
  IF EXISTS (SELECT 1 FROM jarvis.document_chunks WHERE document_id=doc) THEN RAISE EXCEPTION 'Cascade deletion failed'; END IF;
END $$;
ROLLBACK;
