CREATE OR REPLACE FUNCTION public.clear_public_distinguishing_marks()
RETURNS trigger LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  NEW.distinguishing_marks := '';
  RETURN NEW;
END; $$;
DROP TRIGGER IF EXISTS trg_clear_public_distinguishing_marks ON public.reports;
CREATE TRIGGER trg_clear_public_distinguishing_marks
BEFORE INSERT OR UPDATE OF distinguishing_marks ON public.reports
FOR EACH ROW EXECUTE FUNCTION public.clear_public_distinguishing_marks();
