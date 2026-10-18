import { useEffect, useState } from 'react';
import { store } from '../../api/store';
import { media } from '../../api/client';
import type { Checkout } from '../../api/types';

export type OrderProductPhotos = Record<string, { id: string; image: string }>;

/** One variant batch and one request per distinct product for the visible page. */
export function useOrderProductPhotos(items: Checkout['items'], supplied?: OrderProductPhotos) {
  const [photos, setPhotos] = useState<OrderProductPhotos>({});
  const variants = [...new Set(items.map(item => item.variantId))].sort().join('|');
  useEffect(() => {
    if (supplied) return;
    let active = true;
    setPhotos({});
    if (!variants) return;
    void store.variantSnapshots(variants.split('|')).then(async rows => {
      const ids = [...new Set(rows.map(row => row.productId))];
      const results = await Promise.allSettled(ids.map(id => store.product(id)));
      if (!active) return;
      const images = new Map(results.flatMap((result, index) => result.status === 'fulfilled'
        ? [[ids[index], result.value.thumbnailUrl || media(result.value.thumbnailMediaId)] as const] : []));
      setPhotos(Object.fromEntries(rows.map(row => [row.variantId, { id: row.productId, image: images.get(row.productId) || '' }])));
    }).catch(() => {});
    return () => { active = false; };
  }, [variants, supplied]);
  return supplied ?? photos;
}
