const paths = {
  dashboard: ['M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z'],
  content: ['M5 4h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-5 3v-3a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z', 'M7 9h10M7 13h6'],
  pages: ['M7 3h13v14H7z', 'M4 7H2v14h13v-2'],
  design: ['M4 5h16M4 12h16M4 19h16', 'M8 3v4M16 10v4M10 17v4'],
  people: ['M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z', 'M17 4a4 4 0 0 1 0 7M22 21v-2a4 4 0 0 0-3-3.87'],
  settings: ['M12 3 4 7.5v9L12 21l8-4.5v-9Z', 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z'],
  page: ['M14 2H5v20h14V7Z', 'M14 2v6h5M8 12h8M8 16h6'],
  folder: ['M3 5h6l2 3h10v12H3Z'],
  link: ['M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-2 2', 'M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l2-2'],
  block: ['M3 3h18v18H3zM3 9h18M9 9v12'],
  arrow: ['M5 12h14M13 6l6 6-6 6'],
  chevron: ['m9 5 7 7-7 7'],
} as const;

export type NavigationIconName = keyof typeof paths;

export function NavigationIcon({name}:{name:NavigationIconName}) {
  return <svg className="navigation-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{paths[name].map((d,index)=><path key={index} d={d}/>)}</svg>;
}

export const managementIcons:Record<string,NavigationIconName> = {
  '콘텐츠 관리':'content',
  '페이지 관리':'pages',
  '디자인 관리':'design',
  '운영 관리':'people',
  '사이트 설정':'settings',
};
