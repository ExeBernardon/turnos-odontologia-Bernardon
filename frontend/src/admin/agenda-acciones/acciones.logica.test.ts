import { describe, expect, it } from 'vitest';
import { ErrorApi, ErrorConexion, MENSAJE_GENERICO } from '../../api/cliente';
import type { ApiError, Paciente, Turno } from '../../api/types';
import { ejemplo } from '../configuracion/ejemplos.testutil';
import {
  accionDeErrorTurno,
  accionesPermitidas,
  armarTurnoManual,
  textoResultado,
  validarContacto,
  type ClaveError,
} from './acciones.logica';

const turnos = ejemplo('turnos-listar.200') as Turno[];
const pendiente = turnos[0] as Turno; // 2026-12-01 16:00-03:00, PENDIENTE
const confirmado = turnos[1] as Turno; // 2026-12-02 09:00-03:00
const cancelado = turnos[2] as Turno;

const ANTES = new Date('2026-12-01T12:00:00-03:00');
const DESPUES = new Date('2026-12-01T16:30:00-03:00');

function conEstado(turno: Turno, estado: Turno['estado']): Turno {
  return { ...turno, estado };
}

function errorDe(nombre: string): ErrorApi {
  const cuerpo = ejemplo(nombre) as ApiError;
  return new ErrorApi(cuerpo.status, cuerpo.mensaje, cuerpo.codigo, cuerpo.detalles, cuerpo);
}

describe('accionesPermitidas', () => {
  it('PENDIENTE futuro: confirmar, reprogramar y cancelar; asistencia deshabilitada desde las 16:00', () => {
    expect(accionesPermitidas(pendiente, ANTES)).toEqual({
      confirmar: true,
      reprogramar: true,
      cancelar: true,
      asistencia: { visible: true, habilitada: false, desde: '16:00' },
    });
  });

  it('PENDIENTE ya empezado: sin confirmar y con asistencia habilitada', () => {
    const acciones = accionesPermitidas(pendiente, DESPUES);
    expect(acciones.confirmar).toBe(false);
    expect(acciones.cancelar).toBe(true);
    expect(acciones.asistencia).toEqual({ visible: true, habilitada: true, desde: '16:00' });
  });

  it('CONFIRMADO: sin confirmar pero con el resto', () => {
    const acciones = accionesPermitidas(confirmado, ANTES);
    expect(acciones.confirmar).toBe(false);
    expect(acciones.reprogramar).toBe(true);
    expect(acciones.cancelar).toBe(true);
    expect(acciones.asistencia.visible).toBe(true);
    expect(acciones.asistencia.desde).toBe('09:00');
  });

  it.each(['CANCELADO', 'ASISTIO', 'AUSENTE'] as const)('%s no tiene ninguna acción', (estado) => {
    const acciones = accionesPermitidas(conEstado(cancelado, estado), ANTES);
    expect(acciones.confirmar).toBe(false);
    expect(acciones.reprogramar).toBe(false);
    expect(acciones.cancelar).toBe(false);
    expect(acciones.asistencia.visible).toBe(false);
  });

  it('muestra la misma hora desde con el navegador en otra zona', () => {
    const anterior = process.env['TZ'];
    process.env['TZ'] = 'America/New_York';
    try {
      expect(accionesPermitidas(pendiente, ANTES).asistencia.desde).toBe('16:00');
    } finally {
      if (anterior === undefined) {
        delete process.env['TZ'];
      } else {
        process.env['TZ'] = anterior;
      }
    }
  });
});

describe('armarTurnoManual', () => {
  const ana: Paciente = { id: 1, nombre: 'Ana', apellido: 'Ejemplo', dni: '99999999', ultimoContacto: null };

  it('con paciente existente envía solo pacienteId y recorta el contacto', () => {
    const body = armarTurnoManual({
      paciente: { tipo: 'existente', paciente: ana },
      prestacionId: 1,
      inicio: '2026-12-04T15:00:00-03:00',
      emailContacto: '  paciente@ejemplo.com ',
      telefonoContacto: ' +54 9 11 0000-0000 ',
    });
    expect(body).toEqual(ejemplo('turnos-crear.request-paciente-existente'));
    expect('paciente' in body).toBe(false);
  });

  it('con paciente nuevo envía solo paciente, con la forma del ejemplo del contrato', () => {
    const body = armarTurnoManual({
      paciente: { tipo: 'nuevo', datos: { nombre: ' Carla ', apellido: 'Muestra ', dni: '99.999.997' } },
      prestacionId: 1,
      inicio: '2026-12-04T16:00:00-03:00',
      emailContacto: ' carla@ejemplo.com',
      telefonoContacto: '+54 9 11 0000-0002 ',
    });
    expect(body).toEqual(ejemplo('turnos-crear.request-paciente-nuevo'));
    expect('pacienteId' in body).toBe(false);
  });
});

