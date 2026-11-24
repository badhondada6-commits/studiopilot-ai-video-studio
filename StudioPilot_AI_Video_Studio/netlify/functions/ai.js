exports.handler = async (event) => {
  const headers = { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" };
  if (event.httpMethod === "OPTIONS") return { statusCode: 204, headers };
  if (event.httpMethod !== "POST") return { statusCode: 405, headers, body: JSON.stringify({ error: "Method not allowed" }) };
  const key = process.env.OPENROUTER_API_KEY;
  if (!key) return { statusCode: 503, headers, body: JSON.stringify({ error: "OPENROUTER_API_KEY is not configured on Netlify" }) };
  try {
    const body = JSON.parse(event.body || "{}");
    if (!body.prompt || typeof body.prompt !== "string") return { statusCode: 400, headers, body: JSON.stringify({ error: "prompt is required" }) };
    const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: { "Authorization": `Bearer ${key}`, "Content-Type": "application/json", "HTTP-Referer": process.env.SITE_URL || "https://netlify.app", "X-Title": "StudioPilot AI Video Studio" },
      body: JSON.stringify({ model: process.env.OPENROUTER_MODEL || body.model || "openrouter/free", messages: [{ role: "system", content: body.system || "Be accurate. Do not invent facts." }, { role: "user", content: body.prompt }], temperature: 0.7 })
    });
    const data = await response.json();
    if (!response.ok) return { statusCode: response.status, headers, body: JSON.stringify({ error: data.error?.message || "AI provider request failed" }) };
    return { statusCode: 200, headers, body: JSON.stringify({ text: data.choices?.[0]?.message?.content || "" }) };
  } catch (e) {
    return { statusCode: 500, headers, body: JSON.stringify({ error: "Server error", detail: e.message }) };
  }
};