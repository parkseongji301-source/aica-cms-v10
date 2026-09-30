// Display/validation vocabulary only. Field names and draft/publication rules do not change.
export const contentPresentation=(typeCode:string)=>typeCode==='FAQ'
  ?{heading:'FAQ 편집',title:'질문',body:'답변',editorLabel:'FAQ 답변',missingTitle:'질문을 입력하세요.'}
  :typeCode==='RESTAURANT'?{heading:'맛집 편집',title:'식당명',body:'소개',editorLabel:'식당 소개',missingTitle:'식당명을 입력하세요.'}
  :{heading:'글 편집',title:'제목',body:'본문',editorLabel:'본문',missingTitle:'제목을 입력하세요.'};
