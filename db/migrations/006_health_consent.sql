-- =====================================================================
-- 006 · v1.7: consentimiento expreso para guardar el peso corporal
-- (dato de salud, LFPDPPP art. 8). Correr UNA vez en Neon → SQL Editor.
-- NULL = no ha dado su consentimiento (o lo retiró). La app lo escribe con el resto del perfil.
-- =====================================================================
alter table public.profiles add column if not exists health_consent_at timestamptz;
