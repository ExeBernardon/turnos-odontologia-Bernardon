/** Token de sesion del odontologo (JWT). C-13 completa el flujo de login y el guard. */
const CLAVE = 'turnos.token';

export function obtenerToken(): string | null {
  try {
    return window.sessionStorage.getItem(CLAVE);
  } catch {
    return null;
  }
}

export function guardarToken(token: string): void {
  try {
    window.sessionStorage.setItem(CLAVE, token);
  } catch {
    // sin storage disponible: la sesion no persiste
  }
}

export function borrarToken(): void {
  try {
    window.sessionStorage.removeItem(CLAVE);
  } catch {
    // sin storage disponible
  }
}
