import type {ClassificationCatalog} from './types';
import {contentContext,contentSections,sectionPath} from './contentNavigation.ts';
export {contextualPostPath,filterValues,scopedPostParams,draftSelection as reviewDraftSelection} from './contentNavigation.ts';
export type {ContentContext as ReviewContext} from './contentNavigation.ts';
export const reviewNodes=contentSections.REVIEW.nodes;
export const reviewContext=(search:URLSearchParams,catalog:ClassificationCatalog|null)=>search.has('reviewSection')?contentContext(search,catalog):null;
export const reviewPath=(key:string,catalog:ClassificationCatalog|null)=>sectionPath('REVIEW',key,catalog);
