#!/usr/bin/env node
/**
 * GitHub Webhook Handler for AI SDLC
 *
 * Receives GitHub webhook events and triggers immediate worker execution,
 * eliminating the 3-minute timer delay for event processing.
 *
 * Supported events:
 * - issues: opened, labeled, unlabeled
 * - pull_request: opened, closed, synchronize, ready_for_review
 * - check_suite: completed
 */

import express from 'express';
import { execFile } from 'child_process';
import { promisify } from 'util';
import crypto from 'crypto';

const execFileAsync = promisify(execFile);

const app = express();
const PORT = process.env.PORT || 3000;
const WORKER_SCRIPT = process.env.WORKER_SCRIPT || '/home/homedir-sdlc/.local/bin/homedir-sdlc-worker.sh';
const WEBHOOK_SECRET = process.env.WEBHOOK_SECRET || '';
const ALLOWED_REPO = process.env.ALLOWED_REPO || 'os-santiago/homedir';

// Middleware to parse JSON payloads
app.use(express.json());

// Request logging middleware
app.use((req, res, next) => {
  const timestamp = new Date().toISOString();
  console.log(`[${timestamp}] ${req.method} ${req.path} - ${req.ip}`);
  next();
});

/**
 * Verify GitHub webhook signature
 * @param {string} payload - Raw request body
 * @param {string} signature - GitHub signature from header
 * @returns {boolean} True if signature is valid
 */
function verifyGitHubSignature(payload, signature) {
  if (!WEBHOOK_SECRET) {
    console.warn('WEBHOOK_SECRET not set - skipping signature verification');
    return true;
  }

  if (!signature) {
    return false;
  }

  const hmac = crypto.createHmac('sha256', WEBHOOK_SECRET);
  const digest = 'sha256=' + hmac.update(payload).digest('hex');

  return crypto.timingSafeEqual(
    Buffer.from(signature),
    Buffer.from(digest)
  );
}

/**
 * Trigger worker script with event command
 * @param {string} command - Event command (e.g., 'issue-opened')
 * @param {object} payload - Event payload
 * @returns {Promise<object>} Worker execution result
 */
async function triggerWorker(command, payload) {
  const timestamp = new Date().toISOString();

  try {
    // Create temporary payload file
    const payloadFile = `/tmp/webhook-payload-${Date.now()}.json`;
    const fs = await import('fs');
    await fs.promises.writeFile(payloadFile, JSON.stringify(payload, null, 2));

    console.log(`[${timestamp}] Triggering worker: ${command}`);

    const { stdout, stderr } = await execFileAsync(
      WORKER_SCRIPT,
      [command, payloadFile],
      {
        timeout: 60000,
        maxBuffer: 10 * 1024 * 1024, // 10MB
        env: process.env
      }
    );

    // Clean up payload file
    await fs.promises.unlink(payloadFile).catch(() => {});

    console.log(`[${timestamp}] Worker completed: ${command}`);

    return {
      success: true,
      command,
      stdout: stdout.substring(0, 1000), // Limit output
      stderr: stderr.substring(0, 1000)
    };
  } catch (error) {
    console.error(`[${timestamp}] Worker failed: ${command}`, error.message);

    return {
      success: false,
      command,
      error: error.message,
      code: error.code
    };
  }
}

/**
 * Health check endpoint
 */
app.get('/health', (req, res) => {
  res.json({
    status: 'healthy',
    service: 'homedir-sdlc-webhook-handler',
    version: '1.0.0',
    uptime: process.uptime(),
    timestamp: new Date().toISOString()
  });
});

/**
 * GitHub webhook endpoint
 */
app.post('/webhook/github', async (req, res) => {
  const timestamp = new Date().toISOString();
  const event = req.headers['x-github-event'];
  const signature = req.headers['x-hub-signature-256'];
  const delivery = req.headers['x-github-delivery'];

  console.log(`[${timestamp}] Webhook received: ${event} (delivery: ${delivery})`);

  // Verify signature
  const rawBody = JSON.stringify(req.body);
  if (!verifyGitHubSignature(rawBody, signature)) {
    console.error(`[${timestamp}] Invalid signature for delivery ${delivery}`);
    return res.status(401).json({ error: 'Invalid signature' });
  }

  const payload = req.body;
  const repo = payload.repository?.full_name;

  // Verify repository
  if (repo !== ALLOWED_REPO) {
    console.warn(`[${timestamp}] Webhook from unauthorized repo: ${repo}`);
    return res.status(403).json({ error: 'Unauthorized repository' });
  }

  // Respond quickly to GitHub (must respond within 10 seconds)
  res.status(202).json({
    received: true,
    event,
    delivery,
    timestamp
  });

  // Process event asynchronously
  let command = null;
  let shouldTrigger = false;

  switch (event) {
    case 'issues':
      if (payload.action === 'opened') {
        command = 'issue-opened';
        shouldTrigger = true;
      } else if (payload.action === 'labeled' || payload.action === 'unlabeled') {
        // Trigger reconciliation for label changes (might affect admission)
        command = 'issue-labeled';
        shouldTrigger = true;
      }
      break;

    case 'pull_request':
      if (payload.action === 'opened') {
        command = 'pr-opened';
        shouldTrigger = true;
      } else if (payload.action === 'closed' && payload.pull_request?.merged) {
        command = 'pr-closed';
        shouldTrigger = true;
      } else if (payload.action === 'synchronize') {
        command = 'pr-synchronized';
        shouldTrigger = true;
      } else if (payload.action === 'ready_for_review') {
        command = 'pr-ready-for-review';
        shouldTrigger = true;
      }
      break;

    case 'check_suite':
      if (payload.action === 'completed') {
        command = 'checks-completed';
        shouldTrigger = true;
      }
      break;

    case 'issue_comment':
      command = 'issue-commented';
      shouldTrigger = true;
      break;

    case 'pull_request_review':
      command = 'pr-review-submitted';
      shouldTrigger = true;
      break;

    default:
      console.log(`[${timestamp}] Unhandled event: ${event} (action: ${payload.action})`);
  }

  // Trigger worker if applicable
  if (shouldTrigger && command) {
    triggerWorker(command, payload).catch(error => {
      console.error(`[${timestamp}] Background worker trigger failed:`, error);
    });
  }
});

/**
 * Catch-all for invalid endpoints
 */
app.use((req, res) => {
  res.status(404).json({ error: 'Not found' });
});

/**
 * Error handler
 */
app.use((err, req, res, next) => {
  console.error('Server error:', err);
  res.status(500).json({ error: 'Internal server error' });
});

/**
 * Start server
 */
app.listen(PORT, () => {
  console.log(`[${new Date().toISOString()}] Webhook handler listening on port ${PORT}`);
  console.log(`Worker script: ${WORKER_SCRIPT}`);
  console.log(`Allowed repo: ${ALLOWED_REPO}`);
  console.log(`Signature verification: ${WEBHOOK_SECRET ? 'enabled' : 'disabled (WARNING)'}`);
});

/**
 * Graceful shutdown
 */
process.on('SIGTERM', () => {
  console.log('[SIGTERM] Shutting down gracefully...');
  process.exit(0);
});

process.on('SIGINT', () => {
  console.log('[SIGINT] Shutting down gracefully...');
  process.exit(0);
});
