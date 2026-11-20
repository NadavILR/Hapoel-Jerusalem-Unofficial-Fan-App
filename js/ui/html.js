/* React + htm (בלי שלב בנייה): class במקום className */
export const { useState, useEffect, useLayoutEffect, useRef, useMemo, useCallback, useContext, createContext, memo, Fragment } = React;
export const html = htm.bind((t, p, ...c) => React.createElement(t, p && p.class ? { ...p, className: p.class, class: undefined } : p, ...c));
export const portal = (node, el) => ReactDOM.createPortal(node, el || document.body);
export const cx = (...a) => a.filter(Boolean).join(' ');
