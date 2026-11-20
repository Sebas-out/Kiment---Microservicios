const id = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const rules: [string, RegExp, string][] = [
  ["GET", new RegExp(`^(categories|colors|sizes)(/${id})?$`), "PRODUCT_READ"],
  ["POST", /^categories$/, "CATEGORY_WRITE"],
  ["PUT", new RegExp(`^categories/${id}$`), "CATEGORY_WRITE"],
  ["PATCH", new RegExp(`^categories/${id}/status$`), "CATEGORY_WRITE"],
  ["POST", /^(colors|sizes)$/, "VARIANT_WRITE"],
  ["PUT", new RegExp(`^(colors|sizes)/${id}$`), "VARIANT_WRITE"],
  ["PATCH", new RegExp(`^(colors|sizes)/${id}/status$`), "VARIANT_WRITE"],
  ["GET", new RegExp(`^(products(/(variants|barcode-data|${id}))?|variants/${id}|products/${id}/images(/${id}(/metadata)?)?)$`), "PRODUCT_READ"],
  ["POST", new RegExp(`^products(/${id}/(variants|images))?$`), "PRODUCT_WRITE"],
  ["PUT", new RegExp(`^((products|variants)/${id}|products/${id}/images/order)$`), "PRODUCT_WRITE"],
  ["PATCH", new RegExp(`^(products|variants)/${id}/status$`), "PRODUCT_WRITE"],
  ["DELETE", new RegExp(`^products/${id}/images/${id}$`), "PRODUCT_WRITE"],
  ["GET", new RegExp(`^stock(/variants/${id})?$`), "STOCK_READ"],
  ["GET", /^stock-movements$/, "MOVEMENT_READ"],
  ["GET", new RegExp(`^stock-transfers(/${id})?$`), "STOCK_READ"],
  ["POST", /^(stock\/adjust|stock-transfers)$/, "STOCK_ADJUST"],
  ["GET", /^reports\/inventory\/(summary|export)$/, "STOCK_REPORT_READ"],
];
export function catalogPermission(method: string, path: string): string | undefined {
  return rules.find(([verb, pattern]) => verb === method && pattern.test(path))?.[2];
}
