import type {PostDocument} from './types';

export function restaurantProblem(doc:Pick<PostDocument,'classification'|'restaurant'>):string {
  const address=doc.restaurant?.address||'';
  if(doc.classification.typeCode!=='RESTAURANT'&&address.trim())return '맛집 주소가 남아 있습니다. 다른 유형으로 저장하려면 주소를 비우거나 맛집 유형으로 되돌려 주세요.';
  if(address.trim().length>500)return '주소는 500자 이하로 입력하세요.';
  return '';
}
// Omission preserves old clients' data. An explicit empty address clears it.
export const restaurantPayload=(doc:Pick<PostDocument,'classification'|'restaurant'>)=>
  doc.classification.typeCode==='RESTAURANT'?{restaurant:{address:doc.restaurant?.address||''}}:
  doc.restaurant?{restaurant:doc.restaurant}:{};
