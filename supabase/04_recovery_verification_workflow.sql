-- WAYNHA: private item verification, recovery requests, and bilateral handover confirmation.
-- This migration is also applied to the connected Supabase project.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.report_verification_questions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  report_id UUID NOT NULL REFERENCES public.reports(id) ON DELETE CASCADE,
  question_text TEXT NOT NULL CHECK (char_length(trim(question_text)) BETWEEN 5 AND 180),
  position SMALLINT NOT NULL DEFAULT 1 CHECK (position BETWEEN 1 AND 3),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(report_id, position)
);
CREATE TABLE IF NOT EXISTS public.report_verification_answers (
  question_id UUID PRIMARY KEY REFERENCES public.report_verification_questions(id) ON DELETE CASCADE,
  answer_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.report_verification_questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.report_verification_answers ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS verification_questions_read ON public.report_verification_questions;
CREATE POLICY verification_questions_read ON public.report_verification_questions
  FOR SELECT TO authenticated USING (
    EXISTS (SELECT 1 FROM public.reports r WHERE r.id = report_id
      AND r.report_type = 'found' AND r.status IN ('open','matched','recovered'))
  );
DROP POLICY IF EXISTS verification_questions_owner_manage ON public.report_verification_questions;
CREATE POLICY verification_questions_owner_manage ON public.report_verification_questions
  FOR ALL TO authenticated USING (
    EXISTS (SELECT 1 FROM public.reports r WHERE r.id = report_id AND r.user_id = auth.uid())
  ) WITH CHECK (
    EXISTS (SELECT 1 FROM public.reports r WHERE r.id = report_id AND r.user_id = auth.uid() AND r.report_type = 'found')
  );
-- No direct client policies for answer hashes; they are only used by SECURITY DEFINER RPCs.

CREATE TABLE IF NOT EXISTS public.recovery_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  report_id UUID NOT NULL REFERENCES public.reports(id) ON DELETE CASCADE,
  claimant_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  finder_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  message TEXT NOT NULL DEFAULT '' CHECK (char_length(message) <= 1000),
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','rejected','cancelled','confirmed')),
  owner_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
  finder_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
  handover_notes TEXT NOT NULL DEFAULT '' CHECK (char_length(handover_notes) <= 1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  confirmed_at TIMESTAMPTZ,
  UNIQUE(report_id, claimant_id)
);
CREATE INDEX IF NOT EXISTS idx_recovery_requests_finder_status ON public.recovery_requests(finder_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_recovery_requests_claimant ON public.recovery_requests(claimant_id, created_at DESC);
ALTER TABLE public.recovery_requests ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS recovery_requests_read_involved ON public.recovery_requests;
CREATE POLICY recovery_requests_read_involved ON public.recovery_requests
  FOR SELECT TO authenticated USING (claimant_id = auth.uid() OR finder_id = auth.uid() OR public.is_admin());
DROP POLICY IF EXISTS recovery_requests_update_involved ON public.recovery_requests;
CREATE POLICY recovery_requests_update_involved ON public.recovery_requests
  FOR UPDATE TO authenticated
  USING (claimant_id = auth.uid() OR finder_id = auth.uid() OR public.is_admin())
  WITH CHECK (claimant_id = auth.uid() OR finder_id = auth.uid() OR public.is_admin());

CREATE OR REPLACE FUNCTION public.set_report_verification_questions(p_report_id UUID, p_questions JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, extensions AS $$
DECLARE r public.reports%ROWTYPE; q JSONB; qid UUID; pos INT := 0; answer_text TEXT;
BEGIN
  SELECT * INTO r FROM public.reports WHERE id = p_report_id FOR UPDATE;
  IF NOT FOUND OR r.user_id <> auth.uid() OR r.report_type <> 'found' THEN
    RAISE EXCEPTION 'غير مصرح: أسئلة التحقق متاحة لناشر بلاغ الموجود فقط';
  END IF;
  IF jsonb_typeof(p_questions) <> 'array' OR jsonb_array_length(p_questions) < 1 OR jsonb_array_length(p_questions) > 3 THEN
    RAISE EXCEPTION 'أضف سؤالاً واحداً إلى ثلاثة أسئلة تحقق';
  END IF;
  DELETE FROM public.report_verification_questions WHERE report_id = p_report_id;
  FOR q IN SELECT value FROM jsonb_array_elements(p_questions) LOOP
    pos := pos + 1;
    answer_text := lower(trim(coalesce(q->>'answer','')));
    IF char_length(trim(coalesce(q->>'question',''))) < 5 OR char_length(answer_text) < 2 THEN
      RAISE EXCEPTION 'السؤال أو الإجابة قصيرة جداً';
    END IF;
    INSERT INTO public.report_verification_questions(report_id, question_text, position)
    VALUES (p_report_id, trim(q->>'question'), pos) RETURNING id INTO qid;
    INSERT INTO public.report_verification_answers(question_id, answer_hash)
    VALUES (qid, encode(digest(answer_text, 'sha256'), 'hex'));
  END LOOP;
  RETURN jsonb_build_object('saved', true, 'count', pos);
END; $$;

CREATE OR REPLACE FUNCTION public.submit_recovery_request(p_report_id UUID, p_answers JSONB, p_message TEXT DEFAULT '')
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, extensions AS $$
DECLARE r public.reports%ROWTYPE; q RECORD; supplied TEXT; question_count INT := 0; correct_count INT := 0; req_id UUID;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'سجّل الدخول أولاً'; END IF;
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
  IF correct_count <> question_count THEN RAISE EXCEPTION 'بعض إجابات التحقق غير صحيحة. راجع تفاصيل الغرض وحاول مجدداً'; END IF;
  INSERT INTO public.recovery_requests(report_id, claimant_id, finder_id, message)
  VALUES (p_report_id, auth.uid(), r.user_id, left(coalesce(trim(p_message),''),1000))
  ON CONFLICT (report_id, claimant_id) DO UPDATE SET status = 'pending', message = EXCLUDED.message, updated_at = now()
  RETURNING id INTO req_id;
  RETURN jsonb_build_object('request_id', req_id, 'status', 'pending');
END; $$;

CREATE OR REPLACE FUNCTION public.review_recovery_request(p_request_id UUID, p_accept BOOLEAN)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE req public.recovery_requests%ROWTYPE;
BEGIN
  SELECT * INTO req FROM public.recovery_requests WHERE id = p_request_id FOR UPDATE;
  IF NOT FOUND OR req.finder_id <> auth.uid() THEN RAISE EXCEPTION 'فقط ناشر بلاغ الموجود يمكنه مراجعة الطلب'; END IF;
  IF req.status <> 'pending' THEN RAISE EXCEPTION 'تمت مراجعة هذا الطلب مسبقاً'; END IF;
  UPDATE public.recovery_requests SET status = CASE WHEN p_accept THEN 'accepted' ELSE 'rejected' END, updated_at = now()
    WHERE id = p_request_id;
  RETURN jsonb_build_object('request_id', p_request_id, 'status', CASE WHEN p_accept THEN 'accepted' ELSE 'rejected' END);
END; $$;

CREATE OR REPLACE FUNCTION public.confirm_recovery_handover(p_request_id UUID, p_notes TEXT DEFAULT '')
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE req public.recovery_requests%ROWTYPE; both_confirmed BOOLEAN;
BEGIN
  SELECT * INTO req FROM public.recovery_requests WHERE id = p_request_id FOR UPDATE;
  IF NOT FOUND OR auth.uid() NOT IN (req.claimant_id, req.finder_id) THEN RAISE EXCEPTION 'غير مصرح بتأكيد هذا التسليم'; END IF;
  IF req.status NOT IN ('accepted','confirmed') THEN RAISE EXCEPTION 'يجب قبول طلب الاستعادة قبل تأكيد التسليم'; END IF;
  UPDATE public.recovery_requests SET
    owner_confirmed = owner_confirmed OR claimant_id = auth.uid(),
    finder_confirmed = finder_confirmed OR finder_id = auth.uid(),
    handover_notes = CASE WHEN trim(coalesce(p_notes,'')) = '' THEN handover_notes ELSE left(trim(p_notes),1000) END,
    updated_at = now()
  WHERE id = p_request_id
  RETURNING (owner_confirmed AND finder_confirmed) INTO both_confirmed;
  IF both_confirmed THEN
    UPDATE public.recovery_requests SET status='confirmed', confirmed_at=now(), updated_at=now() WHERE id=p_request_id;
    UPDATE public.reports SET status='recovered', updated_at=now() WHERE id=req.report_id;
  END IF;
  RETURN jsonb_build_object('request_id',p_request_id,'both_confirmed',both_confirmed,'status',CASE WHEN both_confirmed THEN 'confirmed' ELSE 'accepted' END);
END; $$;

REVOKE ALL ON FUNCTION public.set_report_verification_questions(UUID, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_recovery_request(UUID, JSONB, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.review_recovery_request(UUID, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.confirm_recovery_handover(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_report_verification_questions(UUID, JSONB) TO authenticated;
GRANT EXECUTE ON FUNCTION public.submit_recovery_request(UUID, JSONB, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.review_recovery_request(UUID, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.confirm_recovery_handover(UUID, TEXT) TO authenticated;
