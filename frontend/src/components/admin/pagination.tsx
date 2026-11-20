"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import styles from "./pagination.module.css";

type Props = {
  total: number;
  label: string;
  page: number;
  pages: number;
  disabled?: boolean;
  onChange: (page: number) => void;
};

export function Pagination({ total, label, page, pages, disabled = false, onChange }: Props) {
  const last = Math.max(1, pages);
  const current = Math.max(1, Math.min(page, last));
  const start = Math.max(1, Math.min(current - 2, last - 4));
  const numbers = Array.from({ length: Math.min(5, last) }, (_, index) => start + index);
  return <footer className={styles.pagination}>
    <span aria-live="polite">{total} {label} — Página {current} de {last}</span>
    <nav aria-label={"Paginación de " + label}>
      <button type="button" aria-label="Página anterior" disabled={disabled || current === 1} onClick={() => onChange(current - 1)}><ChevronLeft size={14} /></button>
      {start > 1 && <><button type="button" disabled={disabled} onClick={() => onChange(1)}>1</button>{start > 2 && <span>…</span>}</>}
      {numbers.map(number => <button type="button" key={number} aria-label={"Página " + number} aria-current={number === current ? "page" : undefined} disabled={disabled} onClick={() => onChange(number)}>{number}</button>)}
      {numbers[numbers.length - 1] < last && <>{numbers[numbers.length - 1] < last - 1 && <span>…</span>}<button type="button" disabled={disabled} onClick={() => onChange(last)}>{last}</button></>}
      <button type="button" aria-label="Página siguiente" disabled={disabled || current === last} onClick={() => onChange(current + 1)}><ChevronRight size={14} /></button>
    </nav>
  </footer>;
}
