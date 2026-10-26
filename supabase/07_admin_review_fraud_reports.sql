CREATE OR REPLACE FUNCTION public.review_report_flag(p_flag_id UUID, p_action TEXT, p_notes TEXT DEFAULT '')
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE f public.reports_flags%ROWTYPE; new_status TEXT;
BEGIN
  IF NOT public.is_admin() THEN RAISE EXCEPTION 'صلاحية الإدارة مطلوبة لمراجعة البلاغات'; END IF;
  IF p_action NOT IN ('hide','dismiss','review') THEN RAISE EXCEPTION 'إجراء مراجعة غير صالح'; END IF;
  SELECT * INTO f FROM public.reports_flags WHERE id = p_flag_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'بلاغ المخالفة غير موجود'; END IF;
  IF p_action = 'hide' AND f.report_id IS NOT NULL THEN
    UPDATE public.reports SET status = 'hidden', updated_at = now() WHERE id = f.report_id;
    new_status := 'action_taken';
  ELSIF p_action = 'dismiss' THEN
    new_status := 'dismissed';
  ELSE
    new_status := 'reviewed';
  END IF;
  UPDATE public.reports_flags SET status = new_status, reviewed_by = auth.uid(), reviewed_at = now() WHERE id = p_flag_id;
  INSERT INTO public.admin_audit_logs(admin_id, action_type, target_table, target_id, notes)
  VALUES (auth.uid(), 'review_report_flag', 'reports_flags', p_flag_id::text,
          left(coalesce(p_notes,'') || ' | action=' || p_action, 1000));
  RETURN jsonb_build_object('flag_id', p_flag_id, 'status', new_status);
END; $$;
REVOKE ALL ON FUNCTION public.review_report_flag(UUID, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.review_report_flag(UUID, TEXT, TEXT) TO authenticated;
