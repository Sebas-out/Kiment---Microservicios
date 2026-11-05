import { z } from "zod";
export const loginSchema = z.object({
  email: z.string().trim().max(254).pipe(z.email("Ingresa un correo electrónico válido.")),
  password: z.string().min(1, "Ingresa tu contraseña.").max(128, "La contraseña es demasiado larga.").refine(value => value.trim().length > 0, "Ingresa tu contraseña."),
});
export type LoginInput = z.infer<typeof loginSchema>;
