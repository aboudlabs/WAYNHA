"use strict";

const http = require("node:http");
const https = require("node:https");
const { URL } = require("node:url");

const PORT = Number(process.env.PORT || 3000);
const UPSTREAM_BASE = (process.env.UPSTREAM_SUPABASE_URL || "").trim().replace(/\/+$/, "");
const ANON_KEY = (process.env.SUPABASE_ANON_KEY || "").trim();

if (!UPSTREAM_BASE || !ANON_KEY) {
  console.error("Missing UPSTREAM_SUPABASE_URL or SUPABASE_ANON_KEY environment variable.");
  process.exit(1);
}

const allowedPrefixes = ["/auth/v1/", "/rest/v1/", "/storage/v1/", "/functions/v1/"];

function send(res, status, body, contentType = "application/json; charset=utf-8") {
  res.writeHead(status, {
    "content-type": contentType,
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  res.end(body);
}

const server = http.createServer((req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    return send(res, 200, JSON.stringify({ ok: true, service: "waynha-api-proxy" }));
  }

  const incoming = new URL(req.url || "/", "http://localhost");
  if (!allowedPrefixes.some((prefix) => incoming.pathname.startsWith(prefix))) {
    return send(res, 404, JSON.stringify({ error: "Not found" }));
  }

  let upstream;
  try {
    upstream = new URL(incoming.pathname + incoming.search, UPSTREAM_BASE);
  } catch {
    return send(res, 400, JSON.stringify({ error: "Invalid request URL" }));
  }

  const transport = upstream.protocol === "https:" ? https : http;
  const headers = { ...req.headers };
  delete headers.host;
  delete headers.connection;
  delete headers["content-length"];
  headers.host = upstream.host;
  headers["accept-encoding"] = "identity";
  headers.apikey = headers.apikey || ANON_KEY;
  headers["x-forwarded-host"] = req.headers.host || "";
  headers["x-forwarded-proto"] = "https";

  const options = {
    protocol: upstream.protocol,
    hostname: upstream.hostname,
    port: upstream.port || (upstream.protocol === "https:" ? 443 : 80),
    method: req.method,
    path: upstream.pathname + upstream.search,
    headers
  };

  const upstreamReq = transport.request(options, (upstreamRes) => {
    const responseHeaders = { ...upstreamRes.headers };
    delete responseHeaders.connection;
    delete responseHeaders["transfer-encoding"];
    delete responseHeaders["content-length"];
    delete responseHeaders["content-encoding"];
    responseHeaders["cache-control"] = responseHeaders["cache-control"] || "no-store";
    responseHeaders["x-content-type-options"] = "nosniff";

    const contentType = String(responseHeaders["content-type"] || "");
    const shouldRewrite = /json|text\/|javascript|xml/i.test(contentType);

    if (!shouldRewrite) {
      res.writeHead(upstreamRes.statusCode || 502, responseHeaders);
      upstreamRes.pipe(res);
      return;
    }

    const chunks = [];
    upstreamRes.on("data", (chunk) => chunks.push(chunk));
    upstreamRes.on("end", () => {
      let body = Buffer.concat(chunks).toString("utf8");
      const publicBase = `${req.headers["x-forwarded-proto"] === "http" ? "http" : "https"}://${req.headers.host || ""}`;
      body = body.split(UPSTREAM_BASE).join(publicBase);
      res.writeHead(upstreamRes.statusCode || 502, responseHeaders);
      res.end(body);
    });
  });

  upstreamReq.on("error", (error) => {
    console.error("Upstream request failed:", error.message);
    if (!res.headersSent) {
      send(res, 502, JSON.stringify({ error: "تعذر الاتصال بالخدمة حالياً. حاول مرة أخرى." }));
    } else {
      res.destroy(error);
    }
  });

  req.on("aborted", () => upstreamReq.destroy());
  req.pipe(upstreamReq);
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`WAYNHA proxy listening on port ${PORT}`);
});