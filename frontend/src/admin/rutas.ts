import type { TablaRutas } from '../shared/router';
import { render as agenda } from './agenda';
import { render as configuracion } from './configuracion';
import { render as login } from './login';
import { render as pacientes } from './pacientes';

/** Todas las rutas del area privada (relativas a la base '/admin'). */
export const rutasAdmin: TablaRutas = {
  '/login': login,
  '/agenda': agenda,
  '/configuracion': configuracion,
  '/pacientes': pacientes,
};

export const BASE_ADMIN = '/admin';
export const RAIZ_ADMIN = '/agenda';
