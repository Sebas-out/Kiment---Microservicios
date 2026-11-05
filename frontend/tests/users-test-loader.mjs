import ts from 'typescript';
import { readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';

// Compile the actual source without changing application import conventions.
const cache = new Map();
export async function sourceModule(path) {
  path = resolve(path);
  if (cache.has(path)) return cache.get(path);
  let source = ts.transpileModule(await readFile(path, 'utf8'), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText;
  const imports = [...source.matchAll(/from ["']([^"']+)["']/g)];
  for (const [, specifier] of imports) {
    let url;
    if (specifier.startsWith('.')) url = await sourceModule(resolve(dirname(path), `${specifier}.ts`));
    else if (specifier.startsWith('@/')) url = await sourceModule(resolve('src', `${specifier.slice(2)}.ts`));
    else url = import.meta.resolve(specifier);
    source = source.replaceAll(`from "${specifier}"`, `from "${url}"`).replaceAll(`from '${specifier}'`, `from '${url}'`);
  }
  const url = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
  cache.set(path,url); return url;
}
