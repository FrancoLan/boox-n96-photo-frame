#!/usr/bin/env node

import { createHash, randomUUID } from 'node:crypto';
import { execFile } from 'node:child_process';
import { copyFile, mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);
const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const PROJECT_DIR = resolve(SCRIPT_DIR, '..');
const ACTIONS = new Set(['next', 'previous', 'sync', 'restart', 'disable', 'enable', 'update', 'diagnose']);

async function atomicWrite(path, contents, mode = 0o600) {
  await mkdir(dirname(path), { recursive: true });
  const temporary = `${path}.tmp.${process.pid}`;
  await writeFile(temporary, contents, { mode });
  await rename(temporary, path);
}

async function sha256File(path) {
  return createHash('sha256').update(await readFile(path)).digest('hex');
}

async function packageApk(controlDir) {
  const buildScript = join(PROJECT_DIR, 'android', 'build.sh');
  await execFileAsync(buildScript, [], { cwd: PROJECT_DIR, timeout: 120_000 });
  const apk = join(PROJECT_DIR, 'android', 'build', 'boox-photoframe.apk');
  const sha256 = await sha256File(apk);
  const bytes = (await stat(apk)).size;
  const packageDir = join(controlDir, 'boox-packages');
  await mkdir(packageDir, { recursive: true });
  await copyFile(apk, join(packageDir, `${sha256}.apk`));
  return { sha256, bytes, path: `/v1/boox/control/packages/${sha256}.apk` };
}

function commandText(command) {
  const rows = [
    '# boox-photoframe-control-v1',
    `id\t${command.id}`,
    `action\t${command.action}`,
    `created\t${command.created}`,
    `expires\t${command.expires}`,
  ];
  if (command.package) {
    rows.push(`packageSha256\t${command.package.sha256}`);
    rows.push(`packageBytes\t${command.package.bytes}`);
    rows.push(`packagePath\t${command.package.path}`);
  }
  rows.push('');
  return rows.join('\n');
}

async function main() {
  const [configPath, action] = process.argv.slice(2);
  if (!configPath || !action) throw new Error('usage: manage-boox.mjs CONFIG status|next|previous|sync|restart|disable|enable|update|diagnose|clear');
  const config = JSON.parse(await readFile(configPath, 'utf8'));
  const controlDir = join(resolve(config.dataDir), 'control');
  await mkdir(controlDir, { recursive: true });
  if (action === 'status') {
    try {
      console.log(await readFile(join(controlDir, 'boox-status.json'), 'utf8'));
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
      console.log(JSON.stringify({ online: false, detail: 'No BOOX status has been received yet.' }, null, 2));
    }
    return;
  }
  if (action === 'clear') {
    await atomicWrite(join(controlDir, 'boox-command.tsv'), '# boox-photoframe-control-v1\naction\tnone\n');
    console.log('Pending BOOX command cleared.');
    return;
  }
  if (!ACTIONS.has(action)) throw new Error(`unsupported BOOX action: ${action}`);
  const now = Math.floor(Date.now() / 1000);
  const command = { id: randomUUID(), action, created: now, expires: now + 15 * 60 };
  if (action === 'update') command.package = await packageApk(controlDir);
  await atomicWrite(join(controlDir, 'boox-command.tsv'), commandText(command));
  console.log(JSON.stringify(command, null, 2));
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
