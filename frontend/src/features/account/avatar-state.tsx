"use client";

import { createContext, useContext, useState, type ReactNode } from "react";
import { avatarSource } from "./avatar-contract";

const AvatarContext = createContext<{ src: string | null; update: (present: boolean) => void }>({ src: null, update: () => {} });

export function AccountAvatarProvider({ present, revision, children }: { present: boolean; revision: string; children: ReactNode }) {
  const serverSrc = avatarSource(present, revision);
  const [override, setOverride] = useState<{ previous: string | null; src: string | null }>();
  const src = override && override.previous === serverSrc ? override.src : serverSrc;
  return <AvatarContext.Provider value={{ src, update: next => setOverride({
    previous: serverSrc, src: avatarSource(next, Date.now().toString(36) + "-" + Math.random().toString(36).slice(2)),
  }) }}>{children}</AvatarContext.Provider>;
}

export function useAccountAvatar() { return useContext(AvatarContext); }

export function AccountAvatar({ firstName, lastName, className, online = false, preview }: {
  firstName: string; lastName: string; className: string; online?: boolean; preview?: string;
}) {
  const { src } = useAccountAvatar();
  const url = preview ?? src;
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const initials = `${firstName.trim().charAt(0)}${lastName.trim().charAt(0)}`.toUpperCase();
  return <span className={className} aria-hidden="true">
    <span>{initials}</span>
    {/* An authenticated, same-origin image must bypass the Next image optimizer. */}
    {/* eslint-disable-next-line @next/next/no-img-element */}
    {url && url !== failedUrl && <img key={url} src={url} alt="" onError={() => setFailedUrl(url)} />}
    {online && <i />}
  </span>;
}
