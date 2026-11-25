import { ErrorApi, MENSAJE_GENERICO, pedir } from '../api/cliente';
import type { LoginResponse } from '../api/types';
import { el, montar } from '../shared/dom';
import type { ContextoRuta } from '../shared/router';
import { guardarSesion } from '../shared/sesion';

const EMAIL_INVALIDO = 'Ingresá un email válido.';

function parsearLogin(dato: unknown): LoginResponse {
  if (typeof dato !== 'object' || dato === null) {
    throw new Error('respuesta inesperada');
  }
  const respuesta = dato as Record<string, unknown>;
  if (
    typeof respuesta.token !== 'string' ||
    respuesta.token === '' ||
    respuesta.tipo !== 'Bearer' ||
    typeof respuesta.expiraEn !== 'string' ||
    typeof respuesta.nombre !== 'string'
  ) {
    throw new Error('respuesta inesperada');
  }
  return {
    token: respuesta.token,
    tipo: 'Bearer',
    expiraEn: respuesta.expiraEn,
    nombre: respuesta.nombre,
  };
}

function validar(email: string, password: string): { email?: string; password?: string } {
  const errores: { email?: string; password?: string } = {};
  if (email.trim() === '') {
    errores.email = 'Ingresá tu email.';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errores.email = EMAIL_INVALIDO;
  }
  if (password === '') {
    errores.password = 'Ingresá tu contraseña.';
  }
  return errores;
}

export function render(contenedor: HTMLElement, _ctx: ContextoRuta): void {
  const errorGeneral = el('p', { class: 'login__error', role: 'alert', hidden: '' });
  const campoEmail = el('input', {
    id: 'login-email',
    type: 'email',
    autocomplete: 'username',
    'aria-describedby': 'login-email-error',
  });
  const errorEmail = el('p', { id: 'login-email-error', class: 'campo__error', hidden: '' });
  const campoPassword = el('input', {
    id: 'login-password',
    type: 'password',
    autocomplete: 'current-password',
    'aria-describedby': 'login-password-error',
  });
  const errorPassword = el('p', { id: 'login-password-error', class: 'campo__error', hidden: '' });
  const boton = el('button', { type: 'submit' }, 'Ingresar');
  const formulario = el(
    'form',
    { class: 'login__form', novalidate: '' },
    el('div', { class: 'campo' }, el('label', { for: 'login-email' }, 'Email'), campoEmail, errorEmail),
    el(
      'div',
      { class: 'campo' },
      el('label', { for: 'login-password' }, 'Contraseña'),
      campoPassword,
      errorPassword,
    ),
    boton,
  );

  const tarjeta = el(
    'div',
    { class: 'login__card' },
    el('h1', {}, 'Ingresar'),
    el('p', { class: 'login__intro' }, 'Ingresá con tu cuenta de odontólogo para gestionar la agenda.'),
    errorGeneral,
    formulario,
  );
  montar(contenedor, el('section', { class: 'login' }, tarjeta));

  const mostrarError = (destino: HTMLElement, mensaje: string | undefined): void => {
    destino.textContent = mensaje ?? '';
    destino.hidden = mensaje === undefined;
  };

  const irAAgenda = (): void => {
    window.history.pushState({}, '', '/admin/agenda');
    window.dispatchEvent(new PopStateEvent('popstate'));
  };

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const email = campoEmail.value;
    const password = campoPassword.value;
    const errores = validar(email, password);
    mostrarError(errorEmail, errores.email);
    mostrarError(errorPassword, errores.password);
    campoEmail.setAttribute('aria-invalid', errores.email === undefined ? 'false' : 'true');
    campoPassword.setAttribute('aria-invalid', errores.password === undefined ? 'false' : 'true');
    errorGeneral.hidden = true;
    errorGeneral.textContent = '';
    if (errores.email !== undefined || errores.password !== undefined) {
      return;
    }

    boton.disabled = true;
    void pedir('POST', '/api/auth/login', { body: { email, password }, parsear: parsearLogin })
      .then((sesion) => {
        guardarSesion(sesion);
        irAAgenda();
      })
      .catch((falla: unknown) => {
        if (falla instanceof ErrorApi && falla.codigo === 'VALIDACION') {
          for (const detalle of falla.detalles ?? []) {
            if (detalle.campo === 'email') {
              mostrarError(errorEmail, detalle.mensaje);
              campoEmail.setAttribute('aria-invalid', 'true');
            }
            if (detalle.campo === 'password') {
              mostrarError(errorPassword, detalle.mensaje);
              campoPassword.setAttribute('aria-invalid', 'true');
            }
          }
        }
        errorGeneral.textContent = falla instanceof ErrorApi ? falla.message : MENSAJE_GENERICO;
        errorGeneral.hidden = false;
      })
      .finally(() => {
        boton.disabled = false;
      });
  });
}
