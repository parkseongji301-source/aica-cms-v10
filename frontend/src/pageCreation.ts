/** Use the existing page form API with an explicit, unpublished empty draft. */
export function pageDraftForm(title:string,slug:string) {
  return new URLSearchParams({title:title.trim(),slug:slug.trim().toLowerCase(),sectionsJson:'[]',action:'save',saveIntent:'MANUAL_DRAFT'});
}
export type CreatedPage = {id:number;revision:number;slug:string;status:string;pending:boolean};
