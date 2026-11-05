// Isolated BFF integration: ALL upstream traffic goes to an in-memory stub.
// Never point this script at Spring Boot or a real database.
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
const actorId='321c9f1d-624d-4fe5-8c70-9b43396059b2';
const targetId='421c9f1d-624d-4fe5-8c70-9b43396059b2';
const fields={firstName:'Cuenta',lastName:'Aislada',dni:'00000000',phone:null,email:'isolated@example.com'};
const record={...fields,id:targetId,role:'VENTAS',enabled:true,branchId:null,createdAt:'2026-11-04T00:00:00Z',updatedAt:'2026-11-04T00:00:00Z',lastLoginAt:null};
const permissions=['USER_READ','USER_CREATE','USER_UPDATE','USER_ROLE_CHANGE','USER_DISABLE'];
let target={...record}; let calls=[]; let fault; let checks=0;
const upstream=createServer(async(req,res)=>{
  const url=new URL(req.url,'http://fixture');
  const send=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(data));};
  if(url.pathname==='/api/v1/auth/me') {
    const token=req.headers.authorization;
    if(token==='Bearer expired') return send(401,{});
    const limited=token==='Bearer limited', sales=token==='Bearer sales';
    return send(200,{user:{...record,id:actorId,role:sales?'VENTAS':'ADMIN'},permissions:limited?['USER_READ']:permissions});
  }
  calls.push({path:url.pathname,query:url.searchParams,method:req.method});
  if(fault) { const f=fault;fault=undefined;if(f.text){res.writeHead(f.status,{'Content-Type':'text/plain'});return res.end('untrusted private details');}return send(f.status,f.data); }
  if(url.pathname==='/api/v1/roles') return send(200,[{id:1,code:'ADMIN',name:'Administrador',description:'Administración'},{id:2,code:'VENTAS',name:'Ventas',description:'Ventas'}]);
  if(url.pathname==='/api/v1/roles/1/permissions') return send(200,[{code:'USER_READ',description:'Consultar usuarios'}]);
  if(url.pathname==='/api/v1/users' && req.method==='GET') return send(200,{content:[target],page:Number(url.searchParams.get('page')??0),size:10,totalElements:21,totalPages:3});
  let body='';for await(const chunk of req) body+=chunk;
  const data=body?JSON.parse(body):{};
  if(url.pathname==='/api/v1/users' && req.method==='POST') {
    assert.equal(data.phone,null);assert.equal(data.password.length,12);
    return send(201,{...target,...data,password:undefined,passwordHash:'must-not-leak',refreshToken:'must-not-leak'});
  }
  if(url.pathname===`/api/v1/users/${actorId}/status`) return send(409,{detail:'Debe existir al menos un administrador activo'});
  if(url.pathname===`/api/v1/users/${targetId}`) {
    if(req.method==='PATCH'){assert.equal('role' in data,false);assert.equal('password' in data,false);target={...target,...data};}
    return send(200,target);
  }
  if(url.pathname===`/api/v1/users/${targetId}/role`){target.role=data.role;return send(200,target);}
  if(url.pathname===`/api/v1/users/${targetId}/status`){target.enabled=data.enabled;return send(200,target);}
  return send(404,{});
});
upstream.listen(0,'127.0.0.1');await once(upstream,'listening');
const upstreamPort=upstream.address().port;
const probe=createServer(); probe.listen(0,'127.0.0.1'); await once(probe,'listening');
const appPort=probe.address().port; await new Promise(resolve=>probe.close(resolve));
const origin=`http://localhost:${appPort}`;
const server=spawn(process.execPath,['node_modules/next/dist/bin/next','start','-p',String(appPort)],{cwd:process.cwd(),env:{...process.env,AUTH_API_URL:`http://127.0.0.1:${upstreamPort}`,APP_ORIGIN:origin},stdio:['ignore','pipe','pipe']});
let logs='';server.stdout.on('data',d=>{logs=(logs+d).slice(-2000);});server.stderr.on('data',d=>{logs=(logs+d).slice(-2000);});
async function request(path,method='GET',body,token='admin',headers={}){
  const response=await fetch(`${origin}/api/management/${path}`,{method,headers:{...(token?{Cookie:`__Host-kiments-access=${token}`} : {}),...(body!==undefined?{'Content-Type':'application/json',Origin:origin}:{}),...headers},...(body!==undefined?{body:JSON.stringify(body)}:{})});
  return {status:response.status,data:await response.json(),response};
}
function check(name,fn){fn();checks++;console.log(`PASS ${name}`);}
try {
  let ready=false;
  for(let i=0;i<120;i++){try{await fetch(origin);ready=true;break;}catch{await new Promise(r=>setTimeout(r,250));}}
  if(!ready)throw new Error('Isolated Next server did not start');
  let r=await request('users','GET',undefined,'');check('anonymous BFF rejected',()=>assert.equal(r.status,401));
  r=await request('users','GET',undefined,'sales');check('non-admin rejected despite permission fixture',()=>assert.equal(r.status,403));
  r=await request('users','GET',undefined,'expired');check('expired session rejected',()=>assert.equal(r.status,401));
  const deniedPage=await fetch(`${origin}/usuarios`,{headers:{Cookie:'__Host-kiments-access=sales'}});check('private page responds under denied role',()=>assert.equal(deniedPage.status,200));
  const deniedHTML=await deniedPage.text();check('denied page has no users UI',()=>{assert.ok(deniedHTML.includes('No tienes acceso a Usuarios'));assert.ok(!deniedHTML.includes('Nuevo usuario'));});
  r=await request('users?name=Cuenta&role=VENTAS&enabled=true&page=2&size=10');check('server filters and pagination forwarded',()=>{assert.equal(r.status,200);assert.equal(r.data.page,2);assert.equal(calls.at(-1).query.get('name'),'Cuenta');assert.equal(calls.at(-1).query.get('role'),'VENTAS');assert.equal(calls.at(-1).query.get('enabled'),'true');});
  r=await request('users?email=isolated%40example.com');check('exact email forwarded',()=>assert.equal(calls.at(-1).query.get('email'),'isolated@example.com'));
  r=await request('roles');check('roles contract parsed',()=>{assert.equal(r.status,200);assert.equal(r.data[0].code,'ADMIN');});
  r=await request('roles/1/permissions');check('permissions contract parsed',()=>assert.equal(r.data[0].code,'USER_READ'));
  r=await request(`users/${targetId}`);check('real-detail-shaped DTO parsed',()=>assert.equal(r.data.id,targetId));
  assert.ok(calls.length > 0, 'No writes without proof that the isolated upstream was reached');
  const create={...fields,phone:'',password:'a'.repeat(12),role:'VENTAS',enabled:true,branchId:'521c9f1d-624d-4fe5-8c70-9b43396059b2'};
  r=await request('users','POST',create,'limited');check('write permission enforced in BFF',()=>assert.equal(r.status,403));
  r=await request('users','POST',create,'admin',{Origin:'http://untrusted.invalid'});check('cross-origin writes rejected',()=>assert.equal(r.status,403));
  r=await request('users','POST',{...create,password:'short'});check('creation validation rejected before upstream',()=>{assert.equal(r.status,400);assert.ok(r.data.fields.password);});
  r=await request('users','POST',create);check('isolated create succeeds, no secrets returned',()=>{assert.equal(r.status,201);assert.ok(!JSON.stringify(r.data).includes('must-not-leak'));assert.equal('password' in r.data,false);});
  r=await request(`users/${targetId}`,'PATCH',{...fields,phone:'',firstName:'Actualizado'});check('isolated edit persists in stub',()=>{assert.equal(r.status,200);assert.equal(r.data.firstName,'Actualizado');});
  r=await request(`users/${targetId}`,'PATCH',{...fields,phone:'',role:'ADMIN'});check('protected edit properties rejected',()=>assert.equal(r.status,400));
  r=await request(`users/${targetId}/role`,'PATCH',{role:'ALMACEN'});check('isolated role update succeeds',()=>assert.equal(r.data.role,'ALMACEN'));
  r=await request(`users/${targetId}/status`,'PATCH',{enabled:false});check('isolated deactivation succeeds',()=>assert.equal(r.data.enabled,false));
  r=await request(`users/${targetId}/status`,'PATCH',{enabled:true});check('isolated reactivation succeeds',()=>assert.equal(r.data.enabled,true));
  r=await request(`users/${actorId}/status`,'PATCH',{enabled:false});check('last-admin conflict retained',()=>{assert.equal(r.status,409);assert.equal(r.data.error,'Debe existir al menos un administrador activo');});
  for(const status of [400,401,403,404,409,500]){
    fault={status,data:status===400?{detail:'invalid',errors:[{field:'email',message:'private value'}]}:{detail:'private value'}};
    r=await request('users');check(`upstream ${status} safely handled`,()=>{assert.equal(r.status,status===500?502:status);assert.ok(!JSON.stringify(r.data).includes('private value'));if(status===400)assert.ok(r.data.fields.email);});
  }
  fault={status:502,text:true};r=await request('users');check('non-JSON upstream error controlled',()=>{assert.equal(r.status,502);assert.ok(!r.data.error.includes('private'));});
  r=await request('users?size=101');check('pagination bounds validated',()=>assert.equal(r.status,400));
  r=await request('auth/me');check('arbitrary proxy paths denied',()=>assert.equal(r.status,404));
  check('responses not cached',()=>assert.equal(r.response.headers.get('cache-control'),'no-store'));
  console.log(`${checks} isolated BFF integration checks passed. No Spring Boot or PostgreSQL writes.`);
} finally {
  server.kill();upstream.close();
}
