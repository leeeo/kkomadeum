// Sign an unsigned release APK with the personal release key.
// The key material comes from a signing env file (KEYSTORE_BASE64, STORE_PASSWORD,
// KEY_PASSWORD, KEY_ALIAS) that is generated from the owner's secret store.
// Values are passed to apksigner through environment variables only and never printed.
//
// Usage: node tools/sign-release.mjs <signing.env> <unsigned.apk> <signed.apk>
// Requires ANDROID_HOME (or LOCALAPPDATA/Android/Sdk) with build-tools 35.0.0 and `java` on PATH.
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { join } from "node:path";
import { tmpdir } from "node:os";

const [envFile, input, output] = process.argv.slice(2);
if (!envFile || !input || !output) {
  console.error("Usage: node tools/sign-release.mjs <signing.env> <unsigned.apk> <signed.apk>");
  process.exit(2);
}
const sdk = process.env.ANDROID_HOME || join(process.env.LOCALAPPDATA || "", "Android", "Sdk");
const tools = join(sdk, "build-tools", "35.0.0");
const values = Object.fromEntries(readFileSync(envFile, "utf8").split(/\r?\n/)
  .map(line => /^([A-Z][A-Z0-9_]*)=(.*)$/.exec(line)).filter(Boolean).map(m => [m[1], m[2]]));
for (const key of ["KEYSTORE_BASE64", "STORE_PASSWORD", "KEY_PASSWORD", "KEY_ALIAS"]) {
  if (!values[key]) { console.error(`Missing ${key} in signing env`); process.exit(1); }
}
const run = (cmd, args, env) => {
  const r = spawnSync(cmd, args, { env: { ...process.env, ...env }, windowsHide: true, encoding: "utf8" });
  if (r.status !== 0) { console.error(`${args.includes("sign") ? "apksigner sign" : cmd} failed (details suppressed)`); process.exit(1); }
};
const dir = mkdtempSync(join(tmpdir(), "sign-"));
try {
  if (process.platform === "win32") {
    run("icacls.exe", [dir, "/inheritance:r", "/grant:r", `${process.env.USERNAME}:(F)`]);
  }
  const ks = join(dir, "release.p12");
  writeFileSync(ks, Buffer.from(values.KEYSTORE_BASE64, "base64"), { mode: 0o600 });
  const aligned = join(dir, "aligned.apk");
  run(join(tools, process.platform === "win32" ? "zipalign.exe" : "zipalign"), ["-P", "16", "-f", "4", input, aligned]);
  run("java", ["-jar", join(tools, "lib", "apksigner.jar"), "sign", "--ks", ks, "--ks-type", "PKCS12",
    "--ks-key-alias", values.KEY_ALIAS, "--ks-pass", "env:SIGN_STORE_PASS", "--key-pass", "env:SIGN_KEY_PASS",
    "--out", output, aligned], { SIGN_STORE_PASS: values.STORE_PASSWORD, SIGN_KEY_PASS: values.KEY_PASSWORD });
  console.log(`signed: ${output}`);
} finally {
  rmSync(dir, { recursive: true, force: true });
}
