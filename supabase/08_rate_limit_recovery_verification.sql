CREATE TABLE IF NOT EXISTS public.recovery_verification_attempts (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  report_id UUID NOT NULL REFERENCES public.reports(id) ON DELETE CASCADE,
  claimant_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  succeeded BOOLEAN NOT NULL DEFAULT FALSE,
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_recovery_attempts_rate_limit
  ON public.recovery_verification_attempts(report_id, claimant_id, attempted_at DESC);
ALTER TABLE public.recovery_verification_attempts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.recovery_verification_attempts FROM anon, authenticated;

CREATE OR REPLACE FUNCTION public.submit_recovery_request(p_report_id UUID, p_answers JSONB, p_message TEXT DEFAULT '')
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, extensions AS $$
DECLARE r public.reports%ROWTYPE; q RECORD; supplied TEXT; question_count INT := 0; correct_count INT := 0; req_id UUID; attempts INT;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'سجّل الدخول أولاً'; END IF;
  SELECT count(*) INTO attempts FROM public.recovery_verification_attempts
    WHERE report_id = p_report_id AND claimant_id = auth.uid()
      AND attempted_at > now() - interval '1 hour';
  IF attempts >= 5 THEN RETURN jsonb_build_object('verified',false,'rate_limited',true); END IF;
  SELECT * INTO r FROM public.reports WHERE id = p_report_id FOR SHARE;
  IF NOT FOUND OR r.report_type <> 'found' OR r.status NOT IN ('open','matched') THEN RAISE EXCEPTION 'البلاغ غير متاح لطلب الاستعادة'; END IF;
  IF r.user_id = auth.uid() THEN RAISE EXCEPTION 'لا يمكنك طلب استعادة غرض من بلاغك'; END IF;
  FOR q IN SELECT v.id, a.answer_hash FROM public.report_verification_questions v
    JOIN public.report_verification_answers a ON a.question_id = v.id
    WHERE v.report_id = p_report_id ORDER BY v.position LOOP
    question_count := question_count + 1;
    SELECT lower(trim(coalesce(x->>'answer',''))) INTO supplied
      FROM jsonb_array_elements(coalesce(p_answers,'[]'::jsonb)) x WHERE x->>'question_id' = q.id::text LIMIT 1;
    IF supplied IS NOT NULL AND encode(digest(supplied, 'sha256'), 'hex') = q.answer_hash THEN correct_count := correct_count + 1; END IF;
  END LOOP;
  IF question_count = 0 THEN RAISE EXCEPTION 'ناشر البلاغ لم يضف أسئلة تحقق بعد'; END IF;
  IF correct_count <> question_count THEN
    INSERT INTO public.recovery_verification_attempts(report_id, claimant_id, succeeded)
    VALUES (p_report_id, auth.uid(), FALSE);
    RETURN jsonb_build_object('verified',false,'rate_limited',false);
  END IF;
  INSERT INTO public.recovery_verification_attempts(report_id, claimant_id, succeeded)
  VALUES (p_report_id, auth.uid(), TRUE);
  INSERT INTO public.recovery_requests(report_id, claimant_id, finder_id, message)
  VALUES (p_report_id, auth.uid(), r.user_id, left(coalesce(trim(p_message),''),1000))
  ON CONFLICT (report_id, claimant_id) DO UPDATE SET status = 'pending', message = EXCLUDED.message,
    owner_confirmed = FALSE, finder_confirmed = FALSE, handover_notes = '', confirmed_at = NULL, updated_at = now()
  RETURNING id INTO req_id;
  RETURN jsonb_build_object('verified',true,'request_id', req_id, 'status', 'pending');
END; $$;
