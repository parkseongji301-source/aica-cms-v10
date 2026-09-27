import type {ClassificationCatalog,Go} from './types';
import {ContentTree} from './ContentTree';
export function ReviewTree(props:{catalog:ClassificationCatalog|null;selected:string|null;go:Go;error:string}) {
  return <ContentTree type="REVIEW" {...props}/>;
}
