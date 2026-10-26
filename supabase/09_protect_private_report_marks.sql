CREATE TABLE IF NOT EXISTS public.report_private_details (
  report_id UUID PRIMARY KEY REFERENCES public.reports(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  distinguishing_marks TEXT NOT NULL DEFAULT '' CHECK (char_length(distinguishing_marks) <= 2000),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.report_private_details ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS report_private_details_owner_read ON public.report_private_details;
CREATE POLICY report_private_details_owner_read ON public.report_private_details
  FOR SELECT TO authenticated USING (user_id = auth.uid() OR public.is_admin());
DROP POLICY IF EXISTS report_private_details_owner_manage ON public.report_private_details;
CREATE POLICY report_private_details_owner_manage ON public.report_private_details
  FOR ALL TO authenticated USING (user_id = auth.uid() OR public.is_admin())
  WITH CHECK (user_id = auth.uid() OR public.is_admin());

INSERT INTO public.report_private_details(report_id, user_id, distinguishing_marks)
SELECT id, user_id, distinguishing_marks FROM public.reports
WHERE trim(coalesce(distinguishing_marks,'')) <> ''
ON CONFLICT (report_id) DO UPDATE SET distinguishing_marks = EXCLUDED.distinguishing_marks, user_id = EXCLUDED.user_id, updated_at = now();
UPDATE public.reports SET distinguishing_marks = '' WHERE trim(coalesce(distinguishing_marks,'')) <> '';

CREATE OR REPLACE FUNCTION public.set_report_private_details(p_report_id UUID, p_marks TEXT)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r public.reports%ROWTYPE;
BEGIN
  SELECT * INTO r FROM public.reports WHERE id = p_report_id FOR UPDATE;
  IF NOT FOUND OR r.user_id <> auth.uid() THEN RAISE EXCEPTION 'فقط ناشر البلاغ يمكنه حفظ العلامات الخاصة'; END IF;
  INSERT INTO public.report_private_details(report_id,user_id,distinguishing_marks,updated_at)
  VALUES (p_report_id,auth.uid(),left(coalesce(trim(p_marks),''),2000),now())
  ON CONFLICT (report_id) DO UPDATE SET distinguishing_marks=EXCLUDED.distinguishing_marks, updated_at=now();
  RETURN jsonb_build_object('saved',true);
END; $$;

CREATE OR REPLACE FUNCTION public.get_my_report_private_details()
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT coalesce(jsonb_object_agg(report_id::text, distinguishing_marks), '{}'::jsonb)
  FROM public.report_private_details WHERE user_id = auth.uid();
$$;
REVOKE ALL ON FUNCTION public.set_report_private_details(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_my_report_private_details() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_report_private_details(UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_report_private_details() TO authenticated;
