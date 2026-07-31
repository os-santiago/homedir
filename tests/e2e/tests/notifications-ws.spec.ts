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
    const helloAck = new Promise<string | null>((resolve) => {
      const timer = setTimeout(() => resolve(null), 5000);
      page.on('websocket', (ws) => {
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
    expect(ack).not.toBeNull();
    expect(ack).toContain('hello-ack');
  });
});
