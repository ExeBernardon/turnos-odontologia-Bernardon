import fs from 'fs'; import path from 'path'; import { fileURLToPath } from 'url';
import YAML from 'yaml'; import Ajv2020 from 'ajv/dist/2020.js'; import addFormats from 'ajv-formats';
const root=process.argv[2] || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'); const doc=YAML.parse(fs.readFileSync(root+'/openapi.yaml','utf8'));
const ajv=new Ajv2020({strict:false,allErrors:true}); addFormats.default?addFormats.default(ajv):addFormats(ajv);
// rewrite refs to point into one root schema
const rootSchema={$id:'http://x.test/api',components:{schemas:doc.components.schemas}};
ajv.addSchema(rootSchema);
function resolve(s){ return JSON.parse(JSON.stringify(s).replace(/"#\/components\/schemas\//g,'"http://x.test/api#/components/schemas/')); }
let n=0,bad=0; const seen=new Set();
function check(schema,ex,where){
  for(const [k,v] of Object.entries(ex||{})){
    const f=v.externalValue; if(!f) continue; seen.add(path.basename(f));
    const data=JSON.parse(fs.readFileSync(path.join(root,f),'utf8'));
    const val=ajv.compile(resolve(schema)); n++;
    if(!val(data)){bad++;console.log('INVALID',where,f,JSON.stringify(val.errors.slice(0,3)));}
  }
}
const resolveRef=(r)=>{ const m=r.$ref.split('/').pop(); return doc.components.responses[m]; };
for(const [p,ms] of Object.entries(doc.paths)) for(const [m,o] of Object.entries(ms)){
  const w=m.toUpperCase()+' '+p;
  const rb=o.requestBody?.content?.['application/json']; if(rb) check(rb.schema,rb.examples,w+' req');
  for(const [st,r0] of Object.entries(o.responses)){ const r=r0.$ref?resolveRef(r0):r0; const c=r.content?.['application/json']; if(c) check(c.schema,c.examples,w+' '+st); }
}
for(const [k,r] of Object.entries(doc.components.responses)){const c=r.content['application/json']; check(c.schema,c.examples,'resp '+k);}
const files=fs.readdirSync(root+'/ejemplos'); const unref=files.filter(f=>!seen.has(f));
console.log(`checked ${n} example refs, ${bad} invalid; ${files.length} files, unreferenced: ${JSON.stringify(unref)}`);
process.exit(bad?1:0);
