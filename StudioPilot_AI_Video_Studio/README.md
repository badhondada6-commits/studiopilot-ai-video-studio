# StudioPilot AI Video Studio

A free-first, local-first video production workspace for web and desktop PWA. The ordered workflow is Research & Ideas → Script Studio → Voice-over → Storyboard → Image creation → Image-to-video → Video editor → Render preview → Download → Publish Pack.

## Features

- Project data and exports stay in the current browser profile.
- Topic ideas, script drafting, editable storyboard scenes, an edit decision list (EDL), and YouTube metadata.
- Analyze recent public YouTube channel metadata with an optional user-owned Data API key, then generate original ideas from the resulting topic/packaging patterns.
- Paste a reference transcript to measure its word count and analyze broad structure; script drafting can target approximately that length without reproducing the transcript.
- Script, production, and pre-publish checklist progress is saved per project; legacy projects start unchecked.
- Works without an AI service using local fallback templates.
- Optional text generation with a local Ollama model (no API key), Google Gemini, or OpenRouter.
- Voice-over recording/import and system-voice preview; system speech is preview-only.
- Create prompt-informed, stylized scene illustrations in-browser with no key/server, upload images, or optionally use a locally hosted AUTOMATIC1111 / Forge Stable Diffusion API.
- Upload source videos or create simple pan-and-zoom clips from still images in the browser.
- Browser-based video rendering with scene captions, voice-over/music mix, preview, and download.
- Installable desktop PWA on supported Chromium browsers, with an offline app shell.
- Selected media and rendered video are not uploaded or persisted: they remain in memory for the current tab and need to be reselected after closing or reloading it.

This is a focused browser editor, not a full nonlinear editor or generative video model. Image-to-video is a pan/zoom effect; the EDL/edit notes are planning aids and are not applied as a timeline of transitions. Source-video audio is not mixed into the final render; only the selected voice-over and music tracks are included. Output is MP4 only when the browser supports that recorder format; otherwise it is usually WebM. Long renders can use substantial memory. Channel analysis uses YouTube's public Data API and does not retrieve captions or hidden analytics. The app does not upload to YouTube.

## Run on the web

Deploy the contents of this folder to Netlify (if connecting this repository, set the base directory to `StudioPilot_AI_Video_Studio`; leave the build command blank and set publish directory to `.`), or any static HTTPS host. The Netlify function in `netlify/functions/ai.js` is optional and is only needed for server-side OpenRouter requests. Local fallback templates work without a key or function.

## Install as a desktop app

Open the deployed HTTPS site in Chrome or Edge and choose **Install StudioPilot** from the browser's install icon/menu. The PWA shell is cached for offline use; local AI still needs Ollama running, and hosted AI needs an internet connection. This project has no separate Electron/Tauri native wrapper.

## No-key local AI (recommended)

