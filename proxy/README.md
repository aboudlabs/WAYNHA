# WAYNHA API Proxy

This small Node.js service forwards only WAYNHA's required Supabase API routes through a Render-hosted HTTPS endpoint. It does not use a Supabase service-role key.

## Deploy on Render

1. Create a **Web Service** and select the `aboudlabs/WAYNHA` repository.
2. Set **Root Directory** to `proxy`.
3. Set **Runtime** to Node.
4. Set **Build Command** to `npm install`.
5. Set **Start Command** to `npm start`.
6. Add environment variables:
   - `UPSTREAM_SUPABASE_URL`: the Supabase project URL, for example `https://YOUR_PROJECT_REF.supabase.co`.
   - `SUPABASE_ANON_KEY`: the project's publishable/anon key only. Never use a service-role key.
7. Deploy and check `https://YOUR-RENDER-SERVICE.onrender.com/health`. It should return `{"ok":true,"service":"waynha-api-proxy"}`.

After deployment, configure the Android app's `SUPABASE_URL` build value to the Render service base URL, while keeping `SUPABASE_ANON_KEY` as the publishable/anon key. Rebuild and install the APK.

## Notes

- The proxy has a fixed upstream host and only forwards Supabase Auth, REST, Storage, and Functions routes.
- Supabase Row Level Security remains responsible for user data authorization.
- Render's free service may sleep when idle, causing a delay on the first request.
- A proxy helps only if the Render service is reachable from the affected network and the upstream Supabase project is reachable from Render.
