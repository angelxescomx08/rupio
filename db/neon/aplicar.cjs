// Aplica migraciones de db/migrations en Neon PRODUCCIÓN (proyecto Rupio) con tu sesión de la consola de Neon.
//
//   powershell -File db/neon/aplicar.ps1 db/migrations/010_cardio.sql --verificar="select count(*) from exercises"
//
// 1. Abre Chrome con un perfil propio (.agents/neon/perfil). La primera vez inicias sesión en Neon; después ya no.
// 2. Con esa sesión pide a la API de la consola el proyecto cuyo endpoint es el de la app (ENDPOINT) y comprueba que sea
//    la rama principal. Si no coincide, se detiene sin tocar nada.
// 3. Pide la cadena de conexión del rol dueño, cierra Chrome y corre cada archivo con `pg`. La cadena nunca se imprime.
// 4. Recarga la caché de la Data API (NOTIFY pgrst) y corre las consultas de --verificar.
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const { Client } = require('pg');

const ENDPOINT = 'ep-wild-art-bdlip6s4';               // el de NEON_AUTH_URL / NEON_DATA_API_URL en app/build.gradle.kts
const DATABASE = 'neondb';
const ROOT = path.resolve(__dirname, '..', '..');
const PROFILE = path.join(ROOT, '.agents', 'neon', 'perfil');
const API = 'https://console.neon.tech/api/v2';

function args() {
  const files = [], verify = [];
  let dryRun = false;
  for (const a of process.argv.slice(2)) {
    if (a.startsWith('--verificar=')) verify.push(a.slice('--verificar='.length));
    else if (a === '--solo-revisar') dryRun = true;
    else files.push(path.resolve(ROOT, a));
  }
  for (const f of files) if (!fs.existsSync(f)) throw new Error(`No existe ${f}`);
  if (!files.length && !verify.length && !dryRun) throw new Error('Uso: aplicar.ps1 <migración.sql>... [--verificar="select ..."] [--solo-revisar]');
  return { files, verify, dryRun };
}

async function connectionUri() {
  const ctx = await chromium.launchPersistentContext(PROFILE, {
    channel: 'chrome', headless: false, viewport: null,
    ignoreDefaultArgs: ['--enable-automation'], args: ['--disable-blink-features=AutomationControlled'],
  });
  try {
    const page = ctx.pages()[0] || await ctx.newPage();
    // Si la consola manda su propio token en las llamadas a la API, se reutiliza.
    let auth = null;
    page.on('request', (r) => { const h = r.headers(); if (r.url().startsWith(API) && h.authorization) auth = h.authorization; });
    await page.goto('https://console.neon.tech/app/projects');
    const api = async (p) => {
      const r = await page.request.get(API + p, { headers: auth ? { authorization: auth } : {} });
      if (!r.ok()) { const e = new Error(`${r.status()} en ${p}`); e.status = r.status(); throw e; }
      return r.json();
    };
    let projects = null;
    for (let i = 0; i < 300 && !projects; i++) {
      try { projects = (await api('/projects?limit=400')).projects; }
      catch (e) {
        if (e.status !== 401 && e.status !== 403) throw e;
        if (i === 0) console.log('Inicia sesión en Neon en la ventana de Chrome (solo hace falta la primera vez). Espero hasta 10 min…');
        await page.waitForTimeout(2000);
      }
    }
    if (!projects) throw new Error('No se pudo entrar a la consola de Neon.');
    for (const p of projects) {
      const ep = (await api(`/projects/${p.id}/endpoints`)).endpoints.find((e) => e.id === ENDPOINT || (e.host || '').startsWith(ENDPOINT));
      if (!ep) continue;
      const branch = (await api(`/projects/${p.id}/branches/${ep.branch_id}`)).branch;
      if (!(branch.default || branch.primary)) throw new Error(`El endpoint ${ENDPOINT} está en la rama "${branch.name}", que no es la principal. No se aplica nada.`);
      const roles = (await api(`/projects/${p.id}/branches/${branch.id}/roles`)).roles.map((r) => r.name);
      const role = roles.find((r) => r === `${DATABASE}_owner`) || roles.find((r) => r.endsWith('_owner'));
      if (!role) throw new Error(`No encontré el rol dueño en la rama ${branch.name} (roles: ${roles.join(', ')}).`);
      const q = new URLSearchParams({ branch_id: branch.id, endpoint_id: ep.id, database_name: DATABASE, role_name: role, pooled: 'false' });
      const { uri } = await api(`/projects/${p.id}/connection_uri?${q}`);
      console.log(`Destino: proyecto "${p.name}" · rama "${branch.name}" (principal) · ${ep.host} · base ${DATABASE} · rol ${role}`);
      return uri;
    }
    throw new Error(`Ningún proyecto de esta cuenta tiene el endpoint ${ENDPOINT}.`);
  } finally {
    await ctx.close();
  }
}

(async () => {
  const { files, verify, dryRun } = args();
  const uri = await connectionUri();
  if (dryRun) { console.log('Solo revisión: no se corrió nada.'); return; }
  // verify-full: cifrado y certificado del servidor verificados (y sin el aviso de pg sobre sslmode=require).
  const client = new Client({ connectionString: uri.replace(/sslmode=[a-z-]+/, 'sslmode=verify-full') });
  await client.connect();
  try {
    for (const f of files) {
      await client.query(fs.readFileSync(f, 'utf8'));
      console.log(`Aplicada: ${path.relative(ROOT, f)}`);
    }
    if (files.length) { await client.query("notify pgrst, 'reload schema'"); console.log('Caché de la Data API recargada.'); }
    for (const v of verify) {
      const r = await client.query(v);
      console.log(`\n> ${v}`);
      console.table(r.rows);
    }
  } finally {
    await client.end();
  }
})().catch((e) => { console.error('ERROR: ' + e.message); process.exit(1); });
