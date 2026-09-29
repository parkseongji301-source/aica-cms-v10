import type {ClassificationCatalog,ClassificationSelection} from './types';

// A list query may match multiple types; a new draft always has exactly one.
export function listDraftSelection(typeCodes:string[],catalog:ClassificationCatalog):ClassificationSelection {
  const codes=[...new Set(typeCodes)];
  const typeCode=codes.length===1&&catalog.types.some(t=>t.code===codes[0]&&t.active)?codes[0]:'';
  return {typeCode,cohortIds:[],topicIds:[]};
}
