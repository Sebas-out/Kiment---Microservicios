"use client";

import Image from "next/image";
import { Pause, Play } from "lucide-react";
import { useEffect, useState } from "react";

const photos = [
  { src: "/images/store.jpg", alt: "Interior de una tienda de ropa con prendas en percheros" },
  { src: "/images/store-02.jpg", alt: "Prendas en tonos cálidos colgadas en un perchero" },
];

/** Two photographs, six seconds each, with a gentle crossfade. */
export function StoreCarousel() {
  const [paused, setPaused] = useState(false);
  const [active, setActive] = useState(0);

  useEffect(() => {
    if (paused) return;
    const media = window.matchMedia("(min-width: 768px) and (prefers-reduced-motion: no-preference)");
    let timer: ReturnType<typeof setInterval> | undefined;
    function synchronize() {
      clearInterval(timer);
      if (media.matches) {
        timer = setInterval(() => setActive(index => (index + 1) % photos.length), 6000);
      }
    }
    synchronize();
    media.addEventListener("change", synchronize);
    return () => {
      clearInterval(timer);
      media.removeEventListener("change", synchronize);
    };
  }, [paused]);

  return (
    <aside className="photo-panel" aria-label="Fotografías de la tienda" aria-roledescription="carrusel" data-paused={paused}>
      <div className="carousel-images" aria-live="off">
        {photos.map((photo, index) => (
          <div className="carousel-slide" key={photo.src} data-active={active === index} aria-hidden={active !== index}>
            <Image src={photo.src} alt={photo.alt} fill priority sizes="50vw" className="store-photo" />
          </div>
        ))}
      </div>
      <button type="button" className="carousel-control" onClick={() => setPaused(!paused)}
        aria-label={paused ? "Reanudar carrusel" : "Pausar carrusel"} aria-pressed={paused}>
        {paused ? <Play size={15} aria-hidden="true" /> : <Pause size={15} aria-hidden="true" />}
      </button>
    </aside>
  );
}
