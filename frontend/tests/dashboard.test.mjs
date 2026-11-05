import {test} from 'node:test';
import assert from 'node:assert/strict';
import {limaDate,presetRange,validRange,formatMoney} from '../src/features/dashboard/filters.ts';
test('Lima date respects midnight UTC offset',()=>assert.equal(limaDate(new Date('2026-11-04T02:00:00Z')),'2026-11-03'));
test('inclusive seven and thirty day ranges cross month boundaries',()=>{assert.deepEqual(presetRange('week','2026-11-04'),{from:'2026-10-29',to:'2026-11-04'});assert.deepEqual(presetRange('month','2026-11-04'),{from:'2026-10-06',to:'2026-11-04'});});
test('yesterday crosses year boundary',()=>assert.deepEqual(presetRange('yesterday','2026-01-01'),{from:'2025-12-31',to:'2025-12-31'}));
test('invalid dates and reversed ranges are rejected',()=>{assert.equal(validRange('2026-02-30','2026-03-01'),false);assert.equal(validRange('2026-11-05','2026-11-04'),false);assert.equal(validRange('','2026-11-04'),false);assert.equal(validRange('2026-11-04','2026-11-04'),true);});
test('Peruvian currency has explicit S/ prefix and two decimals',()=>assert.equal(formatMoney(1234.5),'S/ 1,234.50'));
