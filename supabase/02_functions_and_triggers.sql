-- =============================================================================
-- مشروع WAYNHA (وينها؟) - المرحلة 2: الدوال والقيود الخادمية والمطابقة التلقائية
-- ترتيب التنفيذ: شغّل هذا الملف ثانياً (02_functions_and_triggers.sql)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. دالة التحقق من صلاحية المدير على الخادم (لا تعتمد على الواجهة أبداً)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.profiles
        WHERE id = auth.uid()
          AND role IN ('admin', 'moderator')
          AND is_suspended = FALSE
    );
$$;

-- -----------------------------------------------------------------------------
-- 2. دالة التحقق من عضوية المحادثة وعدم وجود حظر
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.is_conversation_member(conv_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.conversation_members cm
        JOIN public.conversations c ON c.id = cm.conversation_id
        WHERE cm.conversation_id = conv_id
          AND cm.user_id = auth.uid()
          AND c.status = 'active'
          AND NOT EXISTS (
              SELECT 1 FROM public.user_blocks ub
              WHERE (ub.blocker_id = c.initiator_id AND ub.blocked_id = c.recipient_id)
                 OR (ub.blocker_id = c.recipient_id AND ub.blocked_id = c.initiator_id)
          )
    );
$$;

-- -----------------------------------------------------------------------------
-- 3. منع أي مستخدم عادي من ترقية نفسه إلى مدير أو إلغاء حظر حسابه
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.prevent_self_role_escalation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT public.is_admin() THEN
        IF NEW.role IS DISTINCT FROM OLD.role THEN
            RAISE EXCEPTION 'غير مصرح لك بتعديل صلاحيات الحساب (role).';
        END IF;
        IF NEW.is_suspended IS DISTINCT FROM OLD.is_suspended THEN
            RAISE EXCEPTION 'غير مصرح لك بتعديل حالة إيقاف الحساب.';
        END IF;
    END IF;
    NEW.city := 'حمص';
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_profiles_security ON public.profiles;
CREATE TRIGGER trg_profiles_security
BEFORE UPDATE ON public.profiles
FOR EACH ROW
EXECUTE FUNCTION public.prevent_self_role_escalation();

-- -----------------------------------------------------------------------------
-- 4. التحقق الصارم من مدينة حمص ومنع نشر أرقام الهواتف علناً في البلاغات
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.enforce_homs_and_privacy_on_report()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    city_active BOOLEAN;
    recent_count INT;
BEGIN
    -- التحقق من أن المدينة مفعّلة في جدول إعدادات النظام (حمص فقط حالياً)
    SELECT is_enabled INTO city_active
    FROM public.system_cities
    WHERE name_ar = NEW.city;

    IF city_active IS NOT TRUE OR NEW.city <> 'حمص' THEN
        RAISE EXCEPTION 'التطبيق مخصص حالياً لمدينة حمص فقط. لا يمكن نشر بلاغ خارج حمص.';
    END IF;

    -- منع نشر أرقام الهواتف علناً داخل العنوان أو الوصف أو العلامات المميزة
    IF (NEW.title ~ '(\+?963|09[0-9]{8}|[0-9]{9,12})')
       OR (NEW.description ~ '(\+?963|09[0-9]{8}|[0-9]{9,12})')
       OR (COALESCE(NEW.distinguishing_marks, '') ~ '(\+?963|09[0-9]{8}|[0-9]{9,12})') THEN
        RAISE EXCEPTION 'لأمانك، يمنع نشر أرقام الهواتف أو الأرقام الشخصية الحساسة علناً في البلاغ. استخدم المحادثة الآمنة داخل التطبيق.';
    END IF;

    -- حد لمعدل النشر لمنع الإزعاج (Rate Limiting: بحد أقصى 5 بلاغات خلال 10 دقائق)
    IF TG_OP = 'INSERT' THEN
        SELECT COUNT(*) INTO recent_count
        FROM public.reports
        WHERE user_id = NEW.user_id
          AND created_at > NOW() - INTERVAL '10 minutes';

        IF recent_count >= 5 THEN
            RAISE EXCEPTION 'لقد تجاوزت الحد المسموح لنشر البلاغات المتتالية. يرجى الانتظار قليلاً.';
        END IF;
    END IF;

    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_report ON public.reports;
CREATE TRIGGER trg_validate_report
BEFORE INSERT OR UPDATE ON public.reports
FOR EACH ROW
EXECUTE FUNCTION public.enforce_homs_and_privacy_on_report();

-- -----------------------------------------------------------------------------
-- 5. إيقاف المطابقات الجديدة عند إغلاق البلاغ أو استعادة الغرض
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_report_status_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NEW.status IN ('closed', 'recovered', 'hidden') AND OLD.status = 'open' THEN
        UPDATE public.report_matches
        SET review_status = 'archived',
            updated_at = NOW()
        WHERE (lost_report_id = NEW.id OR found_report_id = NEW.id)
          AND review_status = 'pending';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_report_status_change ON public.reports;
