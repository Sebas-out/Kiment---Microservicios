import { LoginSessionNotice } from "@/features/auth/components/login-session-notice";
import { StoreCarousel } from "@/components/store-carousel";
import { Brand } from "@/components/brand";
import { ThemeToggle } from "@/components/theme-toggle";
import { LoginForm } from "@/features/auth/components/login-form";
export default function LoginPage() {
  return <main className="login-shell">
    <LoginSessionNotice />
    <section className="login-panel" aria-label="Inicio de sesión">
      <header className="login-header"><Brand /><ThemeToggle /></header>
      <div className="login-content">
        <h1 className="sr-only">Iniciar sesión en KIMENTS</h1>
        <Brand large /><LoginForm />
      </div>
    </section>
    <StoreCarousel />
  </main>;
}
