-- =============================================================================
-- مشروع WAYNHA (وينها؟) - المرحلة 3: سياسات أمان الصفوف (Row Level Security - RLS)
-- ترتيب التنفيذ: شغّل هذا الملف ثالثاً (03_rls_policies.sql)
-- =============================================================================

-- تفعيل RLS على جميع الجداول دون استثناء
ALTER TABLE public.system_cities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.report_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.report_matches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saved_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recovery_confirmations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_audit_logs ENABLE ROW LEVEL SECURITY;

-- -----------------------------------------------------------------------------
-- 0. سياسات system_cities و categories
-- -----------------------------------------------------------------------------
CREATE POLICY "cities_select_enabled" ON public.system_cities
    FOR SELECT USING (is_enabled = TRUE OR public.is_admin());

CREATE POLICY "cities_admin_manage" ON public.system_cities
    FOR ALL USING (public.is_admin()) WITH CHECK (public.is_admin());

CREATE POLICY "categories_select_active" ON public.categories
    FOR SELECT USING (is_active = TRUE OR public.is_admin());

CREATE POLICY "categories_admin_manage" ON public.categories
    FOR ALL USING (public.is_admin()) WITH CHECK (public.is_admin());

-- -----------------------------------------------------------------------------
-- 1. سياسات profiles (حماية بيانات المستخدمين ومنع التعديل غير المصرح)
-- -----------------------------------------------------------------------------
CREATE POLICY "profiles_select_authenticated" ON public.profiles
    FOR SELECT TO authenticated
    USING (is_suspended = FALSE OR id = auth.uid() OR public.is_admin());

CREATE POLICY "profiles_insert_own" ON public.profiles
    FOR INSERT TO authenticated
    WITH CHECK (id = auth.uid() AND city = 'حمص' AND role = 'user');

CREATE POLICY "profiles_update_own_or_admin" ON public.profiles
    FOR UPDATE TO authenticated
    USING (id = auth.uid() OR public.is_admin())
    WITH CHECK (id = auth.uid() OR public.is_admin());

-- -----------------------------------------------------------------------------
-- 2. سياسات reports (قراءة البلاغات العامة في حمص، وتعديل/حذف بلاغات المالك فقط)
-- -----------------------------------------------------------------------------
CREATE POLICY "reports_select_homs_visible" ON public.reports
    FOR SELECT
    USING (
        (city = 'حمص' AND status IN ('open', 'matched', 'recovered', 'closed'))
        OR user_id = auth.uid()
        OR public.is_admin()
    );

CREATE POLICY "reports_insert_own_homs" ON public.reports
    FOR INSERT TO authenticated
    WITH CHECK (
        user_id = auth.uid()
        AND city = 'حمص'
    );

CREATE POLICY "reports_update_own_or_admin" ON public.reports
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid() OR public.is_admin())
    WITH CHECK (
        (user_id = auth.uid() AND city = 'حمص')
        OR public.is_admin()
    );

CREATE POLICY "reports_delete_own_or_admin" ON public.reports
    FOR DELETE TO authenticated
    USING (user_id = auth.uid() OR public.is_admin());

-- -----------------------------------------------------------------------------
-- 3. سياسات report_images
-- -----------------------------------------------------------------------------
CREATE POLICY "report_images_select_visible" ON public.report_images
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.reports r
            WHERE r.id = report_images.report_id
              AND (r.status <> 'hidden' OR r.user_id = auth.uid() OR public.is_admin())
        )
    );

CREATE POLICY "report_images_insert_owner" ON public.report_images
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.reports r
            WHERE r.id = report_images.report_id
              AND r.user_id = auth.uid()
        )
    );

CREATE POLICY "report_images_delete_owner_or_admin" ON public.report_images
    FOR DELETE TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.reports r
            WHERE r.id = report_images.report_id
              AND (r.user_id = auth.uid() OR public.is_admin())
        )
    );

-- -----------------------------------------------------------------------------
-- 4. سياسات report_matches (يقرأها فقط أصحاب البلاغين المتطابقين أو الإدارة)
-- -----------------------------------------------------------------------------
CREATE POLICY "report_matches_select_involved" ON public.report_matches
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.reports r
            WHERE (r.id = report_matches.lost_report_id OR r.id = report_matches.found_report_id)
              AND r.user_id = auth.uid()
        )
        OR public.is_admin()
    );

CREATE POLICY "report_matches_update_involved" ON public.report_matches
    FOR UPDATE TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.reports r
            WHERE (r.id = report_matches.lost_report_id OR r.id = report_matches.found_report_id)
              AND r.user_id = auth.uid()
        )
        OR public.is_admin()
    );

-- -----------------------------------------------------------------------------
-- 5. سياسات conversations و conversation_members و messages (للمشاركين حصراً)
-- -----------------------------------------------------------------------------
CREATE POLICY "conversations_select_participants_only" ON public.conversations
    FOR SELECT TO authenticated
    USING (initiator_id = auth.uid() OR recipient_id = auth.uid() OR public.is_admin());

