CREATE OR REPLACE FUNCTION biofid_search_layer_metadata(
	IN corpus_id bigint,
	
    IN input1 text[], 
    IN input2 text,
    IN take_count integer,
    IN offset_count integer,
	
    IN count_all boolean DEFAULT false,
	
    IN order_direction text DEFAULT 'DESC',
    IN order_by_column text DEFAULT 'title',
	
    OUT total_count_out integer,
    OUT document_ids integer[],
    OUT named_entities_found text[][],
    OUT time_found text[][],
    OUT taxons_found text[][]
)
RETURNS record AS $$
DECLARE
    -- Declare variables to hold total count, document IDs, named entities, time, and taxons
    total_count_temp integer;
    document_ids_temp integer[];
    named_entities_temp text[][];
    time_temp text[][];
    taxons_temp text[][];
BEGIN
    -- Common table expression to define the set of documents
    WITH documents_query AS (
        SELECT DISTINCT d.id
        FROM document d
        WHERE d.corpusid = corpus_id and (d.documenttitle = ANY(input1) OR d.language = ANY(input1))
        
        UNION
        
        SELECT DISTINCT d.id
        FROM document d
        JOIN metadatatitleinfo me ON d.id = me.id
        WHERE d.corpusid = corpus_id and (me.title ~* input2 OR me.published ~* input2)
		
		UNION
        
        SELECT DISTINCT d.id
        FROM document d
        JOIN lemma l ON d.id = l.document_id
        WHERE d.corpusid = corpus_id and (l.value ~* input2 OR l.coveredtext ~* input2)
    ),
    -- Count all found documents
    counted_documents AS (
        SELECT COUNT(*) AS total_count FROM documents_query
    )
    
    -- Retrieve total count, document IDs, named entities, time, and taxons (conditionally)
    SELECT 
      CASE WHEN count_all THEN (SELECT total_count FROM counted_documents) ELSE NULL END AS total_count,
      ARRAY(
        SELECT dq.id
        FROM (
            SELECT dq.id
            FROM documents_query dq
            JOIN metadatatitleinfo me ON dq.id = me.id
			
			-- This ordering is a bit scuffed, but it finally works. A lot of copy pasting when adding new cases, but that should happend often. --
			ORDER BY 
			  CASE 
				WHEN order_by_column = 'title' THEN 
				  CASE WHEN order_direction = 'ASC' THEN me.title ELSE NULL END
				WHEN order_by_column = 'published' THEN 
				  CASE WHEN order_direction = 'ASC' THEN me.published ELSE NULL END
				-- Add more cases for other valid columns
				ELSE NULL
			  END ASC,
			  CASE 
				WHEN order_by_column = 'title' THEN 
				  CASE WHEN order_direction = 'DESC' THEN me.title ELSE NULL END
				WHEN order_by_column = 'published' THEN 
				  CASE WHEN order_direction = 'DESC' THEN me.published ELSE NULL END
				-- Add more cases for other valid columns
				ELSE NULL
			  END DESC

			LIMIT take_count OFFSET offset_count
        ) AS dq
      ) AS document_ids_temp,
	  
	  -- Count the occurences of all the found entities, taxons etc. --
	  
	  CASE WHEN count_all THEN
		  ARRAY(
			SELECT ARRAY[ne.coveredtext, COUNT(ne.id)::text] AS named_entity
			FROM documents_query dq
			JOIN namedentity ne ON dq.id = ne.document_id
			GROUP BY ne.coveredtext
		  )
		  ELSE ARRAY[]::text[][]
	  END AS named_entities_temp,
	  
	  CASE WHEN count_all THEN
		  ARRAY(
			SELECT ARRAY[t.coveredtext, COUNT(t.id)::text] AS time
			FROM documents_query dq
			JOIN time t ON dq.id = t.document_id
			GROUP BY t.coveredtext
		  ) 
		  ELSE ARRAY[]::text[][]
	  END AS time_temp,
	  
	  CASE WHEN count_all THEN
      ARRAY(
        SELECT ARRAY[ta.coveredtext, COUNT(ta.id)::text] AS taxon
        FROM documents_query dq
        JOIN taxon ta ON dq.id = ta.document_id
        GROUP BY ta.coveredtext
      )
	  ELSE ARRAY[]::text[][]
	  END AS taxons_temp
	  
    INTO total_count_temp, document_ids_temp, named_entities_temp, time_temp, taxons_temp
    FROM (SELECT 1) AS dummy;
    
    -- Set out parameters
    total_count_out := total_count_temp;
    document_ids := document_ids_temp;
    named_entities_found := named_entities_temp;
    time_found := time_temp;
    taxons_found := taxons_temp;
END;
$$ LANGUAGE plpgsql;
