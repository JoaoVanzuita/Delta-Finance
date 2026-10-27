ALTER TABLE financial_goal
  ADD COLUMN notes_enc bytea;

COMMENT ON COLUMN financial_goal.notes_enc IS
  'Observações livres da meta, cifradas com AES-256-GCM.';
