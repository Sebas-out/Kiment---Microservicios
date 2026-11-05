import assert from 'node:assert/strict';
import { startFixture } from './management-fixture.mjs';
const fixture=await startFixture();let checks=0;
async function request(path,method='GET',body,token='admin',headers={}){
  const res=await fetch(`${fixture.origin}/api/management/${path}`,{method,headers:{...(token?{Cookie:`__Host-kiments-access=${token}`} : {}),...(body!==undefined?{Origin:fixture.origin,'Content-Type':'application/json'}:{}),...headers},...(body!==undefined?{body:JSON.stringify(body)}:{})});
  return{status:res.status,data:await res.json()};
}
function check(label,fn){fn();console.log(`PASS ${label}`);checks++;}
try{
  let r=await request('branches','GET',undefined,'');check('anonymous branches rejected',()=>assert.equal(r.status,401));
  r=await request('branches','GET',undefined,'sales');check('operative branches access rejected',()=>assert.equal(r.status,403));
  r=await request('branches','GET',undefined,'limited');check('BRANCH_READ required independently',()=>assert.equal(r.status,403));
  const denied=await fetch(`${fixture.origin}/sucursales`,{headers:{Cookie:'__Host-kiments-access=limited'}});check('branch page denies missing permission',()=>assert.equal(denied.status,200));const deniedHtml=await denied.text();check('denied page has no branch UI',()=>assert.ok(deniedHtml.includes('No tienes acceso a Sucursales')));
  r=await request('branches?enabled=true&page=0&size=1');check('active server pagination',()=>{assert.equal(r.status,200);assert.equal(r.data.content.length,1);assert.equal(r.data.totalPages,2);});
  const first=r.data.content[0];r=await request('branches?enabled=true&page=1&size=1');check('second page available',()=>assert.notEqual(r.data.content[0].id,first.id));
  r=await request('branches?name=Norte');check('name search forwarded',()=>assert.equal(r.data.content.length,1));
  const before=fixture.writes;
  r=await request('branches','POST',{code:'QA',name:'QA',address:'',phone:'',enabled:true,branchType:'VENTA',companyId:'521c9f1d-624d-4fe5-8c70-9b43396059b2'});check('unsupported companyId rejected before persistence',()=>{assert.equal(r.status,400);assert.equal(fixture.writes,before);});
  r=await request('branches','POST',{code:'QA',name:'QA',address:'',phone:'',enabled:true,branchType:'VENTA'},'limited');check('create permission required',()=>assert.equal(r.status,403));
  r=await request('branches','POST',{code:'QA',name:'QA',address:'',phone:'',enabled:true,branchType:'VENTA'},'admin',{Origin:'http://untrusted.invalid'});check('branch write CSRF protection',()=>assert.equal(r.status,403));
  r=await request('branches','POST',{code:'QA',name:'QA',address:'',phone:'',enabled:true,branchType:'VENTA'});check('branch creation in memory',()=>{assert.equal(r.status,201);assert.equal(r.data.code,'QA');});const created=r.data.id;
  const v5={code:'V5',name:'Sucursal V5 aislada',address:'Calle de prueba',phone:'010000000',city:'Lima',ubigeo:'010202',email:'NEW@EXAMPLE.INVALID',branchType:'MIXTO',sunatCode:'0002',enabled:true};
  r=await request('branches','POST',v5);check('V5 creation carries every supported attribute',()=>{assert.equal(r.status,201);assert.equal(r.data.branchType,'MIXTO');assert.equal(r.data.ubigeo,'010202');assert.equal(r.data.sunatCode,'0002');assert.equal(r.data.email,'new@example.invalid');});const v5id=r.data.id;
  r=await request(`branches/${v5id}`);check('V5 read retains leading zeroes',()=>{assert.equal(r.data.sunatCode,'0002');assert.equal(r.data.ubigeo,'010202');});
  r=await request(`branches/${fixture.branchIds[0]}`);check('legacy branch remains null type',()=>assert.equal(r.data.branchType,null));
  r=await request(`branches/${fixture.branchIds[0]}`,'PATCH',{city:'Arequipa'});check('partial PATCH retains untouched V5 fields',()=>{assert.equal(r.data.city,'Arequipa');assert.equal(r.data.email,'local@example.invalid');assert.equal(r.data.ubigeo,'010101');assert.equal(r.data.sunatCode,'0001');assert.equal(r.data.branchType,null);assert.deepEqual(fixture.branchWrites.at(-1).body,{city:'Arequipa'});});
  r=await request(`branches/${fixture.branchIds[0]}`,'PATCH',{city:null,email:null,ubigeo:null,sunatCode:null,branchType:null});check('explicit null preserves existing values',()=>{assert.equal(r.data.city,'Arequipa');assert.equal(r.data.sunatCode,'0001');});
  r=await request(`branches/${fixture.branchIds[0]}`,'PATCH',{city:' ',email:'',ubigeo:'',sunatCode:''});check('blank optional fields clear to null without clearing phone',()=>{assert.equal(r.data.city,null);assert.equal(r.data.email,null);assert.equal(r.data.ubigeo,null);assert.equal(r.data.sunatCode,null);assert.equal(r.data.phone,'010000000');});
  r=await request(`branches/${fixture.branchIds[0]}`,'PATCH',{branchType:'ALMACEN'});check('legacy type can be classified explicitly',()=>assert.equal(r.data.branchType,'ALMACEN'));
  r=await request('branches','POST',{...v5,code:'V5-SECOND'});check('duplicate SUNAT on create is readable',()=>{assert.equal(r.status,409);assert.ok(r.data.error.includes('SUNAT'));});
  r=await request(`branches/${created}`,'PATCH',{sunatCode:'0002'});check('duplicate SUNAT on edit is readable',()=>{assert.equal(r.status,409);assert.ok(r.data.error.includes('SUNAT'));});
  r=await request(`branches/${created}`);check('SUNAT conflict did not change the target',()=>assert.equal(r.data.sunatCode,null));
  r=await request('branches?type=MIXTO&city=Lima&sunatCode=0002&code=V5');check('all V5 filters forwarded to backend',()=>{assert.equal(r.status,200);assert.equal(r.data.content.length,1);assert.equal(r.data.content[0].id,v5id);});
  r=await request('branches','POST',{...v5,branchType:undefined});check('type mandatory on create',()=>{assert.equal(r.status,400);assert.ok(r.data.fields.branchType);});
  r=await request('branches','POST',{...v5,ubigeo:'12a456'});check('ubigeo validation at BFF',()=>{assert.equal(r.status,400);assert.ok(r.data.fields.ubigeo);});
  r=await request('branches','POST',{...v5,sunatCode:'123'});check('SUNAT validation at BFF',()=>{assert.equal(r.status,400);assert.ok(r.data.fields.sunatCode);});
  r=await request('branches','POST',{...v5,email:'invalid'});check('email validation at BFF',()=>{assert.equal(r.status,400);assert.ok(r.data.fields.email);});
  r=await request('branches/821c9f1d-624d-4fe5-8c70-9b43396059b2');check('missing branch returns controlled 404',()=>assert.equal(r.status,404));
  r=await request('branches','GET',undefined,'expired');check('expired branch session returns 401',()=>assert.equal(r.status,401));
  r=await request('branches','POST',{code:'QA',name:'Duplicada',address:'',phone:'',enabled:true,branchType:'VENTA'});check('duplicate code conflict readable',()=>{assert.equal(r.status,409);assert.ok(r.data.error.includes('código'));});
  r=await request(`branches/${created}`,'PATCH',{name:'Editada',address:'Dirección',phone:''});check('edit persists in isolated stub',()=>assert.equal(r.data.name,'Editada'));
  r=await request(`branches/${created}`,'PATCH',{name:'Editada',address:'',phone:'',code:'NEW'});check('immutable code excluded',()=>assert.equal(r.status,400));
  r=await request(`branches/${fixture.branchIds[0]}/status`,'PATCH',{enabled:false});check('active workers prevent deactivation',()=>{assert.equal(r.status,409);assert.ok(r.data.error.includes('trabajador activo'));});
  r=await request(`branches/${fixture.branchIds[0]}`);check('conflict did not modify status',()=>assert.equal(r.data.enabled,true));
  r=await request(`users/${fixture.targetId}/branch`,'PATCH',{branchId:fixture.branchIds[1]});check('worker reassigned in backend operation',()=>assert.equal(r.data.branchId,fixture.branchIds[1]));
  r=await request(`users/${fixture.targetId}/branch`,'PATCH',{branchId:fixture.branchIds[2]});check('inactive branch rejected',()=>assert.equal(r.status,409));
  r=await request(`users/${fixture.targetId}`);check('failed reassignment preserved prior branch',()=>assert.equal(r.data.branchId,fixture.branchIds[1]));
  r=await request(`users/${fixture.targetId}/role`,'PATCH',{role:'ADMIN',branchId:null});check('operative to ADMIN clears branch',()=>{assert.equal(r.data.role,'ADMIN');assert.equal(r.data.branchId,null);});
  r=await request(`users/${fixture.targetId}/role`,'PATCH',{role:'ALMACEN',branchId:fixture.branchIds[0]});check('ADMIN to operative assigns branch atomically',()=>{assert.equal(r.data.role,'ALMACEN');assert.equal(r.data.branchId,fixture.branchIds[0]);});
  const create={firstName:'Prueba',lastName:'Aislada',dni:'00000003',phone:null,email:'new@example.invalid',password:'a'.repeat(12),role:'VENTAS',enabled:true};
  r=await request('users','POST',create);check('operative creation without branch blocked',()=>{assert.equal(r.status,400);assert.ok(r.data.fields.branchId);});
  r=await request('users','POST',{...create,branchId:fixture.branchIds[1]});check('operative creation with actual fixture id',()=>assert.equal(r.data.branchId,fixture.branchIds[1]));
  r=await request('users','POST',{...create,role:'ADMIN',branchId:fixture.branchIds[1]});check('ADMIN cannot be assigned a physical branch',()=>assert.equal(r.status,400));
  r=await request(`users?branchId=${fixture.branchIds[1]}`);check('user branch filter runs on server',()=>assert.ok(r.data.content.every(u=>u.branchId===fixture.branchIds[1])));
  r=await request(`branches/${created}/status`,'PATCH',{enabled:false});check('branch with no active workers deactivated',()=>assert.equal(r.data.enabled,false));
  r=await request(`branches/${created}/status`,'PATCH',{enabled:true});check('branch reactivated',()=>assert.equal(r.data.enabled,true));
  console.log(`${checks} isolated branch/assignment checks passed; zero Spring Boot/PostgreSQL mutations.`);
}finally{fixture.stop();}
