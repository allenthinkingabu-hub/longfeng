// CSS Modules ambient · 让 import s from './X.module.css' typecheck 通过
declare module '*.module.css' {
  const classes: { readonly [key: string]: string };
  export default classes;
}
