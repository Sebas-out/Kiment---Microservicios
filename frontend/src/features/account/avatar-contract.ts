export const maxAvatarBytes = 2 * 1024 * 1024;
export const avatarMediaTypes = ["image/jpeg", "image/png"] as const;
export const avatarPath = "/api/account/avatar";

export function isAvatarType(type: string) {
  return avatarMediaTypes.some(value => value === type);
}

export function avatarFileProblem(file: Pick<File, "name" | "type" | "size">) {
  if (file.size > maxAvatarBytes) return { status: 413, message: "La foto debe pesar como máximo 2 MB." };
  if (!file.size || !(
    (file.type === "image/jpeg" && /\.jpe?g$/i.test(file.name)) ||
    (file.type === "image/png" && /\.png$/i.test(file.name))
  )) return { status: 400, message: "Selecciona una imagen válida en formato JPG o PNG." };
}

export function matchesAvatarSignature(type: string, bytes: Uint8Array) {
  const signature = type === "image/png" ? [137, 80, 78, 71, 13, 10, 26, 10]
    : type === "image/jpeg" ? [255, 216, 255] : [];
  return signature.length > 0 && signature.every((value, index) => bytes[index] === value);
}

export function avatarSource(present: boolean, revision: string) {
  return present ? `${avatarPath}?v=${encodeURIComponent(revision)}` : null;
}
