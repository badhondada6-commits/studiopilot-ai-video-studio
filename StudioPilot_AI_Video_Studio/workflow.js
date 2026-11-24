(() => {
  const $ = id => document.getElementById(id);
  const imageAssets = new Map();
  const imageUrls = new Map();
  const imageElements = new Map();
  const sourceVideos = new Map();
  const sourceVideoUrls = new Map();
  const sourceVideoPlayers = new Map();
  const motionClips = new Map();
  const motionUrls = new Map();
  const motionPlayers = new Map();
  let voiceBlob = null;
  let voiceUrl = "";
  let voiceRecorder = null;
  let voiceChunks = [];
  let microphone = null;
  let musicBlob = null;
  let musicUrl = "";
  let renderBlob = null;
  let renderUrl = "";
  let renderType = "";
  let speech = null;

  function status(id, message) {
    $(id).textContent = message;
  }

  function invalidateRender() {
    if (renderUrl) URL.revokeObjectURL(renderUrl);
    renderUrl = "";
    renderBlob = null;
    renderType = "";
    project.rendered = false;
    project.downloaded = false;
    $("finalVideoPreview").removeAttribute("src");
    $("downloadPreview").removeAttribute("src");
    $("finalVideoPreview").classList.add("hidden");
    $("downloadPreview").classList.add("hidden");
    $("downloadVideo").classList.add("hidden");
    $("continueToDownload").disabled = true;
  }

  function clearMotionClips() {
    motionUrls.forEach(URL.revokeObjectURL);
    motionUrls.clear();
    motionClips.clear();
    motionPlayers.clear();
    project.motionReady = false;
    project.editApproved = false;
  }

  function clearSceneAssets() {
    imageUrls.forEach(URL.revokeObjectURL);
    sourceVideoUrls.forEach(URL.revokeObjectURL);
    clearMotionClips();
    imageAssets.clear();
    imageUrls.clear();
    imageElements.clear();
    sourceVideos.clear();
    sourceVideoUrls.clear();
    sourceVideoPlayers.clear();
    project.imagesReady = false;
    project.editApproved = false;
    invalidateRender();
  }

  function safeName(value) {
    return (value || "studiopilot-video").replace(/[^a-z0-9_-]+/gi, "-").replace(/^-|-$/g, "").toLowerCase() || "studiopilot-video";
  }

  function hasMotionForEveryScene() {
    return project.scenes.length > 0 && project.scenes.every((scene, index) => sourceVideos.has(index) || motionClips.has(index));
  }

  function recorderType(types) {
    if (!window.MediaRecorder) throw new Error("MediaRecorder is not supported in this browser.");
    return types.find(type => MediaRecorder.isTypeSupported(type)) || "";
  }

  function setImage(index, blob) {
    invalidateRender();
    clearMotionClips();
    project.editApproved = false;
    if (imageUrls.has(index)) URL.revokeObjectURL(imageUrls.get(index));
    const url = URL.createObjectURL(blob);
    const image = new Image();
    image.src = url;
    imageAssets.set(index, blob);
    imageUrls.set(index, url);
    imageElements.set(index, image);
    project.imagesReady = project.scenes.length > 0 && imageAssets.size >= project.scenes.length;
    save();
    renderImageScenes();
    renderMotionScenes();
  }

  function setVideo(index, blob) {
    invalidateRender();
    clearMotionClips();
    project.editApproved = false;
    if (sourceVideoUrls.has(index)) URL.revokeObjectURL(sourceVideoUrls.get(index));
    sourceVideos.set(index, blob);
    sourceVideoUrls.set(index, URL.createObjectURL(blob));
    sourceVideoPlayers.delete(index);
  }

  function setMotion(index, blob) {
    invalidateRender();
    project.editApproved = false;
    if (motionUrls.has(index)) URL.revokeObjectURL(motionUrls.get(index));
    motionClips.set(index, blob);
    motionUrls.set(index, URL.createObjectURL(blob));
    motionPlayers.delete(index);
    renderMotionScenes();
  }

  function renderVoiceover() {
    $("voiceoverScript").textContent = project.script || "Approve a script to load narration here.";
    if (voiceUrl) {
      $("voiceoverPreview").src = voiceUrl;
      $("voiceoverPreview").classList.remove("hidden");
    } else status("voiceoverStatus", "No voice track in this tab. Record or import audio before rendering with narration.");
  }

  function useVoice(blob) {
    invalidateRender();
    if (voiceUrl) URL.revokeObjectURL(voiceUrl);
    voiceBlob = blob;
    voiceUrl = URL.createObjectURL(blob);
    $("voiceoverPreview").src = voiceUrl;
    $("voiceoverPreview").classList.remove("hidden");
    status("voiceoverStatus", `Voice-over ready (${(blob.size / 1048576).toFixed(1)} MB).`);
    project.voiceoverApproved = false;
    project.editApproved = false;
    save();
  }

  function stopMicrophone() {
    if (voiceRecorder && voiceRecorder.state !== "inactive") voiceRecorder.stop();
    $("recordVoice").disabled = false;
    $("stopVoice").disabled = true;
  }

  function imagePromptSize() {
    const aspect = $("aspect").value;
    if (aspect.startsWith("9:16")) return { width: 432, height: 768 };
    if (aspect.startsWith("1:1")) return { width: 512, height: 512 };
    return { width: 768, height: 432 };
  }

  async function createLocalIllustration(index, promptText, button) {
    const scene = project.scenes[index];
    if (!scene) throw new Error(`Scene ${index + 1} no longer exists.`);
    const prompt = promptText.trim() || scene.visual || scene.narration || `Scene ${index + 1}`;
    if (button) button.disabled = true;
    status("imageStatus", `Creating a no-key illustration for scene ${index + 1}…`);
    try {
      const { width, height } = imagePromptSize();
      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext("2d");
      if (!context) throw new Error("Canvas image creation is unavailable in this browser.");
      let seed = [...prompt].reduce((hash, char) => (hash * 31 + char.charCodeAt(0)) >>> 0, 7);
      const random = () => {
        seed = (seed * 1664525 + 1013904223) >>> 0;
        return seed / 4294967296;
      };
      const palettes = [
        ["#071426", "#155e75", "#2dd4bf", "#fbbf24"],
        ["#111827", "#14532d", "#4ade80", "#d9f99d"],
        ["#1e1637", "#6d28d9", "#c4b5fd", "#f0abfc"],
        ["#311b22", "#9a3412", "#fb7185", "#fde68a"]
      ];
      const palette = palettes[seed % palettes.length];
      const lower = prompt.toLowerCase();
      const gradient = context.createLinearGradient(0, 0, width, height);
      gradient.addColorStop(0, palette[0]);
      gradient.addColorStop(1, palette[1]);
      context.fillStyle = gradient;
      context.fillRect(0, 0, width, height);

      const glow = context.createRadialGradient(width * 0.68, height * 0.38, 2, width * 0.68, height * 0.38, width * 0.55);
      glow.addColorStop(0, `${palette[2]}88`);
      glow.addColorStop(1, `${palette[1]}00`);
      context.fillStyle = glow;
      context.fillRect(0, 0, width, height);

      context.globalAlpha = 0.14;
      context.strokeStyle = "#e5f3ff";
      context.lineWidth = 1;
      for (let x = 0; x < width; x += Math.round(width / 12)) {
        context.beginPath();
        context.moveTo(x, 0);
        context.lineTo(x, height);
        context.stroke();
      }
      for (let y = 0; y < height; y += Math.round(height / 10)) {
        context.beginPath();
        context.moveTo(0, y);
        context.lineTo(width, y);
        context.stroke();
      }
      context.globalAlpha = 1;

      if (/mountain|nature|forest|ocean|earth|landscape|river|tree/.test(lower)) {
        context.fillStyle = palette[3];
        context.globalAlpha = 0.9;
        context.beginPath();
        context.arc(width * 0.75, height * 0.3, Math.min(width, height) * 0.1, 0, Math.PI * 2);
        context.fill();
        context.globalAlpha = 1;
        for (let layer = 0; layer < 3; layer += 1) {
          const base = height * (0.62 + layer * 0.12);
          context.fillStyle = [palette[2], palette[1], palette[0]][layer];
          context.beginPath();
          context.moveTo(0, height);
          for (let x = 0; x <= width; x += width / 5) {
            const peak = base - random() * height * 0.27;
            context.lineTo(x, peak);
          }
          context.lineTo(width, height);
          context.closePath();
          context.fill();
        }
      } else if (/city|urban|building|street|architecture/.test(lower)) {
        const base = height * 0.76;
        context.fillStyle = palette[0];
        for (let x = 0; x < width; x += width / 8) {
          const buildingHeight = height * (0.2 + random() * 0.42);
          context.fillRect(x, base - buildingHeight, width / 8 - 3, buildingHeight);
          context.fillStyle = palette[3];
          for (let wy = base - buildingHeight + 10; wy < base - 8; wy += 18) {
            for (let wx = x + 7; wx < x + width / 8 - 5; wx += 16) {
              if (random() > 0.38) context.fillRect(wx, wy, 4, 6);
            }
          }
          context.fillStyle = palette[0];
        }
        context.fillStyle = palette[2];
        context.fillRect(0, base, width, height - base);
      } else if (/space|planet|moon|star|universe|galaxy|orbit/.test(lower)) {
        const radius = Math.min(width, height) * 0.22;
        const planet = context.createRadialGradient(width * 0.63 - radius * 0.35, height * 0.4 - radius * 0.4, radius * 0.05, width * 0.63, height * 0.4, radius);
        planet.addColorStop(0, "#ffffff");
        planet.addColorStop(0.24, palette[2]);
        planet.addColorStop(1, palette[1]);
        context.fillStyle = planet;
        context.beginPath();
        context.arc(width * 0.63, height * 0.4, radius, 0, Math.PI * 2);
        context.fill();
        context.strokeStyle = `${palette[3]}bb`;
        context.lineWidth = Math.max(3, width * 0.012);
        context.beginPath();
        context.ellipse(width * 0.63, height * 0.4, radius * 1.55, radius * 0.48, -0.28, 0, Math.PI * 2);
        context.stroke();
        for (let i = 0; i < 50; i += 1) {
          context.fillStyle = `rgba(255,255,255,${0.25 + random() * 0.7})`;
          context.fillRect(random() * width, random() * height * 0.75, 2, 2);
        }
      } else if (/ai|technology|robot|computer|code|data|digital|science|network/.test(lower)) {
        const points = Array.from({ length: 18 }, () => ({ x: random() * width, y: random() * height * 0.78 }));
        context.strokeStyle = `${palette[2]}99`;
        context.lineWidth = Math.max(1, width * 0.003);
        points.forEach((point, i) => points.slice(i + 1).forEach(other => {
          if (Math.hypot(point.x - other.x, point.y - other.y) < width * 0.24) {
            context.beginPath();
            context.moveTo(point.x, point.y);
            context.lineTo(other.x, other.y);
            context.stroke();
          }
        }));
        points.forEach(point => {
          context.fillStyle = palette[3];
          context.beginPath();
          context.arc(point.x, point.y, Math.max(3, width * 0.008), 0, Math.PI * 2);
          context.fill();
        });
        context.strokeStyle = `${palette[3]}cc`;
        context.lineWidth = Math.max(2, width * 0.008);
        context.beginPath();
        context.arc(width * 0.58, height * 0.37, Math.min(width, height) * 0.14, 0, Math.PI * 2);
        context.stroke();
      } else {
        for (let i = 0; i < 7; i += 1) {
          const x = width * (0.2 + random() * 0.65);
          const y = height * (0.12 + random() * 0.55);
          const radius = Math.min(width, height) * (0.045 + random() * 0.16);
          context.globalAlpha = 0.22 + random() * 0.35;
          context.fillStyle = i % 2 ? palette[2] : palette[3];
          context.beginPath();
          context.arc(x, y, radius, 0, Math.PI * 2);
          context.fill();
          context.globalAlpha = 0.72;
          context.strokeStyle = "#ffffff";
          context.lineWidth = Math.max(2, width * 0.004);
          context.beginPath();
          context.arc(x, y, radius * 1.28, 0, Math.PI * 1.65);
          context.stroke();
        }
        context.globalAlpha = 1;
      }

      const footer = context.createLinearGradient(0, height * 0.64, 0, height);
      footer.addColorStop(0, "rgba(4,10,20,0)");
      footer.addColorStop(1, "rgba(4,10,20,0.88)");
      context.fillStyle = footer;
      context.fillRect(0, height * 0.64, width, height * 0.36);
      context.fillStyle = palette[2];
      context.fillRect(width * 0.07, height * 0.79, width * 0.1, Math.max(3, height * 0.008));
      context.fillStyle = "#ffffff";
      context.font = `700 ${Math.round(width * 0.035)}px system-ui`;
      const label = (scene.text || prompt).replace(/\s+/g, " ").slice(0, 52);
      context.fillText(label, width * 0.07, height * 0.89, width * 0.84);
      context.fillStyle = "rgba(230,240,255,0.72)";
      context.font = `${Math.round(width * 0.016)}px system-ui`;
      context.fillText(`SCENE ${String(index + 1).padStart(2, "0")}  ·  LOCAL ILLUSTRATION`, width * 0.07, height * 0.95);

      const blob = await new Promise((resolve, reject) => canvas.toBlob(result => result ? resolve(result) : reject(new Error("Could not export the local illustration.")), "image/png"));
      setImage(index, blob);
      status("imageStatus", `Local illustration ready for scene ${index + 1}. This artwork was drawn in your browser; no image API or key was used.`);
    } finally {
      if (button) button.disabled = false;
    }
  }

  function sceneCard(index, scene, root) {
    const card = document.createElement("article");
    card.className = "asset-card";
    const title = document.createElement("h3");
    title.textContent = `Scene ${index + 1}`;
    const prompt = document.createElement("textarea");
    prompt.className = "scene-prompt";
    prompt.value = scene.visual || "";
    prompt.setAttribute("aria-label", `Scene ${index + 1} image prompt`);
    prompt.addEventListener("change", () => {
      scene.visual = prompt.value;
      clearMotionClips();
      invalidateRender();
      project.editApproved = false;
      save();
    });
    const file = document.createElement("input");
    file.type = "file";
    file.accept = "image/*";
    file.setAttribute("aria-label", `Upload image for scene ${index + 1}`);
    file.addEventListener("change", () => {
      if (file.files[0]) setImage(index, file.files[0]);
    });
    const image = document.createElement("img");
    image.alt = `Scene ${index + 1} preview`;
    image.className = "hidden";
    if (imageUrls.has(index)) {
      image.src = imageUrls.get(index);
      image.classList.remove("hidden");
    }
    const local = document.createElement("button");
    local.type = "button";
    local.textContent = imageAssets.has(index) ? "Recreate local illustration" : "Create local illustration";
    local.addEventListener("click", () => createLocalIllustration(index, prompt.value, local).catch(error => status("imageStatus", `Could not create illustration: ${error.message}`)));
    const aiImage = document.createElement("button");
    aiImage.type = "button";
    aiImage.className = "ghost";
    aiImage.textContent = "Generate with local Stable Diffusion";
    aiImage.addEventListener("click", () => generateImage(index, prompt, aiImage));
    card.append(title, prompt, file, image, local, aiImage);
    root.append(card);
  }

  function renderImageScenes() {
    const root = $("imageScenes");
    if (!root) return;
    root.replaceChildren();
    if (!project.scenes.length) {
      root.textContent = "Create a storyboard before adding scene images.";
      return;
    }
    project.scenes.forEach((scene, index) => sceneCard(index, scene, root));
  }

  async function generateImage(index, promptInput, button) {
    const prompt = promptInput.value.trim();
    if (!prompt) {
      status("imageStatus", `Add a visual prompt for scene ${index + 1} first.`);
      return;
    }
    const endpoint = $("imageApiEndpoint").value.trim();
    button.disabled = true;
    status("imageStatus", `Generating image for scene ${index + 1} on your local image server…`);
    try {
      const { width, height } = imagePromptSize();
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt, width, height, steps: 20, batch_size: 1 })
      });
      let data;
      try {
        data = await response.json();
      } catch {
        data = {};
      }
      if (!response.ok) throw new Error(data.error || data.detail || `Image API returned ${response.status}`);
      if (!data.images?.[0]) throw new Error("Image API returned no image. Check that the local WebUI API is enabled.");
      const raw = atob(data.images[0].replace(/^data:image\/\w+;base64,/, ""));
      const bytes = Uint8Array.from(raw, char => char.charCodeAt(0));
      setImage(index, new Blob([bytes], { type: "image/png" }));
      status("imageStatus", `Image created for scene ${index + 1}. Generated images are kept in this tab only.`);
    } catch (error) {
      status("imageStatus", `Could not create image: ${error.message}. Upload an image instead or check Settings and the README.`);
    } finally {
      button.disabled = false;
      renderImageScenes();
    }
  }

  async function generateMissingImages() {
    const missing = project.scenes.map((scene, index) => index).filter(index => !imageAssets.has(index));
    if (!missing.length) {
      status("imageStatus", "Every scene already has an image.");
      return;
    }
    for (const index of missing) {
      const prompt = $("imageScenes").children[index]?.querySelector(".scene-prompt");
      if (prompt) {
        try {
          await createLocalIllustration(index, prompt.value);
        } catch (error) {
          status("imageStatus", `Could not create illustration for scene ${index + 1}: ${error.message}`);
          return;
        }
      }
    }
    status("imageStatus", `Created ${missing.length} local illustration(s). These are browser-drawn images, not AI-generated images.`);
  }

  function loadImage(index) {
    const image = imageElements.get(index);
    if (!image) return Promise.resolve(null);
    if (image.complete && image.naturalWidth) return Promise.resolve(image);
    return new Promise((resolve, reject) => {
      image.onload = () => resolve(image);
      image.onerror = () => reject(new Error(`Could not read the image for scene ${index + 1}.`));
    });
  }

  function imageDimensions() {
    const aspect = $("aspect").value;
    if (aspect.startsWith("9:16")) return [720, 1280];
    if (aspect.startsWith("1:1")) return [900, 900];
    return [1280, 720];
  }

  function drawCover(context, source, width, height, progress = 0) {
    const sourceWidth = source.videoWidth || source.naturalWidth || source.width;
    const sourceHeight = source.videoHeight || source.naturalHeight || source.height;
    if (!sourceWidth || !sourceHeight) return;
    const scale = Math.max(width / sourceWidth, height / sourceHeight) * (1 + progress * 0.12);
    const drawWidth = sourceWidth * scale;
    const drawHeight = sourceHeight * scale;
    const pan = progress * width * 0.025;
    context.drawImage(source, (width - drawWidth) / 2 - pan, (height - drawHeight) / 2, drawWidth, drawHeight);
  }

  function drawPlaceholder(context, width, height, scene, index, progress) {
    const gradient = context.createLinearGradient(0, 0, width, height);
    gradient.addColorStop(0, "#202b4d");
    gradient.addColorStop(1, "#080d19");
    context.fillStyle = gradient;
    context.fillRect(0, 0, width, height);
    context.fillStyle = "#42d8d0";
    context.fillRect(64, 70, 5, height - 140);
    context.fillStyle = "#eef3ff";
    context.font = `700 ${Math.round(width * 0.045)}px system-ui`;
    context.fillText(`SCENE ${String(index + 1).padStart(2, "0")}`, 92, 130);
    context.font = `${Math.round(width * 0.026)}px system-ui`;
    const text = (scene.visual || scene.narration || scene.text || "Add a scene image").slice(0, 110);
    context.fillText(text, 92, 205, width - 180);
    context.globalAlpha = 0.3 + progress * 0.2;
    context.fillStyle = "#8b7cff";
    context.beginPath();
    context.arc(width - 110, height - 100, 48 + progress * 28, 0, Math.PI * 2);
    context.fill();
    context.globalAlpha = 1;
  }

  function drawScene(context, width, height, scene, index, progress, visuals) {
    context.fillStyle = "#080d19";
    context.fillRect(0, 0, width, height);
    const visual = visuals[index];
    if (visual && (visual.videoWidth || visual.naturalWidth)) drawCover(context, visual, width, height, progress);
    else drawPlaceholder(context, width, height, scene, index, progress);
    const overlay = context.createLinearGradient(0, height * 0.55, 0, height);
    overlay.addColorStop(0, "rgba(8,13,25,0)");
    overlay.addColorStop(1, "rgba(8,13,25,0.88)");
    context.fillStyle = overlay;
    context.fillRect(0, 0, width, height);
    context.fillStyle = "#eef3ff";
    context.font = `700 ${Math.round(width * 0.032)}px system-ui`;
    context.fillText((scene.text || scene.narration || `Scene ${index + 1}`).slice(0, 72), width * 0.06, height * 0.88, width * 0.88);
    context.fillStyle = "#42d8d0";
    context.fillRect(width * 0.06, height * 0.92, width * 0.88 * progress, Math.max(3, height * 0.006));
  }

  function videoElement(blob) {
    const url = URL.createObjectURL(blob);
    const video = document.createElement("video");
    video.muted = true;
    video.loop = true;
    video.playsInline = true;
    video.preload = "auto";
    video.src = url;
    return new Promise((resolve, reject) => {
      video.onloadeddata = () => resolve({ video, url });
      video.onerror = () => {
        URL.revokeObjectURL(url);
        reject(new Error("Could not read a source video."));
      };
    });
  }

  async function getMotionPlayers() {
    const players = new Map();
    for (const [index, blob] of motionClips) players.set(index, await videoElement(blob));
    return players;
  }

  function renderMotionScenes() {
    const root = $("motionScenes");
    if (!root) return;
    root.replaceChildren();
    project.scenes.forEach((scene, index) => {
      const card = document.createElement("article");
      card.className = "asset-card";
      const title = document.createElement("h3");
      title.textContent = `Scene ${index + 1}`;
      const image = document.createElement("img");
      image.alt = `Scene ${index + 1} source`;
      image.className = imageUrls.has(index) ? "" : "hidden";
      if (imageUrls.has(index)) image.src = imageUrls.get(index);
      const video = document.createElement("video");
      video.controls = true;
      video.playsInline = true;
      const clipUrl = motionUrls.get(index) || sourceVideoUrls.get(index);
      video.className = clipUrl ? "" : "hidden";
      if (clipUrl) video.src = clipUrl;
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = sourceVideos.has(index) ? "Source video ready" : motionClips.has(index) ? "Recreate motion clip" : "Animate image";
      button.disabled = sourceVideos.has(index) || !imageAssets.has(index);
      button.addEventListener("click", async () => {
        button.disabled = true;
        try {
          status("motionStatus", `Animating scene ${index + 1}…`);
          const blob = await animateStill(index);
          setMotion(index, blob);
          project.motionReady = hasMotionForEveryScene();
          save();
          status("motionStatus", `Scene ${index + 1} motion clip is ready.`);
        } catch (error) {
          status("motionStatus", `Could not animate scene ${index + 1}: ${error.message}`);
        } finally {
          button.disabled = false;
        }
      });
      card.append(title, image, video, button);
      root.append(card);
    });
    if (!project.scenes.length) root.textContent = "Create a storyboard first.";
  }

  function recordCanvas(canvas, seconds, drawFrame) {
    const mimeType = recorderType(["video/webm;codecs=vp9", "video/webm;codecs=vp8", "video/mp4", "video/webm"]);
    const stream = canvas.captureStream(24);
    const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
    const chunks = [];
    return new Promise((resolve, reject) => {
      recorder.ondataavailable = event => {
        if (event.data.size) chunks.push(event.data);
      };
      recorder.onerror = event => {
        stream.getTracks().forEach(track => track.stop());
        reject(event.error || new Error("Video recording failed."));
      };
      recorder.onstop = () => {
        stream.getTracks().forEach(track => track.stop());
        resolve(new Blob(chunks, { type: recorder.mimeType || mimeType || "video/webm" }));
      };
      recorder.start(250);
      const start = performance.now();
      const draw = now => {
        const progress = Math.min(1, (now - start) / (seconds * 1000));
        drawFrame(progress);
        if (progress >= 1) recorder.stop();
        else requestAnimationFrame(draw);
      };
      requestAnimationFrame(draw);
    });
  }

  async function animateStill(index) {
    if (!imageAssets.has(index)) throw new Error("Add an image for this scene first.");
    const [width, height] = imageDimensions();
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    const image = await loadImage(index);
    const seconds = Number($("motionDuration").value) || 5;
    return recordCanvas(canvas, seconds, progress => drawCover(context, image, width, height, progress));
  }

  async function uploadMediaFiles() {
    const files = [...$("mediaFiles").files];
    sourceVideos.clear();
    for (const url of sourceVideoUrls.values()) URL.revokeObjectURL(url);
    sourceVideoUrls.clear();
    sourceVideoPlayers.clear();
    clearMotionClips();
    invalidateRender();
    $("fileList").textContent = files.map(file => `${file.name} (${(file.size / 1048576).toFixed(1)} MB)`).join("\n") || "No files selected";
    let sceneIndex = 0;
    for (const file of files) {
      if (file.type.startsWith("image/")) setImage(sceneIndex, file);
      else if (file.type.startsWith("video/")) setVideo(sceneIndex, file);
      sceneIndex += 1;
    }
    project.motionReady = hasMotionForEveryScene();
    save();
    status("motionStatus", `${sourceVideos.size} source video(s) assigned by scene order.`);
  }

  function updateVolumeLabel(inputId, outputId, suffix = "%") {
    const input = $(inputId);
    $(outputId).textContent = `${Math.round(Number(input.value) * 100)}${suffix}`;
  }

  function connectAudioTrack(context, destination, blob, volume, loop) {
    const url = URL.createObjectURL(blob);
    const audio = new Audio(url);
    audio.loop = loop;
    audio.preload = "auto";
    const source = context.createMediaElementSource(audio);
    const gain = context.createGain();
    gain.gain.value = Number(volume);
    source.connect(gain);
    gain.connect(destination);
    return { audio, source, url };
  }

  async function renderComposition() {
    const scenes = project.scenes.map(scene => ({ ...scene }));
    if (!scenes.length) throw new Error("Create a storyboard before rendering.");
    const [width, height] = imageDimensions();
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    const sceneSeconds = Number($("sceneDuration").value) || 5;
    const totalSeconds = sceneSeconds * scenes.length;
    const visuals = [];
    const players = [];
    for (let index = 0; index < scenes.length; index += 1) {
      if (sourceVideos.has(index)) {
        const player = await videoElement(sourceVideos.get(index));
        visuals[index] = player.video;
        players.push(player);
      } else if (motionClips.has(index)) {
        const player = await videoElement(motionClips.get(index));
        visuals[index] = player.video;
        players.push(player);
      } else if (imageAssets.has(index)) {
        visuals[index] = await loadImage(index);
      } else {
        visuals[index] = null;
      }
    }
    const canvasStream = canvas.captureStream(24);
    let audioContext = null;
    let audioDestination = null;
    const audioTracks = [];
    const audioPlayers = [];
    if (voiceBlob || musicBlob) {
      audioContext = new AudioContext();
      audioDestination = audioContext.createMediaStreamDestination();
      if (voiceBlob) audioPlayers.push(connectAudioTrack(audioContext, audioDestination, voiceBlob, $("voiceVolume").value, false));
      if (musicBlob) audioPlayers.push(connectAudioTrack(audioContext, audioDestination, musicBlob, $("musicVolume").value, true));
      audioDestination.stream.getAudioTracks().forEach(track => {
        audioTracks.push(track);
        canvasStream.addTrack(track);
      });
      await audioContext.resume();
    }
    const mimeType = recorderType(["video/webm;codecs=vp9,opus", "video/webm;codecs=vp8,opus", "video/mp4", "video/webm"]);
    const recorder = mimeType ? new MediaRecorder(canvasStream, { mimeType }) : new MediaRecorder(canvasStream);
    const chunks = [];
    status("renderStatus", `Rendering ${scenes.length} scenes (${totalSeconds} seconds)… Keep this tab open.`);
    return new Promise((resolve, reject) => {
      let settled = false;
      const cleanup = () => {
        canvasStream.getTracks().forEach(track => track.stop());
        audioTracks.forEach(track => track.stop());
        audioPlayers.forEach(({ audio, url }) => {
          audio.pause();
          URL.revokeObjectURL(url);
        });
        if (audioContext) audioContext.close();
        players.forEach(({ video, url }) => {
          video.pause();
          URL.revokeObjectURL(url);
        });
      };
      const fail = error => {
        if (settled) return;
        settled = true;
        if (recorder.state !== "inactive") recorder.stop();
        cleanup();
        reject(error);
      };
      recorder.ondataavailable = event => {
        if (event.data.size) chunks.push(event.data);
      };
      recorder.onerror = event => fail(event.error || new Error("Video render failed."));
      recorder.onstop = () => {
        if (settled) return;
        settled = true;
        cleanup();
        resolve(new Blob(chunks, { type: recorder.mimeType || mimeType || "video/webm" }));
      };
      recorder.start(500);
      Promise.all(players.map(({ video }) => video.play().catch(() => undefined)));
      Promise.all(audioPlayers.map(({ audio }) => audio.play().catch(() => undefined)));
      const started = performance.now();
      const draw = now => {
        const frameElapsed = ((Number.isFinite(now) ? now : performance.now()) - started) / 1000;
        const elapsed = Math.max(0, Math.min(totalSeconds, Number.isFinite(frameElapsed) ? frameElapsed : 0));
        const sceneIndex = Math.max(0, Math.min(scenes.length - 1, Math.floor(elapsed / sceneSeconds)));
        const progress = (elapsed - sceneIndex * sceneSeconds) / sceneSeconds;
        try {
          drawScene(context, width, height, scenes[sceneIndex], sceneIndex, progress, visuals);
        } catch (error) {
          fail(error);
          return;
        }
        if (elapsed >= totalSeconds) recorder.stop();
        else requestAnimationFrame(draw);
      };
      requestAnimationFrame(draw);
    });
  }

  function setRenderedVideo(blob) {
    if (renderUrl) URL.revokeObjectURL(renderUrl);
    renderBlob = blob;
    renderType = blob.type || "video/webm";
    renderUrl = URL.createObjectURL(blob);
    const extension = renderType.includes("mp4") ? "mp4" : "webm";
    const fileName = `${safeName(project.name)}.${extension}`;
    $("finalVideoPreview").src = renderUrl;
    $("finalVideoPreview").classList.remove("hidden");
    $("downloadPreview").src = renderUrl;
    $("downloadPreview").classList.remove("hidden");
    $("downloadVideo").href = renderUrl;
    $("downloadVideo").download = fileName;
    $("downloadVideo").textContent = `Download ${fileName}`;
    $("downloadVideo").classList.remove("hidden");
    $("renderDetails").textContent = `${extension.toUpperCase()} · ${(blob.size / 1048576).toFixed(1)} MB · ${project.scenes.length} scenes`;
    $("continueToDownload").disabled = false;
    project.rendered = true;
    save();
    status("renderStatus", "Render complete. Review the preview or continue to download.");
    status("downloadStatus", "Video rendered locally. Download it to keep a permanent copy.");
  }

  function renderDownloadPage() {
    if (renderUrl) {
      $("downloadPreview").src = renderUrl;
      $("downloadPreview").classList.remove("hidden");
      $("downloadVideo").classList.remove("hidden");
    }
  }

  function clearWorkflowAssets() {
    if (voiceRecorder && voiceRecorder.state !== "inactive") {
      voiceRecorder.ondataavailable = null;
      voiceRecorder.onstop = null;
      voiceRecorder.stop();
    }
    if (microphone) microphone.getTracks().forEach(track => track.stop());
    if (speech && window.speechSynthesis) window.speechSynthesis.cancel();
    clearSceneAssets();
    [voiceUrl, musicUrl, renderUrl].filter(Boolean).forEach(URL.revokeObjectURL);
    voiceBlob = null;
    voiceUrl = "";
    voiceRecorder = null;
    voiceChunks = [];
    microphone = null;
    musicBlob = null;
    musicUrl = "";
    $("voiceoverPreview").removeAttribute("src");
    $("voiceoverPreview").classList.add("hidden");
    $("musicPreview").removeAttribute("src");
    $("musicPreview").classList.add("hidden");
    renderBlob = null;
    renderUrl = "";
    renderType = "";
  }

  function initialize() {
    project.voiceoverApproved = false;
    project.imagesReady = false;
    project.motionReady = false;
    project.editApproved = false;
    project.rendered = false;
    project.downloaded = false;
    save();
    const endpoint = $("imageApiEndpoint");
    endpoint.value = localStorage.getItem("studiopilot_image_endpoint") || endpoint.value;
    endpoint.addEventListener("change", () => localStorage.setItem("studiopilot_image_endpoint", endpoint.value.trim()));
    $("speakVoiceover").onclick = () => {
      if (!project.script.trim()) return status("voiceoverStatus", "Create a script before previewing narration.");
      if (!("speechSynthesis" in window)) return status("voiceoverStatus", "System speech preview is not supported in this browser.");
      window.speechSynthesis.cancel();
      speech = new SpeechSynthesisUtterance(project.script);
      speech.lang = $("scriptLang").value === "বাংলা" ? "bn-BD" : "en-US";
      window.speechSynthesis.speak(speech);
      status("voiceoverStatus", "Playing a system voice preview. It is not recorded to a file.");
    };
    $("stopSpeech").onclick = () => window.speechSynthesis?.cancel();
    $("voiceoverFile").onchange = () => {
      if ($("voiceoverFile").files[0]) useVoice($("voiceoverFile").files[0]);
    };
    $("recordVoice").onclick = async () => {
      try {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error("Microphone recording requires HTTPS or localhost.");
        microphone = await navigator.mediaDevices.getUserMedia({ audio: true });
        const type = recorderType(["audio/webm;codecs=opus", "audio/mp4", "audio/webm"]);
        voiceRecorder = type ? new MediaRecorder(microphone, { mimeType: type }) : new MediaRecorder(microphone);
        voiceChunks = [];
        voiceRecorder.ondataavailable = event => {
          if (event.data.size) voiceChunks.push(event.data);
        };
        voiceRecorder.onstop = () => {
          microphone.getTracks().forEach(track => track.stop());
          microphone = null;
          useVoice(new Blob(voiceChunks, { type: voiceRecorder.mimeType || type || "audio/webm" }));
        };
        voiceRecorder.start();
        $("recordVoice").disabled = true;
        $("stopVoice").disabled = false;
        status("voiceoverStatus", "Recording microphone audio…");
      } catch (error) {
        status("voiceoverStatus", `Could not start recording: ${error.message}`);
      }
    };
    $("stopVoice").onclick = stopMicrophone;
    $("approveVoiceover").onclick = () => {
      project.voiceoverApproved = !!voiceBlob;
      save();
      go("storyboard");
    };
    $("uploadSceneImages").onchange = () => {
      [...$("uploadSceneImages").files].slice(0, project.scenes.length).forEach((file, index) => setImage(index, file));
      status("imageStatus", "Uploaded images assigned to scenes in file order. Files stay in this tab.");
    };
    $("generateSceneImages").onclick = generateMissingImages;
    $("approveImages").onclick = () => {
      project.imagesReady = project.scenes.length > 0 && imageAssets.size >= project.scenes.length;
      save();
      if (!project.imagesReady) status("imageStatus", "Some scenes still have no image; the render will use title cards for those scenes.");
      go("imagetovideo");
    };
    $("animateAll").onclick = async () => {
      const indices = project.scenes.map((scene, index) => index).filter(index => imageAssets.has(index));
      if (!indices.length) return status("motionStatus", "Add or generate scene images before making motion clips.");
      $("animateAll").disabled = true;
      try {
        for (const index of indices) {
          status("motionStatus", `Animating scene ${index + 1} of ${indices.length}…`);
          setMotion(index, await animateStill(index));
        }
        project.motionReady = hasMotionForEveryScene();
        save();
        status("motionStatus", "Motion clips are ready. They will be used in the final render.");
      } catch (error) {
        status("motionStatus", `Could not create motion clip: ${error.message}`);
      } finally {
        $("animateAll").disabled = false;
      }
    };
    $("continueToEditor").onclick = () => {
      project.motionReady = hasMotionForEveryScene();
      save();
      go("production");
    };
    $("mediaFiles").onchange = uploadMediaFiles;
    $("musicFile").onchange = () => {
      const file = $("musicFile").files[0];
      if (!file) return;
      if (musicUrl) URL.revokeObjectURL(musicUrl);
      invalidateRender();
      project.editApproved = false;
      musicBlob = file;
      musicUrl = URL.createObjectURL(file);
      $("musicPreview").src = musicUrl;
      $("musicPreview").classList.remove("hidden");
    };
    $("voiceVolume").oninput = () => updateVolumeLabel("voiceVolume", "voiceVolumeValue");
    $("musicVolume").oninput = () => updateVolumeLabel("musicVolume", "musicVolumeValue");
    ["voiceVolume", "musicVolume", "sceneDuration"].forEach(id => $(id).addEventListener("change", () => {
      invalidateRender();
      project.editApproved = false;
      save();
    }));
    $("aspect").addEventListener("change", () => {
      clearMotionClips();
      invalidateRender();
      project.editApproved = false;
      save();
    });
    $("approveEdit").onclick = () => {
      if (!project.scenes.length) return toast("Create a storyboard before approving the edit.");
      if (!project.edl) $("makeEDL").click();
      project.editApproved = true;
      save();
      go("render");
    };
    $("renderVideo").onclick = async () => {
      if (!project.scenes.length) {
        toast("Create a storyboard before rendering.");
        go("storyboard");
        return;
      }
      if (!project.editApproved) {
        toast("Approve the edit in Video editor before rendering.");
        go("production");
        return;
      }
      $("renderVideo").disabled = true;
      $("continueToDownload").disabled = true;
      $("finalVideoPreview").classList.add("hidden");
      try {
        setRenderedVideo(await renderComposition());
      } catch (error) {
        status("renderStatus", `Render failed: ${error.message}`);
      } finally {
        $("renderVideo").disabled = false;
      }
    };
    $("continueToDownload").onclick = () => go("download");
    $("downloadVideo").addEventListener("click", () => {
      project.downloaded = true;
      save();
    });
    $("voiceVolume").dispatchEvent(new Event("input"));
    $("musicVolume").dispatchEvent(new Event("input"));
    window.renderVoiceover = renderVoiceover;
    window.renderImageScenes = renderImageScenes;
    window.renderMotionScenes = renderMotionScenes;
    window.renderDownloadPage = renderDownloadPage;
    window.clearWorkflowAssets = clearWorkflowAssets;
    window.clearSceneAssets = clearSceneAssets;
    window.clearMotionClips = clearMotionClips;
    window.invalidateFinalRender = invalidateRender;
    const originalRenderScenes = window.renderScenes;
    window.renderScenes = (...args) => {
      originalRenderScenes(...args);
      document.querySelectorAll("[data-scene]").forEach(input => {
        const onInput = input.oninput;
        input.oninput = () => {
          onInput();
          clearMotionClips();
          invalidateRender();
          project.editApproved = false;
          save();
        };
      });
      document.querySelectorAll("[data-del]").forEach(button => {
        const onDelete = button.onclick;
        button.onclick = () => {
          clearSceneAssets();
          onDelete();
        };
      });
    };
    const storyBtnHandler = $("storyBtn").onclick;
    $("storyBtn").onclick = async () => {
      clearSceneAssets();
      await storyBtnHandler();
    };
    const approveScriptHandler = $("approveScript").onclick;
    $("approveScript").onclick = () => {
      clearWorkflowAssets();
      approveScriptHandler();
    };
    $("addScene").onclick = () => {
      clearSceneAssets();
      project.scenes.push({ n: project.scenes.length + 1, narration: "", visual: "", text: "", edit: "" });
      window.renderScenes();
      save();
    };
    renderVoiceover();
    renderImageScenes();
    renderMotionScenes();
  }

  initialize();
})();
