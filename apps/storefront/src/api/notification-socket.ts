export type NotificationConnection = 'connecting' | 'connected' | 'reconnecting';

export function connectNotifications(origin: string, onChange: () => void,
  onState: (state: NotificationConnection) => void): () => void {
  const url = new URL('/api/v1/notifications/ws', origin);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  let stopped = false;
  let socket: WebSocket | undefined;
  let retry: ReturnType<typeof setTimeout> | undefined;
  let failures = 0;

  function scheduleRetry() {
    if (stopped) return;
    onState('reconnecting');
    retry = setTimeout(connect, Math.min(1000 * 2 ** failures++, 30000));
  }

  function connect() {
    retry = undefined;
    if (stopped) return;
    onState(failures ? 'reconnecting' : 'connecting');
    try { socket = new WebSocket(url.toString()); }
    catch { scheduleRetry(); return; }
    socket.onopen = () => {
      if (stopped) return;
      failures = 0;
      onState('connected');
      // Reconcile events missed while offline, including the initial handshake window.
      onChange();
    };
    socket.onmessage = event => {
      if (stopped) return;
      try { if (JSON.parse(event.data).type === 'notifications.changed') onChange(); }
      catch { /* Ignore malformed/non-notification frames. */ }
    };
    socket.onclose = scheduleRetry;
    socket.onerror = () => socket?.close();
  }

  connect();
  return () => {
    stopped = true;
    if (retry !== undefined) clearTimeout(retry);
    socket?.close();
  };
}
