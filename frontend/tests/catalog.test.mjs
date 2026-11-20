import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { sourceModule } from './users-test-loader.mjs';
const api = await import(await sourceModule('src/features/products/catalog-api.ts'));
const { catalogPermission } = await import(await sourceModule('src/features/products/catalog-operations.ts'));
const id = '41eaf02c-f304-4357-984b-64d199593afc';
const originalFetch = globalThis.fetch;
const navigatorDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
afterEach(() => { globalThis.fetch = originalFetch; if (navigatorDescriptor) Object.defineProperty(globalThis, 'navigator', navigatorDescriptor); else delete globalThis.navigator; });
test('catalog BFF rejects arbitrary paths, physical product deletion and unsupported methods', () => {
  for (const [method,path] of [['GET','auth/me'],['DELETE',`products/${id}`],['POST',`categories/${id}/status`],['GET','products/../auth'],['GET','products/not-a-uuid'],['PUT','stock/adjust']]) assert.equal(catalogPermission(method,path), undefined);
  assert.equal(catalogPermission('GET','products/barcode-data'), 'PRODUCT_READ');
  assert.equal(catalogPermission('POST',`products/${id}/images`), 'PRODUCT_WRITE');
  assert.equal(catalogPermission('PUT',`products/${id}/images/order`), 'PRODUCT_WRITE');
  assert.equal(catalogPermission('GET',`products/${id}/images/${id}`), 'PRODUCT_READ');
  assert.equal(catalogPermission('POST','stock/adjust'), 'STOCK_ADJUST');
  assert.equal(catalogPermission('GET','stock-movements'), 'MOVEMENT_READ');
  assert.equal(catalogPermission('GET','reports/inventory/export'), 'STOCK_REPORT_READ');
});
test('catalog mutations retain operation identity and surface field validation errors', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/catalog/products'); assert.equal(options.credentials, 'same-origin');
    assert.equal(options.headers['Idempotency-Key'], id); assert.equal(JSON.parse(options.body).name, 'QA');
    return Response.json({error:'Invalid',fields:[{field:'price',message:'Precio obligatorio'}]}, {status:400});
  };
  await assert.rejects(api.catalogRequest('products','POST',{name:'QA'},id), /Precio obligatorio/);
});
test('image uploads let fetch set the multipart boundary and support empty responses', async () => {
  const form = new FormData(); form.set('file', new Blob(['fixture'], {type:'image/png'}), 'fixture.png');
  globalThis.fetch = async (url,options) => { assert.equal(options.body,form); assert.equal(options.headers['Content-Type'],undefined); return new Response(null,{status:204}); };
  assert.equal(await api.catalogRequest(`products/${id}/images`,'POST',form), undefined);
});
test('expired stock writes are not replayed after session renewal', async () => {
  Object.defineProperty(globalThis,'navigator',{configurable:true,value:{locks:{request:async(_,fn)=>fn()}}});
  let writes = 0;
  globalThis.fetch = async url => url.startsWith('/api/catalog/') ? (writes++,Response.json({}, {status:401})) : Response.json({});
  await assert.rejects(api.catalogRequest('stock/adjust','POST',{quantity:1}), /renovada/);
  assert.equal(writes,1);
});
test('catalog pagination collects subsequent pages and rejects malformed pagination', async () => {
  let calls = 0;
  globalThis.fetch = async url => { assert.ok(url.includes(`page=${calls}`)); return Response.json({content:[{id:++calls}],page:calls-1,size:100,totalPages:2,totalElements:2}); };
  assert.deepEqual(await api.allRecords('colors?enabled=true'),[{id:1},{id:2}]);
  globalThis.fetch = async () => Response.json({content:{},totalPages:2});
  await assert.rejects(api.allRecords('colors'), /paginaci/);
});
test('global media remains independent from color galleries and inactive variants disappear', () => {
  const result = api.mapProduct({id,name:'QA',categoryName:'Ropa',variants:[{id:'v',enabled:true,colorId:'blue',colorName:'Azul',sizeName:'S',price:20,wholesalePrice:null,stockByBranch:{branch:3}},{id:'disabled',enabled:false,colorId:'red',colorName:'Rojo'}],images:[{id:'global',colorId:null},{id:'blue-photo',colorId:'blue'}]});
  assert.equal(result.image, `/api/catalog/products/${id}/images/global`);
  assert.deepEqual(result.colorImages.Azul,[`/api/catalog/products/${id}/images/blue-photo`]);
  assert.equal(result.variants.length,1); assert.equal(result.inactiveVariants[0].id,"disabled"); assert.equal(result.variants[0].stock.branch,3);
});
test('inventory spreadsheet downloads preserve the binary XLSX payload', async () => {
  globalThis.fetch=async()=>new Response(new Uint8Array([80,75,3,4]),{headers:{'Content-Type':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'}});
  const blob=await api.catalogRequest('reports/inventory/export');
  assert.deepEqual([...new Uint8Array(await blob.arrayBuffer())],[80,75,3,4]);
});
