import type { NextRequest } from "next/server";
import { AuthError } from "@/lib/auth/backend";
import { checkOrigin, failure, json } from "@/lib/auth/session";
import { accountFetch, avatarResponseError, privateAvatarResponse, privateHeaders, readAccountBody, requireAccountToken } from "@/features/account/server";
import { avatarFileProblem, matchesAvatarSignature, maxAvatarBytes } from "@/features/account/avatar-contract";

const endpoint = "users/me/avatar";

export async function GET() {
  try {
    const response = await accountFetch(endpoint, await requireAccountToken(), "GET");
    return privateAvatarResponse(response);
  } catch (error) { return failure(error); }
}

export async function POST(request: NextRequest) {
  try {
    checkOrigin(request, /^multipart\/form-data\s*;/i);
    const token = await requireAccountToken();
    const bytes = await readAccountBody(request, maxAvatarBytes + 16 * 1024);
    const parts = await new Response(bytes, { headers: { "Content-Type": request.headers.get("content-type")! } }).formData().catch(() => null);
    const file = parts?.get("file");
    if (!(file instanceof File) || Array.from(parts!.keys()).length !== 1)
      throw new AuthError(400, "Selecciona un único archivo de imagen.");
    const invalid = avatarFileProblem(file);
    if (invalid) throw new AuthError(invalid.status, invalid.message);
    if (!matchesAvatarSignature(file.type, new Uint8Array(await file.slice(0, 8).arrayBuffer())))
      throw new AuthError(400, "El contenido del archivo no corresponde a una imagen JPG o PNG.");
    const body = new FormData();
    body.set("file", file, file.type === "image/png" ? "avatar.png" : "avatar.jpg");
    const response = await accountFetch(endpoint, token, "POST", body);
    if (!response.ok) throw avatarResponseError(response.status);
    const data = await response.json().catch(() => null);
    if (response.status !== 200 || data?.hasProfileImage !== true)
      throw new AuthError(502, "El servidor no confirmó la actualización de la foto.");
    return json({ hasProfileImage: true });
  } catch (error) { return failure(error); }
}

export async function DELETE(request: NextRequest) {
  try {
    checkOrigin(request, null);
    const response = await accountFetch(endpoint, await requireAccountToken(), "DELETE");
    if (response.status !== 204) throw avatarResponseError(response.status);
    return new Response(null, { status: 204, headers: privateHeaders });
  } catch (error) { return failure(error); }
}
