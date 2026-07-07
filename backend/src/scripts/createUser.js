'use strict';

/**
 * CLI to provision staff accounts, since there is no public signup.
 *
 * Usage:
 *   npm run create-user -- --username=staff1 --password=secret --name="Staff One" --role=staff
 */

const { getDb } = require('../db/connection');
const usersRepository = require('../db/repositories/usersRepository');
const authService = require('../services/authService');

function parseArgs(argv) {
  const args = {};
  for (const arg of argv) {
    const match = /^--([^=]+)=(.*)$/.exec(arg);
    if (match) {
      args[match[1]] = match[2];
    }
  }
  return args;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const { username, password, name } = args;
  const role = args.role || 'staff';

  const missing = ['username', 'password', 'name'].filter((key) => !args[key]);
  if (missing.length > 0) {
    // eslint-disable-next-line no-console
    console.error(
      `Missing required argument(s): ${missing.join(', ')}\n\n` +
        'Usage: npm run create-user -- --username=<username> --password=<password> --name="<Full Name>" [--role=staff]'
    );
    process.exitCode = 1;
    return;
  }

  if (password.length < 8) {
    // eslint-disable-next-line no-console
    console.error('Password must be at least 8 characters.');
    process.exitCode = 1;
    return;
  }

  getDb(); // ensures schema exists

  const existing = usersRepository.findByUsername(username);
  if (existing) {
    // eslint-disable-next-line no-console
    console.error(`A user with username "${username}" already exists (id=${existing.id}).`);
    process.exitCode = 1;
    return;
  }

  const passwordHash = await authService.hashPassword(password);
  const user = usersRepository.create({ username, passwordHash, name, role });

  // eslint-disable-next-line no-console
  console.log(
    `Created user: id=${user.id} username=${user.username} name="${user.name}" role=${user.role}`
  );
}

main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error('Failed to create user:', err.message);
  process.exitCode = 1;
});
