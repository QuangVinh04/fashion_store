import { useEffect, useRef, useState } from 'react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import { Dialog, DialogClose, DialogDescription, DialogOverlay, DialogPortal, DialogTitle } from '../../app/components/ui/dialog';
import { ProductPhoto } from '../StoreUI';

export type GalleryPhoto = { key: string; src: string; alt: string };

export default function ProductGallery({ photos, name }: { photos: GalleryPhoto[]; name: string }) {
  const [open, setOpen] = useState(false);
  const [index, setIndex] = useState(0);
  const strip = useRef<HTMLDivElement>(null);
  const triggers = useRef<(HTMLButtonElement | null)[]>([]);
  const opener = useRef(0);
  const currentIndex = useRef(0);
  const frame = useRef(0);
  const safePhotos = photos.length ? photos : [{ key: 'empty', src: '', alt: name }];

  function move(next: number, smooth = true) {
    const current = Math.max(0, Math.min(safePhotos.length - 1, next));
    currentIndex.current = current;
    setIndex(current);
    const container = strip.current;
    const slide = container?.children[current] as HTMLElement | undefined;
    if (container && slide) container.scrollTo({
      left: slide.offsetLeft - (container.clientWidth - slide.clientWidth) / 2,
      behavior: smooth && !matchMedia('(prefers-reduced-motion: reduce)').matches ? 'smooth' : 'instant',
    });
  }

  useEffect(() => {
    if (!open) return;
    const id = requestAnimationFrame(() => move(opener.current, false));
    const resize = () => move(currentIndex.current, false);
    window.addEventListener('resize', resize);
    return () => { cancelAnimationFrame(id); cancelAnimationFrame(frame.current); window.removeEventListener('resize', resize); };
  }, [open]);

  return <Dialog open={open} onOpenChange={setOpen}>
    <div className="pd-gallery">
      {safePhotos.map((photo, i) => <button key={photo.key} type="button" className="pd-gallery-photo" ref={el => { triggers.current[i] = el; }}
        aria-label={`Xem ảnh ${i + 1} toàn màn hình`} aria-haspopup="dialog" disabled={!photo.src}
        onClick={() => { opener.current = i; setIndex(i); setOpen(true); }}>
        <ProductPhoto src={photo.src} name={photo.alt} />
      </button>)}
    </div>
    <DialogPortal>
      <DialogOverlay />
      <DialogPrimitive.Content className="pd-photo-dialog" onCloseAutoFocus={event => { event.preventDefault(); triggers.current[opener.current]?.focus(); }}
        onKeyDown={event => {
          if (event.key === 'ArrowRight') { event.preventDefault(); move(currentIndex.current + 1); }
          if (event.key === 'ArrowLeft') { event.preventDefault(); move(currentIndex.current - 1); }
          if (event.key === 'Home') { event.preventDefault(); move(0); }
          if (event.key === 'End') { event.preventDefault(); move(safePhotos.length - 1); }
        }}>
        <DialogTitle className="sr-only">Ảnh sản phẩm</DialogTitle>
        <DialogDescription className="sr-only">{name}. Vuốt hoặc dùng phím mũi tên để chuyển ảnh.</DialogDescription>
        <div className="pd-photo-top"><DialogClose className="pd-icon" aria-label="Đóng ảnh toàn màn hình"><X size={20} /></DialogClose></div>
        <div className="pd-photo-middle">
          <div className="pd-photo-strip" ref={strip} tabIndex={0} role="region" aria-label="Các ảnh sản phẩm"
            onScroll={() => {
              cancelAnimationFrame(frame.current);
              frame.current = requestAnimationFrame(() => {
                const container = strip.current;
                if (!container) return;
                const center = container.scrollLeft + container.clientWidth / 2;
                const distances = Array.from(container.children, child => Math.abs((child as HTMLElement).offsetLeft + child.clientWidth / 2 - center));
                const nearest = distances.indexOf(Math.min(...distances));
                currentIndex.current = nearest;
                setIndex(nearest);
              });
            }}>
            {safePhotos.map(photo => <div key={photo.key} className="pd-photo-slide"><ProductPhoto src={photo.src} name={photo.alt} /></div>)}
          </div>
          {safePhotos.length > 1 && <>
            <button type="button" className="pd-photo-arrow pd-photo-prev" aria-label="Ảnh trước" disabled={index === 0} onClick={() => move(index - 1)}><ChevronLeft /></button>
            <button type="button" className="pd-photo-arrow pd-photo-next" aria-label="Ảnh tiếp theo" disabled={index === safePhotos.length - 1} onClick={() => move(index + 1)}><ChevronRight /></button>
          </>}
        </div>
        <div className="pd-photo-bottom"><span className="pd-photo-count" aria-live="polite">{index + 1} / {safePhotos.length}</span></div>
      </DialogPrimitive.Content>
    </DialogPortal>
  </Dialog>;
}
