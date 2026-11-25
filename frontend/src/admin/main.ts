import '../styles/base.css';
import './admin.css';
import { crearRouter } from '../shared/router';
import { registrarManejadorExpiracion } from '../api/cliente';
import { borrarSesion, sesionValida } from '../shared/sesion';
import { BASE_ADMIN, RAIZ_ADMIN, rutasAdmin } from './rutas';

const contenedor = document.getElementById('app');
if (contenedor !== null) {
  const router = crearRouter({
    base: BASE_ADMIN,
    raiz: RAIZ_ADMIN,
    rutas: rutasAdmin,
    contenedor,
    redirigir: (ruta) => (ruta === '/login' || sesionValida() ? null : '/login'),
  });
  registrarManejadorExpiracion(() => {
    borrarSesion();
    router.navegar('/login');
  });
  router.iniciar();
}
