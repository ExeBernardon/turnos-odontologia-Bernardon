/** Helpers minimos de DOM. Todo texto va con textContent (nunca innerHTML con datos). */

export function el<K extends keyof HTMLElementTagNameMap>(
  etiqueta: K,
  atributos: Readonly<Record<string, string>> = {},
  ...hijos: ReadonlyArray<Node | string>
): HTMLElementTagNameMap[K] {
  const nodo = document.createElement(etiqueta);
  for (const [nombre, valor] of Object.entries(atributos)) {
    nodo.setAttribute(nombre, valor);
  }
  for (const hijo of hijos) {
    nodo.append(hijo);
  }
  return nodo;
}

/** Reemplaza el contenido del contenedor. */
export function montar(contenedor: HTMLElement, ...hijos: ReadonlyArray<Node | string>): void {
  contenedor.replaceChildren(...hijos);
}

/** Pantalla placeholder accesible: un encabezado con el nombre de la pantalla. */
export function placeholder(contenedor: HTMLElement, titulo: string): void {
  montar(contenedor, el('h1', {}, titulo), el('p', {}, 'Pantalla en construcción.'));
}
