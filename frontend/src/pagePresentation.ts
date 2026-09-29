// Display vocabulary only; backend component definitions remain authoritative.
export const blockLabel=(type:string)=>({HERO:'대표 문구',TEXT:'본문',IMAGE:'이미지',POSTS:'글 목록',CTA:'안내 버튼'}[type]||type);
export const variationLabel=(value:string)=>({default:'기본형',centered:'가운데 강조형'}[value]||value);
