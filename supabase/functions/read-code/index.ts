import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const PUBLISHABLE_KEY = projectKey("SUPABASE_PUBLISHABLE_KEYS", "SUPABASE_ANON_KEY");
const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
const GEMINI_MODEL = Deno.env.get("GEMINI_MODEL") ?? "gemini-3.8-flash";

const MAX_IMAGES = 4;
const MAX_BASE64_PER_IMAGE = 8_000_000;
const MAX_BASE64_TOTAL = 16_000_000;
const MIME_TYPES = ["image/jpeg", "image/png", "image/webp"];

const SYSTEM_PROMPT = `You transcribe photos of Python source code (screens, printouts or handwriting) into text.

You may receive several photos of the same file, in order. Transcribe each photo separately
and return one page per photo, in the same order. Photos often overlap by a line or two;
copy each photo completely anyway, including the overlapping lines.

Copy exactly what is written, including mistakes and typos. Never correct, complete or
reformat the code: the developer's own mistakes are what will be reviewed next.
Keep indentation as spaces, measured from the left-most line of code in that photo, and
keep blank lines that separate code. Leave out editor line numbers, menus, tabs and
anything else that isn't code.

Mark a line uncertain when any character in it is hard to read, or when the line is cut
off at the top or bottom edge of the photo; still give your best reading of it.
If a photo contains no code, return an empty list of lines for it.`;

const LINES = {
  type: "array",
  items: {
    type: "object",
    properties: { text: { type: "string" }, uncertain: { type: "boolean" } },
    required: ["text", "uncertain"],
  },
};
const SCHEMA = {
  type: "object",
  properties: {
    pages: { type: "array", items: { type: "object", properties: { lines: LINES }, required: ["lines"] } },
  },
  required: ["pages"],
};

interface Photo {
  data: string;
  mimeType: string;
}

interface Line {
  text: string;
  uncertain: boolean;
}

class RateLimited extends Error {
  constructor(public scope: "day" | "minute") {
    super("rate limited");
  }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Use POST." });

  const authorization = req.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return json(401, { error: "Sign in first." });
  const asUser = createClient(SUPABASE_URL, PUBLISHABLE_KEY, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false },
  });
  const { data: userData, error: userError } = await asUser.auth.getUser(authorization.slice(7));
  if (userError || !userData.user) return json(401, { error: "You've been signed out. Please sign in again." });

  if (!GEMINI_API_KEY) {
    return json(503, { error: "not_configured", message: "The photo reader isn't available right now." });
  }

  let photos: Photo[];
  let singlePhotoRequest = false;
  try {
    const body = await req.json();
    if (Array.isArray(body.images)) {
      photos = body.images.map((i: { image_base64?: unknown; mime_type?: unknown }) => ({
        data: String(i.image_base64 ?? ""),
        mimeType: String(i.mime_type ?? "image/jpeg"),
      }));
    } else {
      singlePhotoRequest = true;
      photos = [{ data: String(body.image_base64 ?? ""), mimeType: String(body.mime_type ?? "image/jpeg") }];
    }
  } catch {
    return json(400, { error: "Send JSON with an images list." });
  }
  if (photos.length === 0 || photos.length > MAX_IMAGES) {
    return json(400, { error: `Send between 1 and ${MAX_IMAGES} photos at a time.` });
  }
  if (photos.some((p) => !p.data || p.data.length > MAX_BASE64_PER_IMAGE) ||
    photos.reduce((sum, p) => sum + p.data.length, 0) > MAX_BASE64_TOTAL) {
    return json(413, { error: "Those photos are too big. Try fewer or smaller photos." });
  }
  if (photos.some((p) => !MIME_TYPES.includes(p.mimeType))) {
    return json(415, { error: "Only JPEG, PNG or WebP photos can be read." });
  }

  try {
    let pages = await readPhotos(photos);
    if (pages.length !== photos.length) {
      pages = [];
      for (const photo of photos) pages.push((await readPhotos([photo]))[0] ?? []);
    }
    return singlePhotoRequest ? json(200, { lines: pages[0] }) : json(200, { pages: pages.map((lines) => ({ lines })) });
  } catch (e) {
    if (e instanceof RateLimited) return rateLimitedResponse(e.scope);
    console.error("read-code failed", (e as Error).message);
    return json(502, { error: "Couldn't read those photos. Please try again in a moment." });
  }
});

async function readPhotos(photos: Photo[]): Promise<Line[][]> {
  const parts: unknown[] = [];
  photos.forEach((photo, i) => {
    parts.push({ text: `Photo ${i + 1} of ${photos.length}:` });
    parts.push({ inlineData: { mimeType: photo.mimeType, data: photo.data } });
  });
  parts.push({ text: `Transcribe the Python code in each of the ${photos.length} photo(s).` });

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": GEMINI_API_KEY },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents: [{ role: "user", parts }],
        generationConfig: {
          responseMimeType: "application/json",
          responseJsonSchema: SCHEMA,
          thinkingConfig: { thinkingLevel: "low" },
          maxOutputTokens: 32000,
        },
      }),
    },
  );
  const body = await response.json();
  if (response.status === 429) throw new RateLimited(isDailyLimit(body) ? "day" : "minute");
  if (!response.ok) throw new Error(`Gemini ${response.status}: ${body?.error?.message ?? "unknown error"}`);
  const candidate = body.candidates?.[0];
  if (!candidate || candidate.finishReason !== "STOP") {
    throw new Error(`Gemini stopped early: ${candidate?.finishReason ?? body.promptFeedback?.blockReason ?? "no answer"}`);
  }
  const text = (candidate.content?.parts ?? [])
    .filter((p: { thought?: boolean }) => !p.thought)
    .map((p: { text?: string }) => p.text ?? "")
    .join("");
  const parsed = JSON.parse(text);
  return (Array.isArray(parsed.pages) ? parsed.pages : []).map((page: { lines?: unknown }) =>
    (Array.isArray(page?.lines) ? page.lines : [])
      .slice(0, 2000)
      .map((l: { text?: unknown; uncertain?: unknown }) => ({
        text: String(l.text ?? "").replaceAll("\t", "    ").trimEnd(),
        uncertain: l.uncertain === true,
      }))
  );
}

function isDailyLimit(body: { error?: { message?: string; details?: unknown } }): boolean {
  return /per\s*day|PerDay/i.test(JSON.stringify(body?.error ?? {}));
}

function rateLimitedResponse(scope: "day" | "minute"): Response {
  return json(429, {
    error: "rate_limited",
    scope,
    message: scope === "day"
      ? "We've hit today's AI limit for now. It resets every morning around 9 or 10am, so please try again then."
      : "The AI is busy. Give it a minute and try again.",
  });
}

function projectKey(jsonVar: string, legacyVar: string): string {
  const raw = Deno.env.get(jsonVar);
  if (raw) {
    try {
      return JSON.parse(raw)["default"];
    } catch {
    }
  }
  return Deno.env.get(legacyVar) ?? "";
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
