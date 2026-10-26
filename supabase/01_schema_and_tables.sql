-- =============================================================================
-- مشروع WAYNHA (وينها؟) - المرحلة 1: الجداول والقيود والفهارس
-- ترتيب التنفيذ: شغّل هذا الملف أولاً (01_schema_and_tables.sql) في Supabase SQL Editor
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- 0. جدول إعدادات المدن المدعومة (لدعم التوسع المستقبلي دون إعادة بناء التطبيق)
-- في الإصدار الأول: حمص فقط هي المفعّلة (is_enabled = true)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.system_cities (
    city_code TEXT PRIMARY KEY,
    name_ar TEXT NOT NULL UNIQUE,
    country_ar TEXT NOT NULL DEFAULT 'سوريا',
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    neighborhoods TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.system_cities (city_code, name_ar, country_ar, is_enabled, is_default, neighborhoods)
VALUES (
    'HOMS',
    'حمص',
    'سوريا',
    TRUE,
    TRUE,
    ARRAY[
        'الإنشاءات', 'الحمراء', 'الغوطة', 'الوعر', 'عكرمة', 'الزهراء',
        'باب السباع', 'باب هود', 'بستان الديوان', 'الحميدية', 'الخالدية',
        'البياضة', 'دير بعلبة', 'كرم الشامي', 'كرم اللوز', 'وادي الذهب',
        'القصور', 'المحطة', 'طريق الشام', 'جامعة البعث', 'السوق المسقوف', 'مركز المدينة'
    ]
)
ON CONFLICT (city_code) DO UPDATE
SET is_enabled = TRUE, is_default = TRUE, neighborhoods = EXCLUDED.neighborhoods;

-- -----------------------------------------------------------------------------
-- 0.1 جدول تصنيفات المفقودات والموجودات (قابل للإدارة من لوحة الإدارة)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.categories (
    id TEXT PRIMARY KEY,
    name_ar TEXT NOT NULL UNIQUE,
    icon_name TEXT NOT NULL DEFAULT 'category',
    is_sensitive_document BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.categories (id, name_ar, icon_name, is_sensitive_document, sort_order) VALUES
    ('electronics', 'هواتف وأجهزة إلكترونية', 'smartphone', FALSE, 1),
    ('wallets', 'محافظ ومبالغ نقدية', 'account_balance_wallet', FALSE, 2),
    ('keys', 'مفاتيح', 'key', FALSE, 3),
    ('bags', 'حقائب', 'work', FALSE, 4),
    ('documents', 'وثائق وأوراق', 'badge', TRUE, 5),
    ('jewelry', 'مجوهرات وإكسسوارات', 'diamond', FALSE, 6),
    ('pets', 'حيوانات أليفة', 'pets', FALSE, 7),
    ('other', 'أغراض أخرى', 'category', FALSE, 8)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 1. جدول profiles: ملفات المستخدمين
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL CHECK (char_length(trim(full_name)) >= 2),
    avatar_url TEXT,
    city TEXT NOT NULL DEFAULT 'حمص' CHECK (city = 'حمص'),
    neighborhood TEXT,
    role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'admin', 'moderator')),
    is_suspended BOOLEAN NOT NULL DEFAULT FALSE,
    privacy_hide_last_seen BOOLEAN NOT NULL DEFAULT FALSE,
    allow_match_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    deletion_requested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_profiles_city ON public.profiles(city);
CREATE INDEX IF NOT EXISTS idx_profiles_role ON public.profiles(role);

-- -----------------------------------------------------------------------------
-- 2. جدول reports: البلاغات (مفقود / موجود داخل حمص حصراً)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    report_type TEXT NOT NULL CHECK (report_type IN ('lost', 'found')),
    title TEXT NOT NULL CHECK (char_length(trim(title)) BETWEEN 3 AND 120),
    category_id TEXT NOT NULL REFERENCES public.categories(id),
    description TEXT NOT NULL CHECK (char_length(trim(description)) BETWEEN 10 AND 2000),
    city TEXT NOT NULL DEFAULT 'حمص' CHECK (city = 'حمص'),
    neighborhood TEXT, -- المنطقة أو الحي داخل حمص (حقل منفصل اختياري دون إحداثيات دقيقة)
    incident_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    distinguishing_marks TEXT,
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'matched', 'recovered', 'closed', 'hidden')),
    is_sensitive_masked BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reports_user_id ON public.reports(user_id);
CREATE INDEX IF NOT EXISTS idx_reports_type_status ON public.reports(report_type, status);
CREATE INDEX IF NOT EXISTS idx_reports_category ON public.reports(category_id);
CREATE INDEX IF NOT EXISTS idx_reports_neighborhood ON public.reports(neighborhood);
CREATE INDEX IF NOT EXISTS idx_reports_created_at ON public.reports(created_at DESC);

