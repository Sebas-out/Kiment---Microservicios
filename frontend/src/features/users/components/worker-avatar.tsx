"use client";

import { useState } from "react";
import type { UserRecord } from "../contracts";
import { avatarColor } from "../presentation";

export function WorkerAvatar({ user }: { user: UserRecord }) {
  const [failed, setFailed] = useState<string>();
  const src = user.hasProfileImage
    ? `/api/management/users/${user.id}/avatar?v=${encodeURIComponent(user.updatedAt)}`
    : undefined;
  return <span className="ku-avatar" style={{ backgroundColor: avatarColor(user.id) }} aria-hidden="true">
    {user.firstName.charAt(0)}{user.lastName.charAt(0)}
    {/* Private images must use the browser's authenticated request directly. */}
    {/* eslint-disable-next-line @next/next/no-img-element */}
    {src && failed !== src && <img key={src} src={src} alt="" loading="lazy" onError={() => setFailed(src)} />}
  </span>;
}
