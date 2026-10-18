import { useCallback, useEffect, useState } from 'react';
import { customerError } from '../../api/client';
import { pollFinancial } from '../../api/wallet-payment';

export function useFinancialLoad<T>(read: (signal: AbortSignal) => Promise<T>, pending: (value: T) => boolean) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision(value => value + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError('');
    void pollFinancial(read, value => { setData(value); setLoading(false); setError(''); }, controller.signal, pending)
      .catch(error => { if (!controller.signal.aborted) { setError(customerError(error)); setLoading(false); } });
    return () => controller.abort();
  }, [read, pending, revision]);
  return { data, setData, error, loading, refresh };
}