1. Install [Ollama](https://ollama.com/download/) on the computer that will run the model.
2. In a terminal, run `ollama pull qwen2.5:3b` (the model is several GB; generation speed depends on hardware).
3. Keep Ollama running. In StudioPilot → **Settings**, choose **Ollama**, leave the URL as `http://127.0.0.1:11434`, and use the exact installed model name.
4. Click **Test AI connection**.

For a deployed site or app hosted on a different origin, Ollama must allow that exact origin using `OLLAMA_ORIGINS`; see the [Ollama server configuration guide](https://docs.ollama.com/faq#how-do-i-configure-ollama-server), then restart Ollama. Do not expose the Ollama port to the public internet. If the origin is blocked by CORS, local fallback templates still work.

## Competitor channel research (optional)

In **Research & Ideas**, enter a YouTube channel URL or `@handle`. To inspect its recent public uploads, create a key in [Google Cloud Console](https://console.cloud.google.com/apis/credentials), enable **YouTube Data API v3**, and paste the key into the page. The key stays in the current tab's `sessionStorage` and is sent only to Google's API; restrict it to the YouTube Data API and the deployed website's HTTP referrer. Analysis samples up to 12 recent public videos and reports available titles, descriptions, durations, views, and channel statistics. Google quota/availability applies.

The YouTube API does not provide transcripts for arbitrary channels. If you have a transcript you are allowed to use, paste it into the optional transcript box. The app counts words locally and asks the configured AI provider for high-level structure and pacing only; it does not save the transcript in the project. Original script drafting can target approximately that word count and use general structure patterns, but does not copy or closely paraphrase the source. Without a pasted transcript, target length is only estimated from sampled video runtime.

Voice-over offers generic system presets (calm documentary, warm conversational, upbeat explainer, and similar). It does not clone or imitate a particular creator's voice. System speech is a preview only; import or record audio to include a voice track in the render.

## Optional local image generation

The default **Create local illustration** action draws a stylized prompt-informed scene image in the browser. It works offline and needs no model, account, API key, or server; it is not an AI-generated image. For AI-generated images, each scene also has an optional AUTOMATIC1111-compatible Stable Diffusion WebUI/Forge action. StudioPilot does not download models or send prompts to a hosted image provider.

1. Install a compatible WebUI and download a model you are licensed to use.
2. Start the WebUI with its API enabled (for AUTOMATIC1111, this commonly means the `--api` launch option). Keep it bound to your own computer.
3. In StudioPilot → **Settings**, set the Stable Diffusion API URL (default: `http://127.0.0.1:7860/sdapi/v1/txt2img`). Configure the WebUI's CORS allow-list for the StudioPilot page origin if the browser blocks the request.
4. Open **Image creation** and use **Generate with local Stable Diffusion**, or simply use **Create local illustration** / upload your own without setting up a server.

The API and model are separate projects with their own hardware requirements, licenses, and setup. Do not expose the local API to the public internet. If your browser blocks local requests from the deployed site, use a local static server or upload images instead.

## Optional OpenRouter free models

OpenRouter requires an account/API key. Create one at [OpenRouter Keys](https://openrouter.ai/keys), then in Settings choose **OpenRouter** and enter the key. It is kept in this browser tab's `sessionStorage` (not in the project or localStorage) and sent directly to OpenRouter. Clear it with **Clear OpenRouter key**. Only use your own key on a site you trust.

The default model is `openrouter/free`, which routes only to available free models. Alternatively, select a model currently marked free in the [OpenRouter model catalog](https://openrouter.ai/models). Free availability does not guarantee an open-source license: check a model's license before use if that matters. Free access can have strict rate limits, outages, or model changes; it is not unlimited or guaranteed. Do not set paid fallbacks.

For a private Netlify deployment, you can instead set `OPENROUTER_API_KEY` under **Site configuration → Environment variables** and redeploy. The function defaults to `openrouter/free`; if setting `OPENROUTER_MODEL`, choose a free-only model/router. Never commit API keys or put them in the frontend source.

## Optional Google Gemini

Create a key in [Google AI Studio](https://aistudio.google.com/app/apikey), then in Settings choose **Google Gemini**, enter the model (default `gemini-2.5-flash`) and paste the key. The key stays in the current tab's `sessionStorage` (not in the project or localStorage) and is sent directly to Google's Gemini API. Clear it with **Clear Gemini key**. Use a restricted key and only enter it on a site you trust; Google account, billing, and model availability limits apply.

## Local preview and data

Open `index.html` for the local fallback experience. For service-worker/PWA behavior, serve this folder over localhost or HTTPS. Local Ollama requests also require Ollama's CORS allow-list to include the app's origin.

Projects are saved in browser `localStorage`; AI provider preferences and the local image API URL are stored locally, but optional Gemini, OpenRouter, and YouTube API keys are only kept for the current tab session. Hosted AI requests send prompts directly to the selected provider. The pasted reference transcript is analyzed in memory only and is not included in the exported project. Export project JSON regularly as a backup. Clearing browser storage can remove projects. Voice, image, source-video, music, and final-render blobs are held in memory only; download the final render and keep source files separately before closing or reloading the tab.
