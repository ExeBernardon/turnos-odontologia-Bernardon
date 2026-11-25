import type { Instante } from '../api/types';

export interface Sesion {
  readonly token: string;
  readonly tipo: 'Bearer';
  readonly expiraEn?: Instante;
  readonly nombre?: string;
}

const CLAVE_SESION = 'turnos.sesion';
const CLAVE_TOKEN = 'turnos.token';

function leer(clave: string): string | null {
  try {
    return window.localStorage.getItem(clave);
  } catch {
    return null;
  }
}

function escribir(clave: string, valor: string): void {
  try {
    window.localStorage.setItem(clave, valor);
  } catch {
    return;
  }
}

function borrar(clave: string): void {
  try {
    window.localStorage.removeItem(clave);
  } catch {
    return;
  }
}

function esSesion(dato: unknown): dato is Sesion {
  if (typeof dato !== 'object' || dato === null) {
    return false;
  }
  const s = dato as Record<string, unknown>;
  return (
    typeof s.token === 'string' &&
    s.token.length > 0 &&
    s.tipo === 'Bearer' &&
    (s.expiraEn === undefined || typeof s.expiraEn === 'string') &&
    (s.nombre === undefined || typeof s.nombre === 'string')
  );
}

export function obtenerSesion(): Sesion | null {
  const guardada = leer(CLAVE_SESION);
  if (guardada !== null) {
    let dato: unknown;
    try {
      dato = JSON.parse(guardada) as unknown;
    } catch {
      dato = null;
    }
    if (esSesion(dato)) {
      return dato;
    }
  }

  const legacy = leer(CLAVE_TOKEN);
  return legacy !== null && legacy !== '' ? { token: legacy, tipo: 'Bearer' } : null;
}

export function guardarSesion(sesion: Sesion): void {
  escribir(CLAVE_SESION, JSON.stringify(sesion));
  borrar(CLAVE_TOKEN);
}

export function borrarSesion(): void {
  borrar(CLAVE_SESION);
  borrar(CLAVE_TOKEN);
}

export function sesionValida(): boolean {
  const sesion = obtenerSesion();
  if (sesion === null || sesion.token === '') {
    return false;
  }
  if (sesion.expiraEn === undefined) {
    return true;
  }
  const vencimiento = Date.parse(sesion.expiraEn);
  return !Number.isNaN(vencimiento) && vencimiento > Date.now();
}

export function obtenerToken(): string | null {
  return obtenerSesion()?.token ?? null;
}

export function guardarToken(token: string): void {
  guardarSesion({ token, tipo: 'Bearer' });
}

export function borrarToken(): void {
  borrarSesion();
}
