// Integration contract for a future website. This file is not a new frontend.
// Relative URLs must resolve against the API origin, not an unrelated frontend origin.
export interface PublicTerm { id: number; code: string; name: string }
export interface PublicMedia {
  id: number; name: string; alt: string; mime: string;
  width: number; height: number; byteSize: number; url: string;
}
export interface PublicPost {
  id: number; title: string; content: string; bodyHtml: string; categoryId: number | null;
  classification: { typeCode: string; typeName: string; cohorts: PublicTerm[]; topics: PublicTerm[] };
  restaurant: { address: string } | null;
  media: PublicMedia[];
  publishedAt: string; // Existing database LocalDateTime, without a time-zone offset.
}
export interface PublicPosts { items: PublicPost[]; total: number }
export interface PublicBlock {
  id: string; schemaVersion: 2;
  type: 'HERO' | 'TEXT' | 'IMAGE' | 'POSTS' | 'CTA';
  variation: 'default' | 'centered'; // centered is registered for HERO only.
  visible: true; // Hidden blocks are omitted from the public response.
  data: {
    heading: string; body: string; bodyHtml: string; image: PublicMedia | null;
    link: string; label: string;
    sourceMode: 'category' | 'query' | 'manual' | null;
    posts: PublicPosts | null;
  };
}
export interface PublicPage {
  apiVersion: 1; id: number; title: string; slug: string; publishedAt: string; blocks: PublicBlock[];
}
// Before the first site structure publication: the managed menus, flat (parentKey null, id = menu id).
// After it: PAGE/GROUP items from the published structure (id = area id) in tree order, then LINK items
// (id = menu id) at the end of the top level. Use key, not id, as the unique key within one response.
export interface PublicMenu {
  id: number; label: string; kind: 'PAGE' | 'CATEGORY' | 'LINK' | 'GROUP'; // GROUP = label without a link
  pageId: number | null; slug: string | null; categoryId: number | null;
  url: string | null; apiHref: string | null;
  key: string; parentKey: string | null; // V14
}
// GET /structure (V14). publishedAt null and items [] until the first structure publication.
export interface PublicStructureNode {
  id: number; kind: 'PAGE' | 'GROUP'; // GROUP = a group, or a page that is not public now but has public areas below
  title: string; label: string; // label = menu label, else title
  pageId: number | null; slug: string | null; apiHref: string | null;
  menuVisible: boolean; contentTypeCode: string | null;
  children: PublicStructureNode[];
}
export interface PublicStructure { apiVersion: 1; publishedAt: string | null; items: PublicStructureNode[] }
export interface PublicError { code: 'NOT_FOUND' | 'INVALID_REQUEST' | 'PUBLICATION_UNAVAILABLE' | 'READ_ONLY' }