CREATE TRIGGER trg_report_status_change
AFTER UPDATE OF status ON public.reports
FOR EACH ROW
EXECUTE FUNCTION public.handle_report_status_change();

-- -----------------------------------------------------------------------------
-- 6. خوارزمية المطابقة المعتمدة على القواعد (Rule-based Matching داخل حمص)
-- تحسب درجة التشابه من 100 بناءً على: التصنيف (40)، الحي (25)، تقارب التاريخ (15)،
-- تشابه الكلمات المفتاحية والعلامات المميزة (20).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.compute_report_matches(target_report_id UUID)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    target_rec public.reports%ROWTYPE;
    candidate_rec public.reports%ROWTYPE;
    score NUMERIC(5,2);
    reasons TEXT[];
    inserted_count INT := 0;
    lost_id UUID;
    found_id UUID;
    notify_user_id UUID;
BEGIN
    SELECT * INTO target_rec FROM public.reports WHERE id = target_report_id;
    IF NOT FOUND OR target_rec.status <> 'open' OR target_rec.city <> 'حمص' THEN
        RETURN 0;
    END IF;

    FOR candidate_rec IN
        SELECT *
        FROM public.reports
        WHERE city = 'حمص'
          AND status = 'open'
          AND report_type <> target_rec.report_type
          AND user_id <> target_rec.user_id
          AND category_id = target_rec.category_id
    LOOP
        score := 40.0; -- تطابق التصنيف الأساسي
        reasons := ARRAY['تطابق التصنيف'];

        -- تطابق الحي أو المنطقة داخل حمص
        IF target_rec.neighborhood IS NOT NULL
           AND candidate_rec.neighborhood IS NOT NULL
           AND trim(target_rec.neighborhood) <> ''
           AND trim(target_rec.neighborhood) = trim(candidate_rec.neighborhood) THEN
            score := score + 25.0;
            reasons := array_append(reasons, 'نفس الحي في حمص (' || target_rec.neighborhood || ')');
        END IF;

        -- تقارب التاريخ (خلال 7 أيام)
        IF ABS(EXTRACT(EPOCH FROM (target_rec.incident_date - candidate_rec.incident_date))) <= (7 * 86400) THEN
            score := score + 15.0;
            reasons := array_append(reasons, 'تقارب تاريخ الفقد والعثور');
        END IF;

        -- تشابه الكلمات في العنوان والوصف والعلامات المميزة
        IF position(lower(split_part(trim(target_rec.title), ' ', 1)) in lower(candidate_rec.title || ' ' || candidate_rec.description)) > 0 THEN
            score := score + 12.0;
            reasons := array_append(reasons, 'تشابه في اسم أو وصف الغرض');
        END IF;

        IF target_rec.distinguishing_marks IS NOT NULL
           AND candidate_rec.distinguishing_marks IS NOT NULL
           AND char_length(trim(target_rec.distinguishing_marks)) >= 3
           AND position(lower(split_part(trim(target_rec.distinguishing_marks), ' ', 1)) in lower(candidate_rec.distinguishing_marks || ' ' || candidate_rec.description)) > 0 THEN
            score := score + 8.0;
            reasons := array_append(reasons, 'تطابق في العلامات المميزة');
        END IF;

        IF score >= 50.0 THEN
            IF target_rec.report_type = 'lost' THEN
                lost_id := target_rec.id;
                found_id := candidate_rec.id;
                notify_user_id := target_rec.user_id;
            ELSE
                lost_id := candidate_rec.id;
                found_id := target_rec.id;
                notify_user_id := candidate_rec.user_id;
            END IF;

            INSERT INTO public.report_matches (lost_report_id, found_report_id, similarity_score, match_reasons, review_status)
            VALUES (lost_id, found_id, LEAST(score, 98.0), reasons, 'pending')
            ON CONFLICT (lost_report_id, found_report_id)
            DO UPDATE SET
                similarity_score = EXCLUDED.similarity_score,
                match_reasons = EXCLUDED.match_reasons,
                updated_at = NOW()
            WHERE public.report_matches.review_status = 'pending';

            INSERT INTO public.notifications (user_id, notification_type, title, body, related_report_id)
            VALUES (
                notify_user_id,
                'similar_report',
                'قد يكون هذا الغرض هو ما تبحث عنه',
                'عثرنا على بلاغ مشابه لبلاغك في حمص بنسبة تشابه ' || ROUND(LEAST(score, 98.0)) || '%. تفقّد التفاصيل الآن.',
                found_id
            );

            inserted_count := inserted_count + 1;
        END IF;
    END LOOP;

    RETURN inserted_count;
END;
$$;

-- -----------------------------------------------------------------------------
-- 7. إنشاء ملف المستخدم تلقائياً عند التسجيل في Supabase Auth
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (id, full_name, city)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'full_name', split_part(NEW.email, '@', 1), 'مستخدم وينها'),
        'حمص'
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_auth_user();
