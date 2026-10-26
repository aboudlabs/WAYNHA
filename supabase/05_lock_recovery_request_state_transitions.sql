-- Enforce bilateral confirmation: state transitions only through SECURITY DEFINER RPCs.
DROP POLICY IF EXISTS recovery_requests_update_involved ON public.recovery_requests;
REVOKE UPDATE, INSERT, DELETE ON public.recovery_requests FROM authenticated;
GRANT SELECT ON public.recovery_requests TO authenticated;
