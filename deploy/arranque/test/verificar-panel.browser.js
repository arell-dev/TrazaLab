async (page) => {
  await page.unrouteAll({ behavior: 'wait' });
  const assert = (condicion, mensaje) => {

    if (!condicion) {
      throw new Error(mensaje);
    }

  };

  let sesionVencida = false;
  let estadoServidor = 'estado-desconocido';

  await page.route('https://panel.test/**', async (ruta) => {
    const url = new URL(ruta.request().url());

    if (url.pathname === '/config.json') {
      await ruta.fulfill({ json: {
        userPoolId: 'us-east-2_Prueba',
        clientId: 'prueba',
        apiUrl: 'https://prueba.execute-api.us-east-2.amazonaws.com/',
        aplicacionUrl: 'https://prueba.cloudfront.net/usuarios/login',
        cognitoUrl: 'https://prueba.auth.us-east-2.amazoncognito.com/'
      } });

      return;
    }

    if (url.pathname.includes('/oidc-client-ts.js')) {
      const codigo = `
        export class InMemoryWebStorage {}
        export class WebStorageStateStore { constructor() {} }
        export class UserManager {
          constructor() {}
          async signinRedirectCallback() { return { expired: ${sesionVencida}, access_token: 'token-sintetico' }; }
          async removeUser() {}
          async signinRedirect() {}
        }
      `;

      await ruta.fulfill({ status: 200, contentType: 'application/javascript', body: codigo });
      return;

    }
    const respuesta = await ruta.fetch({ url: `http://127.0.0.1:5178${url.pathname}${url.search}` });
    await ruta.fulfill({ response: respuesta });
  });

  await page.route('https://prueba.execute-api.us-east-2.amazonaws.com/**', async (ruta) => {
    await ruta.fulfill({ json: { estado: estadoServidor } });
  });

  sesionVencida = true;
  await page.goto('https://panel.test/?code=simulado');
  await page.getByText('La sesión venció. Inicia sesión nuevamente.').waitFor();
  assert(await page.locator('#ingresar').isVisible(), 'Debe permitir iniciar sesión nuevamente.');
  assert(await page.locator('#salir').isVisible(), 'Debe ofrecer cerrar la sesión alojada.');
  assert(await page.locator('#verificar').isHidden(), 'No debe ofrecer consultas con sesión vencida.');

  sesionVencida = false;
  for (estadoServidor of ['estado-desconocido', '__proto__']) {
    await page.goto('https://panel.test/?code=simulado');
    await page.getByText('No se pudo confirmar el estado del servidor. Inténtalo de nuevo.').waitFor();
    assert(await page.locator('#indicador').textContent() === 'Atención', 'El estado desconocido debe indicar atención.');
    assert(await page.locator('#abrir').isHidden(), 'No debe presentar la aplicación como disponible.');
    assert(await page.locator('#encender').isHidden(), 'No debe permitir encender desde un estado desconocido.');
  }
  return 'Correcto: sesión vencida y estado desconocido con Cognito y API simulados.';
}
