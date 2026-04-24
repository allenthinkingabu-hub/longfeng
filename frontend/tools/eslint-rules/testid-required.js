/**
 * S7 · testid-required · ESLint 规则（主文档 §11.7 Step 8）
 * 可点击 JSX 元素必须带 data-testid · 违者 DoD-S7-03 红
 *
 * 覆盖：button / a / Button / Link / IconButton + onClick 绑定
 * 豁免：无 onClick + 非点击角色
 */
module.exports = {
  meta: {
    type: 'problem',
    docs: { description: 'require data-testid on clickable elements' },
    schema: [],
  },
  create(ctx) {
    const CLICKABLE = new Set(['button', 'a', 'Button', 'Link', 'IconButton']);
    return {
      JSXOpeningElement(node) {
        const name = node.name && node.name.name;
        if (!CLICKABLE.has(name)) return;
        const hasOnClick = node.attributes.some((a) => a.type === 'JSXAttribute' && a.name && a.name.name === 'onClick');
        if (!hasOnClick) return;
        const hasTestid = node.attributes.some((a) => {
          if (a.type !== 'JSXAttribute' || !a.name) return false;
          const n = a.name.name;
          if (n === 'data-testid') return true;
          // JSXNamespacedName: data-testid shorthand
          if (a.name.type === 'JSXNamespacedName' && a.name.namespace?.name === 'data' && a.name.name?.name === 'testid') return true;
          return false;
        });
        // 允许通过 testIdPrefix prop 间接传入（ui-kit 组件模式）
        const hasTestIdPrefix = node.attributes.some((a) => a.type === 'JSXAttribute' && a.name && a.name.name === 'testIdPrefix');
        if (!hasTestid && !hasTestIdPrefix) {
          ctx.report({ node, message: `data-testid required on clickable <${name}>` });
        }
      },
    };
  },
};
