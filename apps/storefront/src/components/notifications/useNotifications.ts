import { useCallback, useEffect, useRef, useState } from 'react';
import { customerError } from '../../api/client';
import { notifications, type NotificationSnapshot } from '../../api/notifications';
import { connectNotifications, type NotificationConnection } from '../../api/notification-socket';

export function useNotifications() {
  const [snapshot, setSnapshot] = useState<NotificationSnapshot | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [connection, setConnection] = useState<NotificationConnection>('connecting');
  const [revision, setRevision] = useState(0);
  const mounted = useRef(false);

  const refresh = useCallback(() => { setRevision(value => value + 1); }, []);
  useEffect(() => {
    mounted.current = true;
    const stop = connectNotifications(window.location.origin, () => {
      setPageNo(1); refresh();
    }, setConnection);
    return () => { mounted.current = false; stop(); };
  }, [refresh]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    notifications.list(pageNo, controller.signal).then(result => {
      if (active) { setSnapshot(result); setError(''); }
    }).catch(reason => {
      if (active) setError(customerError(reason));
    }).finally(() => { if (active) setLoading(false); });
    // A later page/refresh invalidates this response even if fetch ignores abort.
    return () => { active = false; controller.abort(); };
  }, [pageNo, revision]);

  const markRead = async (id?: string) => {
    setSaving(true);
    try {
      if (id) await notifications.read(id); else await notifications.readAll();
      if (mounted.current) { setError(''); refresh(); }
      return true;
    } catch (reason) {
      if (mounted.current) setError(customerError(reason));
      return false;
    } finally { if (mounted.current) setSaving(false); }
  };

  return { snapshot, pageNo, setPageNo, loading, saving, error, connection, refresh, markRead };
}
