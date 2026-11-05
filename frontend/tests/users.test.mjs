import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { sourceModule } from './users-test-loader.mjs';
const c = await import(await sourceModule('src/features/users/contracts.ts'));
const o = await import(await sourceModule('src/features/users/operations.ts'));
const client = await import(await sourceModule('src/features/users/client.ts'));
const create = { firstName:'Ana', lastName:'Prueba', dni:'00000000', phone:'', email:'fixture@example.com', password:'a'.repeat(12), role:'VENTAS', enabled:true, branchId:'521c9f1d-624d-4fe5-8c70-9b43396059b2' };
const id = '321c9f1d-624d-4fe5-8c70-9b43396059b2';
const originalFetch = globalThis.fetch;
const originalNavigator = Object.getOwnPropertyDescriptor(globalThis,'navigator');
afterEach(() => { globalThis.fetch = originalFetch; if (originalNavigator) Object.defineProperty(globalThis,'navigator',originalNavigator); else delete globalThis.navigator; });
test('create validates exact backend password limits, DNI, phone and names', () => {
  assert.equal(c.createSchema.parse(create).phone,null);
  for (const changes of [{ password:' '.repeat(12) },{ password:'a'.repeat(11) },{ password:'a'.repeat(129) },{ dni:'123' },{ phone:'abc' },{ firstName:' ' },{ firstName:'a'.repeat(81) },{ email:'invalid' },{ role:'ROOT' }]) assert.equal(c.createSchema.safeParse({...create,...changes}).success,false);
  assert.equal(c.createSchema.safeParse({...create, password:'a'.repeat(128),phone:null}).success,true);
});
test('edit cannot contain role, enabled, passwords or branch assignments', () => {
  const edit = {firstName:'Ana',lastName:'Prueba',dni:'00000000',phone:'',email:'fixture@example.com'};
  assert.equal(c.editSchema.safeParse(edit).success,true);
  for (const key of ['role','password','enabled','branchId']) assert.equal(c.editSchema.safeParse({...edit,[key]:'x'}).success,false);
});
test('server pagination and exact email filters are preserved', () => {
  assert.equal(c.userQuery(' fixture@example.com ','email','ADMIN','true',2).toString(),'page=2&size=10&email=fixture%40example.com&role=ADMIN&enabled=true');
  assert.equal(c.querySchema.safeParse({page:-1}).success,false);
  assert.equal(c.querySchema.safeParse({size:101}).success,false);
});
test('allowlist rejects arbitrary paths and unsupported methods', () => {
  for (const [method,path] of [['DELETE',['users',id]],['PATCH',['roles','1']],['GET',['auth','me']],['GET',['users','../auth']],['GET',['users',id,'anything']],['POST',['users',id,'role']]]) assert.equal(o.operation(method,path,new URLSearchParams()),null);
});
test('each write requires its specific backend permission', () => {
  assert.equal(o.operation('POST',['users'],new URLSearchParams()).permission,'USER_CREATE');
  assert.equal(o.operation('PATCH',['users',id],new URLSearchParams()).permission,'USER_UPDATE');
  assert.equal(o.operation('PATCH',['users',id,'role'],new URLSearchParams()).permission,'USER_ROLE_CHANGE');
  assert.equal(o.operation('PATCH',['users',id,'status'],new URLSearchParams()).permission,'USER_DISABLE');
  assert.equal(o.permitted('ADMIN',true,['USER_READ'],'USER_CREATE'),false);
  assert.equal(o.permitted('VENTAS',true,['USER_READ'],'USER_READ'),false);
  assert.equal(o.permitted('ADMIN',false,['USER_READ'],'USER_READ'),false);
});
test('business restrictions survive while unexpected upstream details never leak', () => {
  assert.equal(o.backendProblem(409,{detail:'Debe existir al menos un administrador activo'}).error,'Debe existir al menos un administrador activo');
  assert.equal(JSON.stringify(o.backendProblem(500,{detail:'secret database password'})).includes('secret'),false);
  assert.equal(JSON.stringify(o.backendProblem(400,{errors:[{field:'password',message:'secret'}]})).includes('secret'),false);
  for (const data of [null,'text',{}, {error:'different shape'}]) assert.equal(typeof o.backendProblem(403,data).error,'string');
});
test('validated response strips password hashes and token fields', () => {
  const user = c.recordSchema.parse({id,firstName:'Ana',lastName:'Prueba',dni:null,phone:null,email:create.email,role:'ADMIN',enabled:true,branchId:null,createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),lastLoginAt:null,passwordHash:'secret',refreshToken:'secret'});
  assert.equal('passwordHash' in user,false); assert.equal('refreshToken' in user,false);
});
test('write uses same-origin BFF and preserves server conflicts without fake success', async () => {
  globalThis.fetch = async (url,options) => { assert.equal(url,`/api/management/users/${id}/status`); assert.equal(options.credentials,'same-origin'); assert.deepEqual(JSON.parse(options.body),{enabled:false}); return Response.json({error:'Debe existir al menos un administrador activo'},{status:409}); };
  await assert.rejects(client.usersRequest(`users/${id}/status`,'PATCH',{enabled:false}), e => e.status===409);
});
test('expired writes are never replayed automatically after session restoration', async () => {
  Object.defineProperty(globalThis,'navigator',{configurable:true,value:{locks:{request:async (_,fn)=>fn()}}});
  let writes=0;
  globalThis.fetch = async url => { if(url.startsWith('/api/management/')) { writes++; return Response.json({error:'expired'},{status:401}); } return Response.json({user:{},permissions:[]}); };
  await assert.rejects(client.usersRequest('users','POST',create),e=>e.status===401 && e.message.includes('renovada'));
  assert.equal(writes,1);
});
test('reads retry after existing session coordinator restores authentication', async () => {
  Object.defineProperty(globalThis,'navigator',{configurable:true,value:{locks:{request:async (_,fn)=>fn()}}});
  let reads=0;
  globalThis.fetch = async url => { if(url.startsWith('/api/management/')) return ++reads===1 ? Response.json({}, {status:401}) : Response.json({content:[]}); return Response.json({}); };
  assert.deepEqual(await client.usersRequest('users'),{content:[]}); assert.equal(reads,2);
});
test('network and non-JSON server errors remain understandable', async () => {
  globalThis.fetch = async()=>{throw new Error('offline');};
  await assert.rejects(client.usersRequest('users'),e=>e.status===503);
  globalThis.fetch = async()=>new Response('gateway down',{status:502});
  await assert.rejects(client.usersRequest('users'),e=>e.status===502);
});
