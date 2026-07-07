'use strict';

const config = require('./config/env');
const logger = require('./utils/logger');
const { getDb } = require('./db/connection');
const { createApp } = require('./app');

// Ensures the SQLite file and all tables exist before we start accepting
// traffic (first-run bootstrap).
getDb();

const app = createApp();

const server = app.listen(config.port, () => {
  logger.info(
    { port: config.port, env: config.env },
    `Donation Terminal backend listening on port ${config.port}`
  );
  logger.info(
    'NOTE: this process assumes TLS termination happens upstream (reverse proxy / load balancer). It does not terminate HTTPS itself.'
  );
});

function shutdown(signal) {
  logger.info({ signal }, 'Shutting down gracefully');
  server.close(() => {
    process.exit(0);
  });
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

module.exports = server;
