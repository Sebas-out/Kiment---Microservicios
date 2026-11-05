export function limaDate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Lima", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  return ["year", "month", "day"].map(key => parts.find(part => part.type === key)?.value).join("-");
}
export function shiftDay(day: string, offset: number): string {
  const date = new Date(`${day}T12:00:00Z`); date.setUTCDate(date.getUTCDate() + offset); return date.toISOString().slice(0, 10);
}
export function presetRange(preset: string, today: string) {
  if (preset === "yesterday") return { from: shiftDay(today, -1), to: shiftDay(today, -1) };
  return { from: shiftDay(today, preset === "week" ? -6 : preset === "month" ? -29 : 0), to: today };
}
export function validRange(from: string, to: string): boolean {
  const valid = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(`${value}T12:00:00Z`)) && new Date(`${value}T12:00:00Z`).toISOString().slice(0, 10) === value;
  return valid(from) && valid(to) && from <= to;
}
export const formatMoney = (amount: number) => `S/ ${new Intl.NumberFormat("es-PE", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount)}`;
export const formatCount = (amount: number) => new Intl.NumberFormat("es-PE").format(amount);
