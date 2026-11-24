import type { TablaRutas } from '../shared/router';
import { render as cancelar } from './cancelar';
import { render as confirmar } from './confirmar';
import { render as reservar } from './reservar';

/** Todas las rutas del area publica (relativas a la base ''). Los changes de front solo reemplazan pantallas. */
export const rutasPublicas: TablaRutas = {
  '/reservar': reservar,
  '/turno/confirmar': confirmar,
  '/turno/cancelar': cancelar,
};

export const RAIZ_PUBLICA = '/reservar';
