"use client";
import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Eye, EyeOff, LoaderCircle } from "lucide-react";
import { loginSchema, type LoginInput } from "../schema";
import { login, loginMessages } from "../client";
import { notify } from "@/lib/notifications";
export function LoginForm() {
  const [visible, setVisible] = useState(false);
  const router = useRouter();
  const submitting = useRef(false);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginInput>({
    resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" },
  });
  async function submit(values: LoginInput) {
    if (submitting.current) return;
    submitting.current = true;
    const id = "auth:login";
    notify.loading("Iniciando sesión…", { id });
    try {
      await login(values);
      notify.success(loginMessages.success, { id });
      router.replace("/dashboard");
      router.refresh();
    } catch (error) {
      notify.error(error instanceof Error ? error.message : loginMessages.server, { id });
    } finally { submitting.current = false; }
  }
  return <form className="login-form" noValidate onSubmit={event => { void handleSubmit(submit)(event); }} aria-busy={isSubmitting}>
    <div className="field">
      <label htmlFor="email">Email</label>
      <input id="email" type="email" autoComplete="username" placeholder="m@ejemplo.com"
        autoCapitalize="none" spellCheck={false} aria-invalid={!!errors.email}
        aria-describedby={errors.email ? "email-error" : undefined} {...register("email")} />
      {errors.email && <p id="email-error" className="field-error" role="alert">{errors.email.message}</p>}
    </div>
    <div className="field">
      <label htmlFor="password">Contraseña</label>
      <div className="password-wrap">
        <input id="password" type={visible ? "text" : "password"} autoComplete="current-password"
          aria-invalid={!!errors.password} aria-describedby={errors.password ? "password-error" : undefined}
          {...register("password")} />
        <button className="password-toggle" type="button" onClick={() => setVisible(!visible)}
          aria-label={visible ? "Ocultar contraseña" : "Mostrar contraseña"} aria-pressed={visible}>
          {visible ? <EyeOff size={17} strokeWidth={1.5} /> : <Eye size={17} strokeWidth={1.5} />}
        </button>
      </div>
      {errors.password && <p id="password-error" className="field-error" role="alert">{errors.password.message}</p>}
    </div>
    <button className="submit-button" type="submit" disabled={isSubmitting}>
      {isSubmitting && <LoaderCircle size={16} className="animate-spin" aria-hidden="true" />}
      {isSubmitting ? "Iniciando sesión…" : "Iniciar Sesión"}
    </button>
    <p className="version">Versión v1.2.0</p>
  </form>;
}
