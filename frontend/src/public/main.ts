import '../styles/base.css';
import { crearRouter } from '../shared/router';
import { RAIZ_PUBLICA, rutasPublicas } from './rutas';

const contenedor = document.getElementById('app');
if (contenedor !== null) {
  crearRouter({ base: '', raiz: RAIZ_PUBLICA, rutas: rutasPublicas, contenedor }).iniciar();
}
