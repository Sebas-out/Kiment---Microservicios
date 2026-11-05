// Exclusively in-memory fixtures. This module never accesses Spring Boot/PostgreSQL.
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { randomUUID } from 'node:crypto';
export async function startFixture() {
  const actorId='321c9f1d-624d-4fe5-8c70-9b43396059b2';
  const targetId='421c9f1d-624d-4fe5-8c70-9b43396059b2';
  const branchIds=['521c9f1d-624d-4fe5-8c70-9b43396059b2','621c9f1d-624d-4fe5-8c70-9b43396059b2','721c9f1d-624d-4fe5-8c70-9b43396059b2'];
  const timestamp='2026-11-04T17:00:00Z';
  const branches=branchIds.map((id,i)=>({id,code:`LOCAL-0${i+1}`,name:['Sucursal Centro (mock)','Sucursal Norte (mock)','Sucursal Sur (mock)'][i],address:['Av. Central 100','Calle Norte 200','Av. Sur 300'][i],phone:'010000000',city:i===0?'Lima':null,ubigeo:i===0?'010101':null,email:i===0?'local@example.invalid':null,branchType:[null,'VENTA','ALMACEN'][i],sunatCode:i===0?'0001':null,enabled:i!==2,createdAt:timestamp,updatedAt:timestamp}));
  const permissions=['USER_READ','USER_CREATE','USER_UPDATE','USER_ROLE_CHANGE','USER_DISABLE','BRANCH_READ','BRANCH_CREATE','BRANCH_UPDATE','BRANCH_DISABLE','PRODUCT_READ','STOCK_READ','SALE_CREATE'];
  const roles=['ADMIN','VENTAS','ALMACEN','VENTAS_ALMACEN'].map((code,i)=>({id:i+1,code,name:code,description:['Administración de usuarios y sucursales.','Acceso al punto de venta.','Operaciones de almacén.','Operaciones de ventas y almacén.'][i]}));
  const users=[{id:actorId,firstName:'Pruebas',lastName:'Aisladas',dni:'00000001',phone:null,email:'qa@example.invalid',role:'ADMIN',enabled:true,branchId:null,createdAt:timestamp,updatedAt:timestamp,lastLoginAt:null},{id:targetId,firstName:'Personal',lastName:'Operativo',dni:'00000002',phone:'010000000',email:'staff@example.invalid',role:'VENTAS',enabled:true,branchId:branchIds[0],createdAt:timestamp,updatedAt:timestamp,lastLoginAt:null}];
  let writes=0;
  const branchWrites=[];
  let avatar=null, avatarStatus=0, avatarWrites=0;
  const normalize=value=>value==null||!value.trim()?null:value.trim();
  const upstream=createServer(async(req,res)=>{
    const url=new URL(req.url,'http://fixture');
    const send=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(data));};
    const problem=(status,detail)=>send(status,{status,detail});
    const workerAvatar=url.pathname.match(/^\/api\/v1\/users\/([^/]+)\/avatar$/);
    if(workerAvatar && workerAvatar[1]!=='me'){
      if(req.headers.authorization!=='Bearer admin')return problem(403,'No autorizado');
      if(workerAvatar[1]!==actorId||!avatar)return problem(404,'Sin imagen');
      res.writeHead(200,{'Content-Type':avatar.type,'Cache-Control':'private, max-age=3600'});
      return res.end(avatar.bytes);
    }
    if(url.pathname==='/api/v1/users/me/avatar'){
      if(!['Bearer admin','Bearer limited','Bearer sales'].includes(req.headers.authorization))return problem(401,'Sesión inválida');
      if(avatarStatus)return problem(avatarStatus,'Mensaje interno de prueba');
      if(req.method==='GET'){
        if(!avatar)return problem(404,'Sin imagen');
        res.writeHead(200,{'Content-Type':avatar.type,'Cache-Control':'private, max-age=3600'});
        return res.end(avatar.bytes);
      }
      if(req.method==='DELETE'){avatar=null;avatarWrites++;res.writeHead(204);return res.end();}
      if(req.method==='POST'){
        const chunks=[];for await(const chunk of req)chunks.push(chunk);
        const parts=await new Response(Buffer.concat(chunks),{headers:{'Content-Type':req.headers['content-type']}}).formData().catch(()=>null);
        const file=parts?.get('file');
        if(!(file instanceof File)||Array.from(parts.keys()).length!==1)return problem(400,'Multipart inválido');
        avatar={type:file.type,bytes:Buffer.from(await file.arrayBuffer())};avatarWrites++;
        return send(200,{message:'Imagen guardada',hasProfileImage:true});
      }
      return problem(405,'Operación inválida');
    }
    let raw=''; for await(const chunk of req) raw+=chunk;
    let data={};try{data=raw?JSON.parse(raw):{};}catch{return problem(400,'Solicitud inválida');}
    if(url.pathname==='/api/v1/auth/login') {
      const token=data.password==='fixture-password'
        ? {'qa@example.invalid':'admin','staff@example.invalid':'sales'}[data.email] : undefined;
      if(!['admin','sales'].includes(token))return problem(401,'Credenciales inválidas');
      return send(200,{accessToken:token,tokenType:'Bearer',expiresIn:900,refreshToken:(token==='admin'?'r':'s').repeat(43)});
    }
    if(url.pathname==='/api/v1/auth/refresh')return problem(401,'Credenciales o sesión inválidas');
    if(url.pathname==='/api/v1/auth/me') {
      const token=req.headers.authorization;
      if(!['Bearer admin','Bearer limited','Bearer sales'].includes(token))return problem(401,'Sesión inválida');
      return send(200,{user:{...users[token==='Bearer sales'?1:0],hasProfileImage:token!=='Bearer sales'&&!!avatar},permissions:token==='Bearer limited'?['USER_READ']:permissions});
    }
    if(url.pathname==='/api/v1/auth/change-password'){
      if(req.headers.authorization!=='Bearer admin'||data.currentPassword!=='fixture-password')return problem(401,'Sesión inválida');
      if(data.newPassword?.length<12)return problem(400,'Contraseña inválida');
      res.writeHead(204);return res.end();
    }
    if(url.pathname==='/api/v1/auth/logout') {res.writeHead(204);return res.end();}
    if(url.pathname==='/api/v1/roles')return send(200,roles);
    const rolePermissions=url.pathname.match(/^\/api\/v1\/roles\/(\d+)\/permissions$/);
    if(rolePermissions)return send(200,(rolePermissions[1]==='1'?permissions:['PRODUCT_READ','STOCK_READ',...(rolePermissions[1]==='2'?['SALE_CREATE']:[])]).map(code=>({code,description:({USER_READ:'Consultar usuarios',USER_CREATE:'Crear usuarios',USER_UPDATE:'Editar usuarios',USER_ROLE_CHANGE:'Cambiar roles',USER_DISABLE:'Activar y desactivar usuarios',BRANCH_READ:'Consultar sucursales',BRANCH_CREATE:'Crear sucursales',BRANCH_UPDATE:'Editar sucursales',BRANCH_DISABLE:'Activar y desactivar sucursales',PRODUCT_READ:'Consultar productos',STOCK_READ:'Consultar existencias',SALE_CREATE:'Registrar ventas'})[code]})));
    function page(items){const page=Number(url.searchParams.get('page')??0),size=Number(url.searchParams.get('size')??20);return{content:items.slice(page*size,(page+1)*size),page,size,totalElements:items.length,totalPages:Math.ceil(items.length/size)};}
    if(url.pathname==='/api/v1/branches') {
      if(req.method==='GET')return send(200,page(branches.filter(b=>(!url.searchParams.has('name')||b.name.toLowerCase().includes(url.searchParams.get('name').toLowerCase()))&&(!url.searchParams.has('enabled')||String(b.enabled)===url.searchParams.get('enabled'))&&(!url.searchParams.has('type')||b.branchType===url.searchParams.get('type'))&&(!url.searchParams.has('city')||(b.city??'').toLowerCase().includes(url.searchParams.get('city').toLowerCase()))&&(!url.searchParams.has('code')||b.code===url.searchParams.get('code'))&&(!url.searchParams.has('sunatCode')||b.sunatCode===url.searchParams.get('sunatCode')))));
      if(req.method==='POST') {
        if(branches.some(b=>b.code===data.code.toUpperCase()))return problem(409,'El código de sucursal ya está registrado');
        const optional=Object.fromEntries(['address','phone','city','ubigeo','email','sunatCode'].map(k=>[k,normalize(data[k])]));
        if(optional.sunatCode&&branches.some(b=>b.sunatCode===optional.sunatCode))return problem(409,'El código de establecimiento SUNAT ya está registrado');
        if(optional.email)optional.email=optional.email.toLowerCase();
        const b={...data,...optional,code:data.code.toUpperCase(),id:randomUUID(),createdAt:timestamp,updatedAt:timestamp};branches.push(b);branchWrites.push({method:'POST',body:data});writes++;return send(201,b);
      }
    }
    const branchPath=url.pathname.match(/^\/api\/v1\/branches\/([^/]+)(?:\/(status))?$/);
    if(branchPath){const branch=branches.find(b=>b.id===branchPath[1]);if(!branch)return problem(404,'Recurso no encontrado');if(req.method==='GET')return send(200,branch);if(branchPath[2]==='status'){const count=users.filter(u=>u.role!=='ADMIN'&&u.enabled&&u.branchId===branch.id).length;if(!data.enabled&&count)return problem(409,`No se puede desactivar la sucursal porque tiene ${count} trabajador${count===1?' activo asignado':'es activos asignados'}. Reasígnelos o desactívelos primero.`);}if(branchPath[2]==='status')branch.enabled=data.enabled;
      else {
        if(data.sunatCode!=null&&normalize(data.sunatCode)&&branches.some(b=>b.id!==branch.id&&b.sunatCode===normalize(data.sunatCode)))return problem(409,'El código de establecimiento SUNAT ya está registrado');
        if(data.name!=null)branch.name=data.name.trim();
        for(const key of ['address','phone','city','ubigeo','email','sunatCode'])if(data[key]!=null)branch[key]=normalize(data[key]);
        if(branch.email)branch.email=branch.email.toLowerCase();
        if(data.branchType!=null)branch.branchType=data.branchType;
      }
      branchWrites.push({method:'PATCH',body:data});branch.updatedAt=timestamp;writes++;return send(200,branch);}
    if(url.pathname==='/api/v1/users'){
      if(req.method==='GET')return send(200,page(users.map(user=>({...user,hasProfileImage:user.id===actorId&&!!avatar})).filter(u=>['role','email','dni','branchId'].every(k=>!url.searchParams.has(k)||u[k]===url.searchParams.get(k))&&(!url.searchParams.has('name')||`${u.firstName} ${u.lastName}`.toLowerCase().includes(url.searchParams.get('name').toLowerCase()))&&(!url.searchParams.has('enabled')||String(u.enabled)===url.searchParams.get('enabled')))));
      if(req.method==='POST'){if(data.role!=='ADMIN'&&!branches.some(b=>b.id===data.branchId&&b.enabled))return problem(409,'No se puede asignar una sucursal inactiva');const {password,...fields}=data;const user={...fields,id:randomUUID(),branchId:data.role==='ADMIN'?null:data.branchId,createdAt:timestamp,updatedAt:timestamp,lastLoginAt:null};users.push(user);writes++;return send(201,user);}
    }
    const userPath=url.pathname.match(/^\/api\/v1\/users\/([^/]+)(?:\/(role|branch|status))?$/);
    if(userPath){const user=users.find(u=>u.id===userPath[1]);if(!user)return problem(404,'Recurso no encontrado');if(req.method==='GET')return send(200,{...user,hasProfileImage:user.id===actorId&&!!avatar});const action=userPath[2];if(action==='role'){if(user.id===actorId)return problem(409,'No puede cambiar su propio rol');if(data.role==='ADMIN'){data.branchId=null;}else if(!branches.some(b=>b.id===(data.branchId??user.branchId)&&b.enabled))return problem(409,'No se puede asignar una sucursal inactiva');}if(action==='branch'){if(user.role==='ADMIN')return problem(409,'Los administradores tienen alcance global y no pueden ser asignados a una sucursal');if(!branches.some(b=>b.id===data.branchId&&b.enabled))return problem(409,'No se puede asignar una sucursal inactiva');}if(action==='status'&&!data.enabled&&user.role==='ADMIN'&&users.filter(u=>u.role==='ADMIN'&&u.enabled).length<=1)return problem(409,'Debe existir al menos un administrador activo');Object.assign(user,data,{updatedAt:timestamp});writes++;return send(200,user);}
    return problem(404,'Recurso no encontrado');
  });
  upstream.listen(0,'127.0.0.1');await once(upstream,'listening');
  const probe=createServer();probe.listen(0,'127.0.0.1');await once(probe,'listening');const port=probe.address().port;await new Promise(r=>probe.close(r));
  // Separate host from the real localhost session so browser cookies cannot collide.
  const origin=`http://127.0.0.1:${port}`;
  const server=spawn(process.execPath,['node_modules/next/dist/bin/next','start','-p',String(port)],{cwd:process.cwd(),env:{...process.env,APP_ORIGIN:origin,AUTH_API_URL:`http://127.0.0.1:${upstream.address().port}`},stdio:['ignore','pipe','pipe']});
  server.stdout.on('data',()=>{});server.stderr.on('data',()=>{});
  let ready=false;for(let i=0;i<120;i++){if(server.exitCode!==null)break;try{await fetch(origin);ready=true;break;}catch{await new Promise(r=>setTimeout(r,200));}}
  const stop=()=>{server.kill();upstream.close();};
  if(!ready){stop();throw new Error('Fixture server could not start');}
  return{origin,actorId,targetId,branchIds,branches,users,branchWrites,get writes(){return writes;},get avatarWrites(){return avatarWrites;},setAvatarStatus(status){avatarStatus=status;},stop};
}
if(process.argv.includes('--serve')){const fixture=await startFixture();console.log(`ISOLATED_FIXTURE_UI=${fixture.origin}`);process.on('SIGINT',()=>{fixture.stop();process.exit(0);});process.on('SIGTERM',()=>{fixture.stop();process.exit(0);});}
