import { test } from 'node:test';
import assert from 'node:assert/strict';
import { sourceModule } from './users-test-loader.mjs';
const b=await import(await sourceModule('src/features/branches/contracts.ts'));
const u=await import(await sourceModule('src/features/users/contracts.ts'));
const o=await import(await sourceModule('src/features/users/operations.ts'));
const id='521c9f1d-624d-4fe5-8c70-9b43396059b2';
const branch={city:null,ubigeo:null,email:null,branchType:null,sunatCode:null,id,code:'SC-01',name:'Sucursal de prueba aislada',address:null,phone:null,enabled:true,createdAt:'2026-11-04T00:00:00Z',updatedAt:'2026-11-04T00:00:00Z'};
const fields={firstName:'Ana',lastName:'Prueba',dni:'00000000',phone:null,email:'fixture@example.com',password:'a'.repeat(12),role:'VENTAS',enabled:true};
test('branches match V5 limits and reject unsupported fiscal or immutable properties',()=>{
  const create={code:'sc-01',name:'Sucursal',address:'',phone:'',branchType:'VENTA',enabled:true};
  assert.equal(b.branchCreateSchema.safeParse(create).success,true);
  for(const patch of [{code:'a'},{code:'HAS SPACE'},{name:' '},{address:'a'.repeat(201)},{phone:'abc'},{city:'a'.repeat(101)},{type:'VENTA'},{email:'invalid'},{companyId:id},{ruc:'00000000000'},{sunatCode:'abcd'},{ubigeo:'15x101'}]) assert.equal(b.branchCreateSchema.safeParse({...create,...patch}).success,false);
  assert.equal(b.branchEditSchema.safeParse({name:'Sucursal',address:'',phone:'',code:'NEW'}).success,false);
});
test('every branches operation has its real permission and physical deletion is denied',()=>{
  for(const [method,path,permission] of [['GET',['branches'],'BRANCH_READ'],['POST',['branches'],'BRANCH_CREATE'],['GET',['branches',id],'BRANCH_READ'],['PATCH',['branches',id],'BRANCH_UPDATE'],['PATCH',['branches',id,'status'],'BRANCH_DISABLE']]) assert.equal(o.operation(method,path,new URLSearchParams()).permission,permission);
  assert.equal(o.operation('DELETE',['branches',id],new URLSearchParams()),null);
});
test('operational accounts require a branch while ADMIN is global',()=>{
  for(const role of ['VENTAS','ALMACEN','VENTAS_ALMACEN']) {
    assert.equal(u.createSchema.safeParse({...fields,role}).success,false);
    assert.equal(u.createSchema.safeParse({...fields,role,branchId:id}).success,true);
  }
  assert.equal(u.createSchema.safeParse({...fields,role:'ADMIN',branchId:null}).success,true);
  assert.equal(u.createSchema.safeParse({...fields,role:'ADMIN',branchId:id}).success,false);
});
test('role and branch payloads match separate endpoints',()=>{
  assert.equal(u.changeRoleSchema.safeParse({role:'VENTAS',branchId:id}).success,true);
  assert.equal(u.changeRoleSchema.safeParse({role:'ADMIN',branchId:id}).success,false);
  assert.equal(u.changeRoleSchema.safeParse({role:'ADMIN',branchId:null}).success,true);
  assert.equal(u.changeBranchSchema.safeParse({branchId:null}).success,false);
  assert.equal(o.operation('PATCH',['users',id,'branch'],new URLSearchParams()).permission,'USER_UPDATE');
});
test('branch selector loads records beyond the first hundred',async()=>{
  const requested=[];
  const items=await b.collectBranches(async page=>{requested.push(page);return{content:Array.from({length:page===0?100:1},(_,i)=>({...branch,id:`${String(page*100+i+1).padStart(8,'0')}-624d-4fe5-8c70-9b43396059b2`})),page,size:100,totalElements:101,totalPages:2};});
  assert.deepEqual(requested,[0,1]);assert.equal(items.length,101);
});
test('branch selector fails rather than silently exposing incomplete options',async()=>{
  await assert.rejects(b.collectBranches(async page=>page===0?{content:[branch],page:0,size:1,totalElements:2,totalPages:2}:Promise.reject(new Error('offline'))));
  await assert.rejects(b.collectBranches(async()=>({content:[branch],page:0,size:1,totalElements:2,totalPages:1})));
});
test('active worker conflict stays readable and arbitrary error text remains hidden',()=>{
  const detail='No se puede desactivar la sucursal porque tiene 3 trabajadores activos asignados. Reasígnelos o desactívelos primero.';
  assert.equal(o.backendProblem(409,{detail}).error,detail);
  assert.equal(o.backendProblem(409,{detail:'No se puede asignar una sucursal inactiva'}).error,'No se puede asignar una sucursal inactiva');
  assert.ok(!o.backendProblem(409,{detail:'private database connection secret'}).error.includes('secret'));
});
test('branch filters forward real enabled and paging parameters',()=>{
  const op=o.operation('GET',['branches'],new URLSearchParams('enabled=true&page=3&size=100&name=Centro'));
  assert.ok(op.path.includes('enabled=true'));assert.ok(op.path.includes('page=3'));
  assert.equal(b.branchQuerySchema.safeParse({size:101}).success,false);
});
