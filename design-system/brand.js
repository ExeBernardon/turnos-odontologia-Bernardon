(function () {
  'use strict';

  const BRAND_NAME = 'Turnos odontológicos';

  function bindBrandName(root) {
    const targets = (root || document).querySelectorAll('[data-brand-name]');
    for (const node of targets) {
      node.textContent = BRAND_NAME;
    }
  }

  if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function () { bindBrandName(document); });
    } else {
      bindBrandName(document);
    }
  }

  window.DesignSystemBrand = { BRAND_NAME: BRAND_NAME, bindBrandName: bindBrandName };
})();
