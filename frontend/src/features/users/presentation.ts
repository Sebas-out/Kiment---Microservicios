const colors = ["#00b786", "#ff9800", "#ff275d", "#6a55ff", "#00b9ae", "#ab47ed", "#00acd0", "#ff7100"];
export function avatarColor(id: string) {
  let value = 0;
  for (const character of id) value = (value * 31 + character.charCodeAt(0)) | 0;
  return colors[(value >>> 0) % colors.length];
}
