import { beforeEach, describe, expect, it } from 'vitest';
import {
  borrarSesion,
  borrarToken,
  guardarSesion,
  guardarToken,
  obtenerSesion,
  obtenerToken,
  sesionValida,
} from './sesion';

const sesion = {
  token: 'token-ficticio',
  tipo: 'Bearer' as const,
  expiraEn: '2099-12-01T20:00:00-03:00',
  nombre: 'Odontólogo Ejemplo',
};

describe('sesion del odontologo', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('guarda y lee la sesion completa en localStorage', () => {
    guardarSesion(sesion);
    expect(obtenerSesion()).toEqual(sesion);
    expect(window.localStorage.getItem('turnos.sesion')).not.toBeNull();
  });

  it('borra la sesion completa', () => {
    guardarSesion(sesion);
    borrarSesion();
    expect(obtenerSesion()).toBeNull();
    expect(obtenerToken()).toBeNull();
  });

  it('considera valida una sesion con vencimiento futuro', () => {
    guardarSesion(sesion);
    expect(sesionValida()).toBe(true);
  });

  it('considera invalida una sesion vencida aunque el token siga guardado', () => {
    guardarSesion({ ...sesion, expiraEn: '2020-01-01T00:00:00-03:00' });
    expect(obtenerToken()).toBe('token-ficticio');
    expect(sesionValida()).toBe(false);
  });

  it('acepta un token legacy sin expiracion', () => {
    window.localStorage.setItem('turnos.token', 'token-legacy');
    expect(obtenerToken()).toBe('token-legacy');
    expect(obtenerSesion()).toEqual({ token: 'token-legacy', tipo: 'Bearer' });
    expect(sesionValida()).toBe(true);
  });

  it('mantiene el shim guardarToken y lo limpia con borrarToken', () => {
    guardarToken('token-shim');
    expect(obtenerToken()).toBe('token-shim');
    borrarToken();
    expect(obtenerToken()).toBeNull();
  });

  it('ignora datos corruptos en el almacenamiento', () => {
    window.localStorage.setItem('turnos.sesion', '{no es json');
    expect(obtenerSesion()).toBeNull();
    expect(sesionValida()).toBe(false);
  });
});