describe('validarContacto', () => {
  const telefonoOk = '+54 9 11 0000-0002';

  it.each([
    ['vacío', ''],
    ['sin arroba', 'carla.ejemplo.com'],
    ['de 151 caracteres', 'a'.repeat(139) + '@ejemplo.com'],
  ])('rechaza un email %s', (_nombre, email) => {
    expect(validarContacto(email, telefonoOk).email).toEqual(expect.any(String));
  });

  it.each([
    ['vacío', ''],
    ['con letras', 'abc123456'],
    ['de 5 caracteres', '12345'],
  ])('rechaza un teléfono %s', (_nombre, telefono) => {
    expect(validarContacto('carla@ejemplo.com', telefono).telefono).toEqual(expect.any(String));
  });

  it('acepta un contacto válido', () => {
    expect(validarContacto('carla@ejemplo.com', '+54 9 11 0000-0002')).toEqual({});
  });
});

describe('accionDeErrorTurno', () => {
  it.each([
    ['turnos-crear.409-horario-ocupado', 'alta'],
    ['turnos-crear.422-horario-no-disponible', 'alta'],
    ['turnos-reprogramar.409-horario-ocupado', 'reprogramar'],
    ['turnos-reprogramar.422-horario-no-disponible', 'reprogramar'],
  ] as const)('%s pide refrescar los horarios', (nombre, accion) => {
    const r = accionDeErrorTurno(errorDe(nombre), accion);
    expect(r.efecto).toBe('refrescarHorarios');
    expect(r.mensaje).toContain('horario');
  });

  it('prestación inactiva recarga las prestaciones', () => {
    expect(accionDeErrorTurno(errorDe('turnos-crear.422-prestacion-inactiva'), 'alta').efecto).toBe('recargarPrestaciones');
  });

  it('DNI duplicado marca el campo DNI', () => {
    const r = accionDeErrorTurno(errorDe('turnos-crear.422-dni-duplicado'), 'alta');
    expect(r.efecto).toBe('campoDni');
    expect(r.mensaje).toContain('DNI');
  });

  it('400 de validación muestra los campos', () => {
    expect(accionDeErrorTurno(errorDe('error.400-validacion'), 'alta').efecto).toBe('campos');
  });

  it('transición inválida recarga la agenda con un mensaje distinto por acción', () => {
    const claves: ClaveError[] = ['confirmar', 'cancelar', 'estado', 'reprogramar'];
    const mensajes = claves.map((clave) => {
      const r = accionDeErrorTurno(errorDe('turnos-' + clave + '.422-transicion-invalida'), clave);
      expect(r.efecto).toBe('recargarAgenda');
      return r.mensaje;
    });
    expect(new Set(mensajes).size).toBe(4);
    expect(mensajes[2]).toBe('Solo se puede marcar la asistencia desde la hora de inicio del turno.');
  });

  it('404 recarga la agenda', () => {
    expect(accionDeErrorTurno(errorDe('error.404-recurso-no-encontrado'), 'cancelar').efecto).toBe('recargarAgenda');
  });

  it('conexión y 500 dan un mensaje genérico sin efecto', () => {
    expect(accionDeErrorTurno(new ErrorConexion(), 'confirmar')).toEqual({
      mensaje: new ErrorConexion().message,
      efecto: 'ninguno',
    });
    expect(accionDeErrorTurno(errorDe('error.500-error-interno'), 'alta')).toEqual({
      mensaje: MENSAJE_GENERICO,
      efecto: 'ninguno',
    });
  });

  it('401 no tiene mensaje propio', () => {
    expect(accionDeErrorTurno(errorDe('error.401-no-autenticado'), 'confirmar')).toEqual({ mensaje: '', efecto: 'ninguno' });
  });

  it('nunca muestra el mensaje del servidor', () => {
    const nombres = [
      'turnos-crear.409-horario-ocupado',
      'turnos-crear.422-dni-duplicado',
      'turnos-estado.422-transicion-invalida',
      'error.404-recurso-no-encontrado',
    ];
    for (const nombre of nombres) {
      const error = errorDe(nombre);
      expect(accionDeErrorTurno(error, 'cancelar').mensaje).not.toBe(error.message);
    }
  });
});

describe('textoResultado', () => {
  const reprogramado = ejemplo('turnos-reprogramar.200') as Turno; // 2026-12-01 18:00 CONFIRMADO

  it('alta según el estado de la respuesta', () => {
    expect(textoResultado(conEstado(pendiente, 'PENDIENTE'), 'alta')).toBe('Turno cargado: pendiente de confirmación');
    expect(textoResultado(conEstado(pendiente, 'CONFIRMADO'), 'alta')).toBe('Turno cargado: confirmado');
  });

  it('reprogramar nombra el nuevo horario en hora de Buenos Aires y el estado', () => {
    expect(textoResultado(conEstado(reprogramado, 'PENDIENTE'), 'reprogramar')).toBe(
      'Turno reprogramado al martes 1 de diciembre, 18:00: queda pendiente de confirmación',
    );
    expect(textoResultado(reprogramado, 'reprogramar')).toBe(
      'Turno reprogramado al martes 1 de diciembre, 18:00: queda confirmado',
    );
  });

  it('confirmar, cancelar y asistencia', () => {
    expect(textoResultado(confirmado, 'confirmar')).toBe('Turno confirmado');
    expect(textoResultado(confirmado, 'cancelar')).toBe('Turno cancelado');
    expect(textoResultado(confirmado, 'asistio')).toBe('Asistencia registrada');
    expect(textoResultado(confirmado, 'ausente')).toBe('Ausencia registrada');
  });
});
