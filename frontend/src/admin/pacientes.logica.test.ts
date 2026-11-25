import { describe, expect, it } from 'vitest';
import { armarRequest, formatearDni, validarPaciente } from './pacientes.logica';

const valido = { nombre: 'Carla', apellido: 'Muestra', dni: '99999997' };

describe('validarPaciente', () => {
  it('acepta datos validos sin errores', () => {
    expect(validarPaciente(valido)).toEqual({});
  });

  it.each(['', '   '])('rechaza nombre vacio o solo espacios (%j)', (nombre) => {
    const errores = validarPaciente({ ...valido, nombre });
    expect(errores.nombre).toBeDefined();
    expect(errores.apellido).toBeUndefined();
    expect(errores.dni).toBeUndefined();
  });

  it('rechaza apellido vacio o solo espacios', () => {
    expect(validarPaciente({ ...valido, apellido: '  ' }).apellido).toBeDefined();
    expect(validarPaciente({ ...valido, apellido: '' }).apellido).toBeDefined();
  });

  it('acepta 100 caracteres y rechaza 101 en nombre y apellido', () => {
    expect(validarPaciente({ ...valido, nombre: 'a'.repeat(100), apellido: 'b'.repeat(100) })).toEqual({});
    const errores = validarPaciente({ ...valido, nombre: 'a'.repeat(101), apellido: 'b'.repeat(101) });
    expect(errores.nombre).toBeDefined();
    expect(errores.apellido).toBeDefined();
  });

  it('no cuenta los espacios de borde para el largo', () => {
    expect(validarPaciente({ ...valido, nombre: ' ' + 'a'.repeat(100) + ' ' })).toEqual({});
  });

  it.each(['99-999-997', '9999a997', '999999', '999999999', '', '99.9999.97', '999.999.997'])('rechaza el DNI %j', (dni) => {
    const errores = validarPaciente({ ...valido, dni });
    expect(errores.dni).toBeDefined();
    expect(errores.nombre).toBeUndefined();
  });

  it.each(['9999999', '99999997', '99.999.997', '9.999.999', '  99999997  '])('acepta el DNI %j', (dni) => {
    expect(validarPaciente({ ...valido, dni })).toEqual({});
  });
});

describe('armarRequest', () => {
  it('recorta nombre, apellido y DNI', () => {
    expect(armarRequest({ nombre: '  Carla ', apellido: ' Muestra  ', dni: ' 99999997 ' })).toEqual(valido);
  });

  it('conserva el DNI como se escribio (con puntos)', () => {
    expect(armarRequest({ ...valido, dni: '99.999.997' }).dni).toBe('99.999.997');
  });
});

describe('formatearDni', () => {
  it('agrega puntos a 8 y 7 digitos', () => {
    expect(formatearDni('99999997')).toBe('99.999.997');
    expect(formatearDni('9999999')).toBe('9.999.999');
  });

  it('deja intacto lo que no es de 7 u 8 digitos', () => {
    expect(formatearDni('99.999.997')).toBe('99.999.997');
    expect(formatearDni('abc')).toBe('abc');
  });
});
