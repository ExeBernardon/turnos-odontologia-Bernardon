import '../styles/base.css';
import { crearRouter } from '../shared/router';
import { BASE_ADMIN, RAIZ_ADMIN, rutasAdmin } from './rutas';

const contenedor = document.getElementById('app');
if (contenedor !== null) {
  crearRouter({ base: BASE_ADMIN, raiz: RAIZ_ADMIN, rutas: rutasAdmin, contenedor }).iniciar();
}
