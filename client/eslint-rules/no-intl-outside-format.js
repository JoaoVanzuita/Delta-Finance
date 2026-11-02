/** Centraliza `Intl` em `src/lib/format`. */
export default {
  meta: {
    type: 'problem',
    docs: { description: 'proíbe Intl fora de lib/format' },
    schema: [],
    messages: {
      intl: 'Formate datas/números/moeda via @/lib/format.',
    },
  },
  create(context) {
    const filename = context.filename.replaceAll('\\', '/')
    if (filename.includes('/src/lib/format/')) {
      return {}
    }
    return {
      MemberExpression(node) {
        if (node.object.type === 'Identifier' && node.object.name === 'Intl') {
          context.report({ node, messageId: 'intl' })
        }
      },
    }
  },
}
