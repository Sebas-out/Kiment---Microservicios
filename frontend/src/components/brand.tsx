export function Brand({ large = false }: { large?: boolean }) {
  return <div className={`brand ${large ? "brand-large" : ""}`} aria-label="Kiments, tienda de ropa">
    <span className="brand-name">KIMENTS</span>
    <span className="brand-caption">TIENDA DE ROPA</span>
  </div>;
}
