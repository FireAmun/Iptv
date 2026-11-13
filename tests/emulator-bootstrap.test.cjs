// Exercise the checked-out CI action with fake commands and a fake clock only.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync(process.argv[2], 'utf8');

async function exercise(services, { unlockFails = false, pendingBoot = false } = {}) {
  const state = { polls: 0, checks: 0, unlocks: 0, published: false };
  const exports = {};
  const exec = async (command, args, options) => {
    if (command.startsWith('sh -c ')) return 0; // Never start an emulator.
    if (command.endsWith('shell getprop sys.boot_completed')) {
      state.polls++;
      options.listeners.stdout(Buffer.from(pendingBoot && state.polls === 1 ? '0\n' : '1\n'));
      return 0;
    }
    if (command.endsWith('shell service check input')) {
      const response = services[Math.min(state.checks++, services.length - 1)];
      if (response === 'transport-error') throw new Error('synthetic transport unavailable');
      state.published = response === 'found';
      options.listeners.stdout(Buffer.from(`Service input: ${response}\n`));
      return 0;
    }
    if (command.endsWith('shell input keyevent 82')) {
      state.unlocks++;
      assert.equal(state.published, true, 'unlock must follow a published input service');
      if (unlockFails) throw new Error('synthetic unlock failure');
      return 0;
    }
    throw new Error(`Unexpected fake command: ${command}`);
  };
  vm.runInNewContext(source, {
    exports,
    require(name) {
      if (name === '@actions/exec') return { exec };
      if (name === 'fs') return {};
      throw new Error(`Unexpected bootstrap dependency: ${name}`);
    },
    process: { platform: 'linux', env: { ANDROID_HOME: '/synthetic-sdk' } },
    console: { log() {}, warn() {} },
    setTimeout(callback) { callback(); },
  });
  let error;
  try {
    await exports.launchEmulator('test', false, false, false, 4, '-no-window', false, 5554);
  } catch (failure) {
    error = failure;
  }
  return { state, error };
}

(async () => {
  let result = await exercise(['not found', 'found']);
  assert.equal(result.error, undefined);
  assert.equal(result.state.checks, 2);
  assert.equal(result.state.unlocks, 1);

  result = await exercise(['transport-error', 'found']);
  assert.equal(result.error, undefined);
  assert.equal(result.state.checks, 2);
  assert.equal(result.state.unlocks, 1);

  result = await exercise(['not found']);
  assert.match(result.error?.message ?? '', /Timeout waiting for emulator to boot/);
  assert.equal(result.state.checks, 3);
  assert.equal(result.state.unlocks, 0);

  result = await exercise(['found'], { unlockFails: true });
  assert.match(result.error?.message ?? '', /synthetic unlock failure/);
  assert.equal(result.state.unlocks, 1);

  result = await exercise(['found'], { pendingBoot: true });
  assert.equal(result.error, undefined);
  assert.equal(result.state.polls, 2);
  assert.equal(result.state.checks, 1);
  assert.equal(result.state.unlocks, 1);
  console.log('CLOUD_BOOTSTRAP_SELF_TESTS_OK cases=5');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
