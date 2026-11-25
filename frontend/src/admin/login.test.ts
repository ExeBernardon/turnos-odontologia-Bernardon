import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { registrarManejadorExpiracion } from '../api/cliente';
import { borrarSesion, obtenerSesion } from '../shared/sesion';
import { render } from './login';

type Contenedor = HTMLElement;

function respuestaJson(status: number, cuerpo: unknown): Response {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });
}

const loginOk = {
  token: 'token-ficticio',
  tipo: 'Bearer',
  expiraEn: '2099-12-01T20:00:00-03:00',
  nombre: 'Odontólogo Ejemplo',
};

function errorApi(status: number, codigo: string, mensaje: string, detalles?: unknown[]): unknown {
  return {
    timestamp: '2026-11-30T10:15:00-03:00',
    status,
    error: status === 401 ? 'Unauthorized' : 'Bad Request',
    codigo,
    mensaje,
    path: '/api/auth/login',
    ...(detalles === undefined ? {} : { detalles }),
  };
}

describe('pantalla de login del odontologo', () => {
  const fetchMock = vi.fn<typeof fetch>();
  const expiracion = vi.fn();

  function montar(): Contenedor {
    document.body.innerHTML = '<div id="app"></div>';
    const contenedor = document.getElementById('app') as HTMLElement;
    render(contenedor, { ruta: '/login', query: new URLSearchParams() });
    return contenedor;
  }

  function completar(contenedor: Contenedor, email: string, password: string): void {
    const campoEmail = contenedor.querySelector<HTMLInputElement>('#login-email');
    const campoPassword = contenedor.querySelector<HTMLInputElement>('#login-password');
    expect(campoEmail).not.toBeNull();
    expect(campoPassword).not.toBeNull();
    if (campoEmail !== null && campoPassword !== null) {
      campoEmail.value = email;
      campoPassword.value = password;
    }
  }

  function enviar(contenedor: Contenedor): void {
    const formulario = contenedor.querySelector('form');
    expect(formulario).not.toBeNull();
    formulario?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  }

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    expiracion.mockReset();
    registrarManejadorExpiracion(expiracion);
    borrarSesion();
    window.history.replaceState({}, '', '/admin/login');
  });

  afterEach(() => {
    registrarManejadorExpiracion(null);
    vi.unstubAllGlobals();
    borrarSesion();
  });

  it('muestra campos etiquetados, autocomplete y estado accesible del error', () => {
    const contenedor = montar();
    expect(contenedor.querySelector('h1')?.textContent).toBe('Ingresar');
    expect(contenedor.querySelector('label[for="login-email"]')?.textContent).toBe('Email');
    expect(contenedor.querySelector('label[for="login-password"]')?.textContent).toBe('Contraseña');
    expect(contenedor.querySelector<HTMLInputElement>('#login-email')?.autocomplete).toBe('username');
    expect(contenedor.querySelector<HTMLInputElement>('#login-password')?.autocomplete).toBe('current-password');
    expect(contenedor.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('enviar con datos validos pide POST /api/auth/login con el LoginRequest', async () => {
    fetchMock.mockResolvedValue(respuestaJson(200, loginOk));
    const contenedor = montar();
    completar(contenedor, 'odontologo@ejemplo.com', 'clave-ficticia');
    enviar(contenedor);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));

    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/api/auth/login');
    expect(init?.method).toBe('POST');
    expect(init?.body).toBe(JSON.stringify({ email: 'odontologo@ejemplo.com', password: 'clave-ficticia' }));
  });

  it('200 guarda la sesion y navega a /admin/agenda', async () => {
    fetchMock.mockResolvedValue(respuestaJson(200, loginOk));
    const contenedor = montar();
    completar(contenedor, 'odontologo@ejemplo.com', 'clave-ficticia');
    enviar(contenedor);
    await vi.waitFor(() => expect(window.location.pathname).toBe('/admin/agenda'));

    expect(obtenerSesion()).toEqual(loginOk);
  });

  it('401 CREDENCIALES_INVALIDAS muestra el mensaje generico y no redirige', async () => {
    fetchMock.mockResolvedValue(
      respuestaJson(401, errorApi(401, 'CREDENCIALES_INVALIDAS', 'Email o contraseña incorrectos.')),
    );
    const contenedor = montar();
    completar(contenedor, 'odontologo@ejemplo.com', 'clave-incorrecta');
    enviar(contenedor);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    await vi.waitFor(() => expect(contenedor.querySelector('[role="alert"]')?.textContent).toContain('incorrectos'));

    expect(window.location.pathname).toBe('/admin/login');
    expect(expiracion).not.toHaveBeenCalled();
    expect(obtenerSesion()).toBeNull();
  });

  it('400 VALIDACION muestra el detalle junto a cada campo', async () => {
    fetchMock.mockResolvedValue(
      respuestaJson(
        400,
        errorApi(400, 'VALIDACION', 'Hay campos con errores.', [
          { campo: 'email', mensaje: 'Formato inválido' },
          { campo: 'password', mensaje: 'La contraseña es obligatoria' },
        ]),
      ),
    );
    const contenedor = montar();
    completar(contenedor, 'odontologo@ejemplo.com', 'clave-ficticia');
    enviar(contenedor);
    await vi.waitFor(() => expect(contenedor.querySelector('#login-email-error')?.textContent).toBe('Formato inválido'));
    expect(fetchMock).toHaveBeenCalledTimes(1);

    expect(contenedor.querySelector('#login-email-error')?.textContent).toBe('Formato inválido');
    expect(contenedor.querySelector('#login-password-error')?.textContent).toBe('La contraseña es obligatoria');
    expect(window.location.pathname).toBe('/admin/login');
  });

  it('valida localmente campos vacios y email invalido sin pedir a la API', async () => {
    const contenedor = montar();
    enviar(contenedor);
    expect(contenedor.querySelector('#login-email-error')?.textContent).toBe('Ingresá tu email.');
    expect(contenedor.querySelector('#login-password-error')?.textContent).toBe('Ingresá tu contraseña.');
    expect(fetchMock).not.toHaveBeenCalled();

    completar(contenedor, 'no-es-email', 'clave-ficticia');
    enviar(contenedor);
    expect(contenedor.querySelector('#login-email-error')?.textContent).toBe('Ingresá un email válido.');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
