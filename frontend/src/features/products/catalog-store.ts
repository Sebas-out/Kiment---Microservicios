export type Kind = "colors" | "sizes" | "categories";
export const normalizeCatalog = (value: string) => value.trim().normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("es");
