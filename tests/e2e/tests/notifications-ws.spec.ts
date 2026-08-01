import { expect, test } from '@playwright/test';

/**
 * Global notifications WebSocket.
 *
 * The page bundles open a WebSocket to /ws/global-notifications and send a
 * `hello` handshake. The server answers with a `hello-ack` and optionally a
 * backlog of recent notifications.
 */
test.describe('global notifications WebSocket', () => {
  test('opens the WebSocket and completes the hello handshake', async ({ page }) => {
    const helloAck = new Promise<string>((resolve, reject) => {
      // Reject on timeout so Playwright reports a clear "timed out waiting for
      // hello-ack" error instead of a bare null assertion, and lets the
      // configured retries kick in.
      const timer = setTimeout(
        () => reject(new Error('timeout waiting for hello-ack')),
        15000,
      );
      page.on('websocket', (ws) => {
        // Only accept the global notifications socket; ignore frames from any
        // other WebSocket endpoint on the page.
        if (new URL(ws.url()).pathname !== '/ws/global-notifications') return;
        ws.on('framereceived', (frame) => {
          const payload = frame.payload.toString();
          if (payload.includes('"hello-ack"')) {
            clearTimeout(timer);
            resolve(payload);
          }
        });
      });
    });

    await page.goto('/');

    // The page sends the hello handshake on open (see core-bundle.js).
    const ack = await helloAck;
    expect(ack).toContain('hello-ack');
  });
});
