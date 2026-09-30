// The React admin is served at /admin; screen routes inside the app are relative to this base.
export const ADMIN_BASE='/admin';
export const routePath=(pathname:string)=>pathname.replace(/^\/admin(?=\/|$)/,'').replace(/\/$/,'');
