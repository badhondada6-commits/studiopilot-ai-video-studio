const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const html = fs.readFileSync(path.join(__dirname, "..", "index.html"), "utf8");
const inlineScript = html.split("<script>")[1]?.split("</script>")[0];
assert.ok(inlineScript, "app inline script exists");
const competitorScript = fs.readFileSync(path.join(__dirname, "..", "competitor.js"), "utf8");

function getDeclaration(source, start, end) {
  const first = source.indexOf(start);
  const last = source.indexOf(end, first);
  assert.notEqual(first, -1, `missing declaration: ${start}`);
  assert.notEqual(last, -1, `missing end marker: ${end}`);
  return source.slice(first, last);
}

const aiFunction = getDeclaration(inlineScript, "async function ai(", "function localIdeas(");
const storyboardParser = getDeclaration(inlineScript, "function parseStoryboard(", '$("storyBtn").onclick');
const checklistRenderer = getDeclaration(inlineScript, "function renderChecklists(", "function toast(");
const channelReferenceParser = getDeclaration(competitorScript, "function parseYouTubeChannelReference(", "\nfunction countReferenceWords(");
const referenceWordCounter = getDeclaration(competitorScript, "function countReferenceWords(", "\nfunction parseYouTubeDuration(");
const youtubeDurationParser = getDeclaration(competitorScript, "function parseYouTubeDuration(", "\nfunction formatYouTubeDuration(");

function createHarness({ values, sessionKey = "", response }) {
  const requests = [];
  const elements = Object.fromEntries(Object.entries(values).map(([id, value]) => [id, { value }]));
  const context = {
    $: id => elements[id],
    openRouterApiKey: sessionKey,
    geminiApiKey: "",
    sessionStorage: { getItem: () => sessionKey },
    fetch: async (url, options) => {
      requests.push({ url, options });
      return response;
    }
  };
  vm.createContext(context);
  vm.runInContext(`${aiFunction}\nglobalThis.runAi = ai;`, context);
  return { context, requests, elements };
}

