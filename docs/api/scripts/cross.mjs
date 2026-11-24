import fs from 'fs'; import YAML from 'yaml';
import { fileURLToPath } from 'url'; import path from 'path';
const R=process.argv[2] || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const doc=YAML.parse(fs.readFileSync(R+'/openapi.yaml','utf8'));
const md=fs.readFileSync(R+'/errores.md','utf8'); const ts=fs.readFileSync(R+'/tipos.ts','utf8');
let ok=true; const fail=(m)=>{ok=false;console.log('FAIL',m)};
const ops=[]; for(const [p,ms] of Object.entries(doc.paths)) for(const [m,o] of Object.entries(ms)) ops.push([m.toUpperCase(),p,o]);
console.log('endpoints:',ops.length); if(ops.length!==32) fail('count');
// statuses allowed
const allowed=new Set(['200','201','204','400','401','404','409','422']);
const respStatus=(o,)=>Object.keys(o.responses);
for(const [m,p,o] of ops){ for(const s of respStatus(o)) if(!allowed.has(s)) fail(`${m} ${p} status ${s}`);}
// matrix vs openapi
const rows=md.split('\n').filter(l=>/^\| `(GET|POST|PUT|PATCH|DELETE) /.test(l));
console.log('matrix rows:',rows.length); if(rows.length!==32) fail('matrix rows');
for(const l of rows){ const c=l.split('|').map(s=>s.trim()); const [m,p]=c[1].replace(/`/g,'').split(' ');
  const op=ops.find(x=>x[0]===m&&x[1]===p); if(!op){fail('matrix op not in openapi '+c[1]);continue;}
  const mat=new Set([c[2],...[...c[3].matchAll(/(?:^|; )(\d{3}) /g)].map(x=>x[1])]);
  const api=new Set(respStatus(op[2])); if([...mat].sort().join()!==[...api].sort().join()) fail(`${c[1]} matrix ${[...mat]} vs api ${[...api]}`);
  // codes in matrix per status exist in codes enum
}
// enum vs catalog vs ts
const en=doc.components.schemas.CodigoError.enum;
const cat=[...md.matchAll(/^\| (\d{3}) \| `([A-Z_]+)` \|/gm)].map(x=>x[2]);
const tsu=[...ts.slice(ts.indexOf('export type CodigoError'),ts.indexOf('// ---',ts.indexOf('export type CodigoError'))).matchAll(/'([A-Z_]+)'/g)].map(x=>x[1]);
const eq=(a,b)=>[...a].sort().join()===[...b].sort().join();
console.log('enum',en.length,'catalog',cat.length,'ts',tsu.length); if(!eq(en,cat)||!eq(en,tsu)) fail('codes differ');
// each code has its status unique and matches catalog status
const catSt=Object.fromEntries([...md.matchAll(/^\| (\d{3}) \| `([A-Z_]+)` \|/gm)].map(x=>[x[2],x[1]]));
// every specific code in matrix is present in an example file with matching status
const exFiles=fs.readdirSync(R+'/ejemplos');
for(const c of en){ const f=exFiles.filter(n=>{try{return JSON.parse(fs.readFileSync(R+'/ejemplos/'+n,'utf8')).codigo===c}catch{return false}}); if(!f.length) fail('no example for '+c);
  for(const n of f){const j=JSON.parse(fs.readFileSync(R+'/ejemplos/'+n,'utf8')); if(String(j.status)!==catSt[c]) fail(n+' status');}}
// matrix specific codes: each op+code has file
for(const [m,p,o] of ops){ for(const [st,r] of Object.entries(o.responses)){ if(r.$ref||!r.content) continue; for(const [k,e] of Object.entries(r.content['application/json'].examples)) if(!fs.existsSync(R+'/'+e.externalValue)) fail('missing '+e.externalValue);}}
// token / forbidden props in schemas
const sch=doc.components.schemas;
const props=(n)=>Object.keys(sch[n].properties||{});
if(['Turno','TurnoResumenPublico','BloqueoCreado'].some(n=>props(n).includes('token'))) fail('token in schema');
const priv=['dni','apellido','email','emailContacto','telefono','telefonoContacto','id','token','paciente'];
const leak=props('TurnoResumenPublico').filter(p=>priv.includes(p)); if(leak.length) fail('leak '+leak);
console.log('TurnoResumenPublico props:',props('TurnoResumenPublico').join(','));
// no other status codes in md matrix
if(/\b(403|405|500)\b/.test(rows.join('\n'))) fail('forbidden status in matrix');
// public ops security []
for(const [m,p,o] of ops){ const pub=p.startsWith('/api/public/')||p==='/api/auth/login'; if(pub&&JSON.stringify(o.security)!=='[]') fail('public sec '+p); if(!pub&&o.security) fail('private has override '+p); if(!pub&&!o.responses['401']) fail('no 401 '+p);}
// no opcionales
if(ops.some(([m,p])=>/espera|\.ics|csv/i.test(p))) fail('opcionales');
console.log(ok?'ALL CROSS CHECKS PASS':'CROSS CHECKS FAILED'); process.exit(ok?0:1);