CREATE POLICY "conversations_insert_initiator" ON public.conversations
    FOR INSERT TO authenticated
    WITH CHECK (
        initiator_id = auth.uid()
        AND initiator_id <> recipient_id
        AND EXISTS (
            SELECT 1 FROM public.reports r
            WHERE r.id = conversations.report_id
              AND r.status IN ('open', 'matched')
              AND r.city = 'حمص'
        )
    );

CREATE POLICY "conversations_update_participants" ON public.conversations
    FOR UPDATE TO authenticated
    USING (initiator_id = auth.uid() OR recipient_id = auth.uid() OR public.is_admin());

CREATE POLICY "conversation_members_select_own" ON public.conversation_members
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR public.is_conversation_member(conversation_id)
        OR public.is_admin()
    );

CREATE POLICY "conversation_members_insert_valid" ON public.conversation_members
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = conversation_members.conversation_id
              AND (c.initiator_id = auth.uid() OR c.recipient_id = auth.uid())
              AND (conversation_members.user_id = c.initiator_id OR conversation_members.user_id = c.recipient_id)
        )
    );

CREATE POLICY "messages_select_members_only" ON public.messages
    FOR SELECT TO authenticated
    USING (public.is_conversation_member(conversation_id));

CREATE POLICY "messages_insert_members_only" ON public.messages
    FOR INSERT TO authenticated
    WITH CHECK (
        sender_id = auth.uid()
        AND public.is_conversation_member(conversation_id)
    );

CREATE POLICY "messages_update_read_status" ON public.messages
    FOR UPDATE TO authenticated
    USING (public.is_conversation_member(conversation_id));

-- -----------------------------------------------------------------------------
-- 6. سياسات notifications (صاحب الإشعار فقط)
-- -----------------------------------------------------------------------------
CREATE POLICY "notifications_select_own" ON public.notifications
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY "notifications_update_own" ON public.notifications
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- -----------------------------------------------------------------------------
-- 7. سياسات saved_reports (المستخدم نفسه فقط)
-- -----------------------------------------------------------------------------
CREATE POLICY "saved_reports_manage_own" ON public.saved_reports
    FOR ALL TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- -----------------------------------------------------------------------------
-- 8. سياسات reports_flags (إنشاء بلاغ مخالفة للمستخدم، ومراجعته للمدير فقط)
-- -----------------------------------------------------------------------------
CREATE POLICY "reports_flags_insert_authenticated" ON public.reports_flags
    FOR INSERT TO authenticated
    WITH CHECK (reporter_id = auth.uid());

CREATE POLICY "reports_flags_select_own_or_admin" ON public.reports_flags
    FOR SELECT TO authenticated
    USING (reporter_id = auth.uid() OR public.is_admin());

CREATE POLICY "reports_flags_update_admin_only" ON public.reports_flags
    FOR UPDATE TO authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- -----------------------------------------------------------------------------
-- 9. سياسات user_blocks
-- -----------------------------------------------------------------------------
CREATE POLICY "user_blocks_manage_own" ON public.user_blocks
    FOR ALL TO authenticated
    USING (blocker_id = auth.uid())
    WITH CHECK (blocker_id = auth.uid());

-- -----------------------------------------------------------------------------
-- 10. سياسات recovery_confirmations
-- -----------------------------------------------------------------------------
CREATE POLICY "recovery_select_involved" ON public.recovery_confirmations
    FOR SELECT TO authenticated
    USING (owner_user_id = auth.uid() OR finder_user_id = auth.uid() OR public.is_admin());

CREATE POLICY "recovery_insert_involved" ON public.recovery_confirmations
    FOR INSERT TO authenticated
    WITH CHECK (owner_user_id = auth.uid() OR finder_user_id = auth.uid());

CREATE POLICY "recovery_update_involved" ON public.recovery_confirmations
    FOR UPDATE TO authenticated
    USING (owner_user_id = auth.uid() OR finder_user_id = auth.uid() OR public.is_admin());

-- -----------------------------------------------------------------------------
-- 11. سياسات admin_audit_logs (للمديرين حصراً)
-- -----------------------------------------------------------------------------
CREATE POLICY "audit_logs_admin_only" ON public.admin_audit_logs
    FOR ALL TO authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin() AND admin_id = auth.uid());

-- -----------------------------------------------------------------------------
-- 12. إعداد حاويات التخزين (Supabase Storage Buckets: report-images)
-- -----------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public)
VALUES ('report-images', 'report-images', TRUE)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "storage_report_images_public_read" ON storage.objects
    FOR SELECT USING (bucket_id = 'report-images');

CREATE POLICY "storage_report_images_auth_upload" ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'report-images'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "storage_report_images_owner_delete" ON storage.objects
    FOR DELETE TO authenticated
    USING (
        bucket_id = 'report-images'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );
