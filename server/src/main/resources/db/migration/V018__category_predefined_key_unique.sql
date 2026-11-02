-- Categorias predefinidas são identificadas por predefined_key. O fluxo antigo resolvia a
-- predefinida pelo nome e podia criar uma segunda linha com a mesma chave após um rename.
-- Consolida eventuais duplicatas preservando a mais antiga e reatribui as transações antes de
-- impor a unicidade.
--
-- O deploy self-hosted é single-instance: a versão antiga é encerrada antes desta migração, então
-- a restrição não precisa conviver com o fluxo que criava duplicatas.
WITH ranked AS (
    SELECT id,
           user_id,
           predefined_key,
           first_value(id) OVER (
               PARTITION BY user_id, predefined_key
               ORDER BY created_at, id
           ) AS keep_id,
           row_number() OVER (
               PARTITION BY user_id, predefined_key
               ORDER BY created_at, id
           ) AS position
      FROM category
     WHERE predefined_key IS NOT NULL
)
UPDATE transaction t
   SET category_id = r.keep_id
  FROM ranked r
 WHERE t.user_id = r.user_id
   AND t.category_id = r.id
   AND r.position > 1;

WITH ranked AS (
    SELECT id,
           user_id,
           predefined_key,
           row_number() OVER (
               PARTITION BY user_id, predefined_key
               ORDER BY created_at, id
           ) AS position
      FROM category
     WHERE predefined_key IS NOT NULL
)
DELETE FROM category c
 USING ranked r
 WHERE c.id = r.id
   AND c.user_id = r.user_id
   AND r.position > 1;

CREATE UNIQUE INDEX category_user_predefined_uk
    ON category (user_id, predefined_key)
    WHERE predefined_key IS NOT NULL;
