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
export interface PublicMenu {
  id: number; label: string; kind: 'PAGE' | 'CATEGORY' | 'LINK';
  pageId: number | null; slug: string | null; categoryId: number | null;
  url: string | null; apiHref: string | null;
}
export interface PublicError { code: 'NOT_FOUND' | 'INVALID_REQUEST' | 'PUBLICATION_UNAVAILABLE' | 'READ_ONLY' }
