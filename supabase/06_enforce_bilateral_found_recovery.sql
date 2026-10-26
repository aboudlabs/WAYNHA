-- Prevent one-sided status changes from marking a found-item report recovered.
CREATE OR REPLACE FUNCTION public.enforce_bilateral_found_recovery()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NEW.report_type = 'found' AND NEW.status = 'recovered' AND OLD.status IS DISTINCT FROM 'recovered' THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.recovery_requests rr
      WHERE rr.report_id = NEW.id AND rr.status = 'confirmed'
        AND rr.owner_confirmed = TRUE AND rr.finder_confirmed = TRUE
    ) THEN
      RAISE EXCEPTION 'لا يمكن إكمال استعادة غرض موجود دون تأكيد الطرفين';
    END IF;
  END IF;
  RETURN NEW;
END; $$;
DROP TRIGGER IF EXISTS trg_bilateral_found_recovery ON public.reports;
CREATE TRIGGER trg_bilateral_found_recovery
BEFORE UPDATE OF status ON public.reports
FOR EACH ROW EXECUTE FUNCTION public.enforce_bilateral_found_recovery();