-- -----------------------------------------------------------------------------
-- 3. جدول report_images: صور البلاغات
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.report_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES public.reports(id) ON DELETE CASCADE,
    storage_path TEXT NOT NULL,
    public_url TEXT NOT NULL,
    is_blurred_for_privacy BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_report_images_report_id ON public.report_images(report_id);

-- -----------------------------------------------------------------------------
-- 4. جدول report_matches: اقتراحات المطابقة بين المفقودات والموجودات
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.report_matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lost_report_id UUID NOT NULL REFERENCES public.reports(id) ON DELETE CASCADE,
    found_report_id UUID NOT NULL REFERENCES public.reports(id) ON DELETE CASCADE,
    similarity_score NUMERIC(5,2) NOT NULL CHECK (similarity_score BETWEEN 0 AND 100),
    match_reasons TEXT[] NOT NULL DEFAULT '{}',
    review_status TEXT NOT NULL DEFAULT 'pending' CHECK (review_status IN ('pending', 'contacted', 'dismissed', 'confirmed', 'archived')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_report_match_pair UNIQUE (lost_report_id, found_report_id),
    CONSTRAINT chk_different_reports CHECK (lost_report_id <> found_report_id)
);

CREATE INDEX IF NOT EXISTS idx_report_matches_lost ON public.report_matches(lost_report_id, similarity_score DESC);
CREATE INDEX IF NOT EXISTS idx_report_matches_found ON public.report_matches(found_report_id, similarity_score DESC);

-- -----------------------------------------------------------------------------
-- 5. جدول conversations: المحادثات المرتبطة ببلاغ محدد
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES public.reports(id) ON DELETE CASCADE,
    matched_report_id UUID REFERENCES public.reports(id) ON DELETE SET NULL,
    initiator_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'closed', 'blocked')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_conversation_per_report_initiator UNIQUE (report_id, initiator_id),
    CONSTRAINT chk_conversation_distinct_users CHECK (initiator_id <> recipient_id)
);

CREATE INDEX IF NOT EXISTS idx_conversations_report_id ON public.conversations(report_id);
CREATE INDEX IF NOT EXISTS idx_conversations_updated_at ON public.conversations(updated_at DESC);

-- -----------------------------------------------------------------------------
-- 6. جدول conversation_members: أعضاء المحادثات
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.conversation_members (
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'participant' CHECK (role IN ('initiator', 'owner', 'participant')),
    last_read_at TIMESTAMPTZ,
    is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_conversation_members_user ON public.conversation_members(user_id);

-- -----------------------------------------------------------------------------
-- 7. جدول messages: الرسائل داخل المحادثة
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    body TEXT NOT NULL CHECK (char_length(trim(body)) BETWEEN 1 AND 1500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON public.messages(conversation_id, created_at ASC);

-- -----------------------------------------------------------------------------
-- 8. جدول notifications: الإشعارات داخل التطبيق
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    notification_type TEXT NOT NULL CHECK (notification_type IN ('new_message', 'similar_report', 'report_status', 'recovery_update')),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    related_report_id UUID REFERENCES public.reports(id) ON DELETE SET NULL,
    related_conversation_id UUID REFERENCES public.conversations(id) ON DELETE SET NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON public.notifications(user_id, is_read, created_at DESC);

-- -----------------------------------------------------------------------------
-- 9. جدول saved_reports: البلاغات المحفوظة للمستخدم
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.saved_reports (
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    report_id UUID NOT NULL REFERENCES public.reports(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, report_id)
);

CREATE INDEX IF NOT EXISTS idx_saved_reports_user ON public.saved_reports(user_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 10. جدول reports_flags: البلاغات عن المحتوى المخالف أو الإساءة
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.reports_flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID REFERENCES public.reports(id) ON DELETE CASCADE,
    conversation_id UUID REFERENCES public.conversations(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reason TEXT NOT NULL CHECK (char_length(trim(reason)) BETWEEN 3 AND 500),
    details TEXT,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'reviewed', 'action_taken', 'dismissed')),
    reviewed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reports_flags_status ON public.reports_flags(status, created_at DESC);

-- -----------------------------------------------------------------------------
-- 11. جدول user_blocks: حظر المستخدمين
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.user_blocks (
    blocker_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT chk_cannot_block_self CHECK (blocker_id <> blocked_id)
);

-- -----------------------------------------------------------------------------
-- 12. جدول recovery_confirmations: تأكيد استعادة الأغراض
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.recovery_confirmations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES public.reports(id) ON DELETE CASCADE,
    matched_report_id UUID REFERENCES public.reports(id) ON DELETE SET NULL,
    owner_user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    finder_user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    owner_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    finder_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    handover_notes TEXT,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'confirmed', 'cancelled')),
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_recovery_report_id ON public.recovery_confirmations(report_id);

-- -----------------------------------------------------------------------------
-- 13. جدول admin_audit_logs: سجلات تدقيق للعمليات الإدارية الحساسة
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.admin_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    action_type TEXT NOT NULL,
    target_table TEXT NOT NULL,
    target_id TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_created ON public.admin_audit_logs(created_at DESC);
