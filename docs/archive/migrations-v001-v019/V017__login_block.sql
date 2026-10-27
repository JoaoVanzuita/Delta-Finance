-- Bloqueio de login persistido (C02).
--
-- A janela deslizante sozinha liberava o login antes do fim do bloqueio quando
-- as cinco falhas estavam espalhadas no tempo. O instante final do bloqueio é
-- gravado na própria tentativa que atingiu o limite e sobrevive a reinícios.
ALTER TABLE login_attempt ADD COLUMN blocked_until timestamptz;

COMMENT ON COLUMN login_attempt.blocked_until IS
  'Fim do bloqueio persistido; nulo quando a tentativa não gerou bloqueio.';
