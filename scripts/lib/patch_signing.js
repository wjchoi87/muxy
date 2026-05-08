#!/usr/bin/env node
// Patch a freshly-generated android/app/build.gradle so that the release
// build type signs with our upload keystore (env-driven), instead of debug.
//
// Usage: node scripts/lib/patch_signing.js <path-to-build.gradle>

const fs = require("fs");

const filePath = process.argv[2];
if (!filePath) {
  console.error("usage: patch_signing.js <build.gradle>");
  process.exit(1);
}

let src = fs.readFileSync(filePath, "utf8");

// Locate a top-level block by name (signingConfigs, buildTypes, etc.)
// and return the [openBracePos, closeBracePos] inclusive span.
function findBlock(text, name) {
  const re = new RegExp("\\b" + name + "\\s*\\{");
  const m = re.exec(text);
  if (!m) return null;
  const open = m.index + m[0].length - 1;
  let depth = 1;
  for (let i = open + 1; i < text.length; i++) {
    if (text[i] === "{") depth++;
    else if (text[i] === "}") {
      depth--;
      if (depth === 0) return [open, i];
    }
  }
  return null;
}

// Locate a nested named block within an outer span (e.g. buildTypes -> release).
function findNamedSubBlock(text, outerOpen, outerClose, name) {
  const inner = text.slice(outerOpen + 1, outerClose);
  const re = new RegExp("\\b" + name + "\\s*\\{");
  const m = re.exec(inner);
  if (!m) return null;
  const open = outerOpen + 1 + m.index + m[0].length - 1;
  let depth = 1;
  for (let i = open + 1; i < text.length; i++) {
    if (text[i] === "{") depth++;
    else if (text[i] === "}") {
      depth--;
      if (depth === 0) return [open, i];
    }
  }
  return null;
}

// 1. Add `release` inside signingConfigs if it isn't there.
const signingConfigs = findBlock(src, "signingConfigs");
if (!signingConfigs) {
  console.error("Could not find signingConfigs block in", filePath);
  process.exit(1);
}

const releaseSigning = findNamedSubBlock(
  src,
  signingConfigs[0],
  signingConfigs[1],
  "release"
);

if (!releaseSigning) {
  const releaseBlock =
    "\n        release {\n" +
    "            storeFile file(\"upload-keystore.jks\")\n" +
    "            storePassword System.getenv(\"ANDROID_KEY_STORE_PASSWORD\")\n" +
    "            keyAlias System.getenv(\"ANDROID_KEY_ALIAS\")\n" +
    "            keyPassword System.getenv(\"ANDROID_KEY_PASSWORD\")\n" +
    "        }";
  src =
    src.slice(0, signingConfigs[1]) +
    releaseBlock +
    "\n    " +
    src.slice(signingConfigs[1]);
}

// 2. Inside buildTypes -> release, replace `signingConfig signingConfigs.X`
//    with `signingConfig signingConfigs.release`.
const buildTypes = findBlock(src, "buildTypes");
if (!buildTypes) {
  console.error("Could not find buildTypes block in", filePath);
  process.exit(1);
}

const releaseBuildType = findNamedSubBlock(
  src,
  buildTypes[0],
  buildTypes[1],
  "release"
);
if (!releaseBuildType) {
  console.error("Could not find buildTypes.release in", filePath);
  process.exit(1);
}

const before = src.slice(0, releaseBuildType[0] + 1);
const inside = src.slice(releaseBuildType[0] + 1, releaseBuildType[1]);
const after = src.slice(releaseBuildType[1]);
const patchedInside = inside.replace(
  /signingConfig\s+signingConfigs\.\w+/,
  "signingConfig signingConfigs.release"
);
if (patchedInside === inside) {
  // No existing line: add one.
  src = before + "\n            signingConfig signingConfigs.release" + inside + after;
} else {
  src = before + patchedInside + after;
}

fs.writeFileSync(filePath, src);
console.log("Patched", filePath);
