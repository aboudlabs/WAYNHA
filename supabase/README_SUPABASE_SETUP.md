# دليل إعداد وربط Supabase لتطبيق WAYNHA (وينها؟)

يحتوي هذا المجلد على ملفات قاعدة البيانات الخلفية الكاملة لتطبيق **WAYNHA (وينها؟)** المخصص لمدينة **حمص، سوريا**.

---

## 1. ترتيب تنفيذ ملفات SQL في Supabase

1. أنشئ مشروعاً جديداً في [Supabase Dashboard](https://supabase.com/dashboard).
2. افتح قسم **SQL Editor** من القائمة الجانبية.
3. نفّذ الملفات بالترتيب التالي تماماً:
   * **أولاً:** `01_schema_and_tables.sql`
     *(ينشئ جدول إعدادات المدن `system_cities` مع تفعيل مدينة حمص فقط، وجدول التصنيفات `categories`، والجداول الـ 12 الأساسية + جدول التدقيق الإداري `admin_audit_logs` مع الفهارس والقيود).*
   * **ثانياً:** `02_functions_and_triggers.sql`
     *(ينشئ دوال التحقق الخادمي من مدينة حمص، ومنع نشر أرقام الهواتف علناً، ومنع ترقية الصلاحيات الذاتية، وإيقاف المطابقات للبلاغات المغلقة، وخوارزمية المطابقة `compute_report_matches`).*
   * **ثالثاً:** `03_rls_policies.sql`
     *(يفعّل Row Level Security على جميع الجداول وينشئ سياسات الصلاحيات الدقيقة وحاوية التخزين `report-images`).*

---

## 2. تفعيل Realtime للمحادثات والإشعارات

من لوحة تحكم Supabase:
1. انتقل إلى **Database -> Replication**.
2. فعّل النشر الفوري (`supabase_realtime`) للجداول التالية:
   * `public.messages`
   * `public.notifications`
   * `public.conversations`

---

## 3. إضافة المفاتيح الآمنة في التطبيق (دون كشف الأسرار)

1. من لوحة تحكم Supabase، انتقل إلى **Project Settings -> API**.
2. انسخ القيمتين التاليتين فقط:
   * `Project URL` -> يوضع في المتغير `SUPABASE_URL`
   * `anon public` key -> يوضع في المتغير `SUPABASE_ANON_KEY`
3. **تنبيه أمني صارم:** لا تضع مفتاح `service_role` السري داخل تطبيق الموبايل أبداً.
4. في بيئة **Google AI Studio**:
   * افتح لوحة الأسرار (**Secrets Panel**) وأضف:
     * `SUPABASE_URL`
     * `SUPABASE_ANON_KEY`
   * يقوم التطبيق بقراءتها تلقائياً عبر `BuildConfig.SUPABASE_URL` و`BuildConfig.SUPABASE_ANON_KEY`.

---

## 4. تعيين حساب مدير (Admin) بأمان

لا يسمح التطبيق لأي مستخدم بتعيين نفسه مديراً من الواجهة. لتعيين حسابك مديراً، شغّل الأمر التالي من داخل **Supabase SQL Editor** فقط:

```sql
UPDATE public.profiles
SET role = 'admin'
WHERE id = 'ضع-معرف-المستخدم-UUID-هنا';
```
