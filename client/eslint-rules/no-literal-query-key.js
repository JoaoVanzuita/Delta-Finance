/** Evita novas chaves de cache literais fora de `lib/query-keys`. */
export default {
  meta: {
    type: 'problem',
    docs: { description: 'proíbe queryKey como array literal' },
    schema: [],
    messages: {
      literal: 'Use queryKeys.* em vez de uma chave literal em queryKey.',
    },
  },
  create(context) {
    return {
      Property(node) {
        const key = node.key
        const isQueryKey =
          (key.type === 'Identifier' && key.name === 'queryKey') ||
          (key.type === 'Literal' && key.value === 'queryKey')
        if (!isQueryKey || node.value.type !== 'ArrayExpression') {
          return
        }
        context.report({ node: node.value, messageId: 'literal' })
      },
    }
  },
}