function createChecklistHarness(checklists = {}) {
  const controls = {
    script: Array.from({ length: 5 }, () => ({ checked: false })),
    production: Array.from({ length: 6 }, () => ({ checked: false })),
    publish: Array.from({ length: 6 }, () => ({ checked: false }))
  };
  const project = { checklists };
  let saveCount = 0;
  const context = {
    project,
    document: {
      querySelectorAll: selector => controls[selector.match(/#page-(script|production|publish)/)?.[1]] || []
    },
    save: () => { saveCount += 1; }
  };
  vm.createContext(context);
  vm.runInContext(`${checklistRenderer}\nglobalThis.render = renderChecklists;`, context);
  return { context, project, controls, saved: () => saveCount };
}

function okResponse(data) {
  return { ok: true, json: async () => data };
}

const localValues = {
  aiProvider: "ollama",
  ollamaUrl: "http://127.0.0.1:11434/",
  apiModel: "qwen2.5:3b",
  openRouterModel: "",
  geminiModel: "gemini-2.5-flash",
  apiEndpoint: "/.netlify/functions/ai"
};

test("sends Ollama requests to the configured local model", async () => {
  const harness = createHarness({
    values: localValues,
    response: okResponse({ message: { content: "local response" } })
  });

  assert.equal(await harness.context.runAi("hello", "system"), "local response");
  assert.equal(harness.requests[0].url, "http://127.0.0.1:11434/api/chat");
  assert.equal(JSON.parse(harness.requests[0].options.body).model, "qwen2.5:3b");
});

test("sends a tab-only OpenRouter key directly with the free router by default", async () => {
  const harness = createHarness({
    values: { ...localValues, aiProvider: "openrouter" },
    sessionKey: "user-key",
    response: okResponse({ choices: [{ message: { content: "hosted response" } }] })
  });

  assert.equal(await harness.context.runAi("hello"), "hosted response");
  assert.equal(harness.requests[0].url, "https://openrouter.ai/api/v1/chat/completions");
  assert.equal(harness.requests[0].options.headers.Authorization, "Bearer user-key");
  assert.equal(JSON.parse(harness.requests[0].options.body).model, "openrouter/free");
});

test("sends Gemini requests directly with the tab-only key and configured model", async () => {
  const harness = createHarness({
    values: { ...localValues, aiProvider: "gemini", geminiModel: "gemini-2.5-flash" },
    sessionKey: "gemini-user-key",
    response: okResponse({ candidates: [{ content: { parts: [{ text: "Gemini response" }] } }] })
  });

  assert.equal(await harness.context.runAi("hello", "system"), "Gemini response");
  assert.equal(harness.requests[0].url, "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent");
  assert.equal(harness.requests[0].options.headers["x-goog-api-key"], "gemini-user-key");
  assert.equal(harness.requests[0].options.headers.Authorization, undefined);
  assert.deepEqual(JSON.parse(harness.requests[0].options.body), {
    systemInstruction: { parts: [{ text: "system" }] },
    contents: [{ role: "user", parts: [{ text: "hello" }] }],
    generationConfig: { temperature: 0.7 }
  });
});

test("requires a Gemini API key instead of sending a keyless request", async () => {
  const harness = createHarness({
    values: { ...localValues, aiProvider: "gemini" },
    response: okResponse({ candidates: [{ content: { parts: [{ text: "unexpected" }] } }] })
  });

  await assert.rejects(harness.context.runAi("hello"), /Enter a Gemini API key/);
  assert.equal(harness.requests.length, 0);
});

test("uses the Netlify function when no browser key is provided", async () => {
  const harness = createHarness({
    values: { ...localValues, aiProvider: "openrouter", openRouterModel: "openrouter/free" },
    response: okResponse({ text: "server response" })
  });

  assert.equal(await harness.context.runAi("hello"), "server response");
  assert.equal(harness.requests[0].url, "/.netlify/functions/ai");
  assert.equal(JSON.parse(harness.requests[0].options.body).model, "openrouter/free");
  assert.equal(harness.requests[0].options.headers.Authorization, undefined);
});

test("surfaces provider errors instead of treating them as successful output", async () => {
  const harness = createHarness({
    values: localValues,
    response: { ok: false, status: 429, json: async () => ({ error: { message: "rate limited" } }) }
  });

  await assert.rejects(harness.context.runAi("hello"), /rate limited/);
});

test("parses structured storyboard scenes and rejects malformed output", () => {
  const context = {};
  vm.createContext(context);
  vm.runInContext(`${storyboardParser}\nglobalThis.parse = parseStoryboard;`, context);

  const scenes = context.parse('{"scenes":[{"narration":"line","visual":"shot","text":"caption","edit":"cut"}]}');
  assert.equal(JSON.stringify(scenes), JSON.stringify([
    { n: 1, narration: "line", visual: "shot", text: "caption", edit: "cut" }
  ]));
  assert.equal(context.parse("not json"), null);
});

test("accepts supported YouTube channel URLs and rejects unrelated URLs", () => {
  const context = { URL };
  vm.createContext(context);
  vm.runInContext(`${channelReferenceParser}\nglobalThis.parse = parseYouTubeChannelReference;`, context);

  assert.deepEqual(JSON.parse(JSON.stringify(context.parse("@example.creator"))), {
    parameter: "forHandle",
    value: "example.creator"
  });
  assert.deepEqual(JSON.parse(JSON.stringify(context.parse("https://www.youtube.com/@example?feature=shared"))), {
    parameter: "forHandle",
    value: "example"
  });
  assert.deepEqual(JSON.parse(JSON.stringify(context.parse("https://youtube.com/channel/UC1234567890123456789012"))), {
    parameter: "id",
    value: "UC1234567890123456789012"
  });
  assert.throws(() => context.parse("https://example.com/@someone"), /youtube\.com/);
  assert.throws(() => context.parse("https://youtube.com/c/someone"), /Custom/);
});

test("counts Unicode transcript words and parses YouTube video durations", () => {
  const context = {};
  vm.createContext(context);
  vm.runInContext(`${referenceWordCounter}\n${youtubeDurationParser}\nglobalThis.count = countReferenceWords; globalThis.duration = parseYouTubeDuration;`, context);

  assert.equal(context.count("Hello, world! বাংলা শব্দ 123"), 5);
  assert.equal(context.duration("PT1H2M3S"), 3723);
  assert.equal(context.duration("PT7M"), 420);
  assert.equal(context.duration("invalid"), 0);
});

test("initializes missing checklist state unchecked and persists user changes", () => {
  const harness = createChecklistHarness();
  harness.context.render();

  assert.deepEqual(harness.controls.production.map(input => input.checked), Array(6).fill(false));
  assert.deepEqual(harness.controls.script.map(input => input.checked), Array(5).fill(false));
  assert.deepEqual(harness.controls.publish.map(input => input.checked), Array(6).fill(false));

  const firstItem = harness.controls.production[0];
  firstItem.checked = true;
  firstItem.onchange();
  assert.deepEqual(JSON.parse(JSON.stringify(harness.project.checklists.production)), [true, false, false, false, false, false]);
  assert.equal(harness.saved(), 1);
});
