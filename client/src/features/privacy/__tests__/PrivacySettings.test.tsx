import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { PrivacySettingsPage } from '@/features/privacy/pages/PrivacySettingsPage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

const INTEGRATION = {
  id: 'integration-1',
  provider: 'MEU_PLUGGY',
  clientIdMasked: 'clie…ente',
  status: 'ACTIVE',
  lastSyncAttemptAt: '2026-10-23T12:00:00Z',
  lastSuccessfulSyncAt: '2026-10-23T12:00:00Z',
  connectionCount: 1,
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi() {
  const mock = vi.fn((url: string, init: RequestInit) => {
    if (url.includes('/auth/csrf')) return new Response(null, { status: 204 })
    if (url.includes('/integrations/pluggy') && init.method === 'DELETE') {
      return new Response(null, { status: 204 })
    }
    if (url.includes('/integrations/pluggy')) return jsonResponse(INTEGRATION)
    if (url.includes('/me/export')) {
      return new Response(new Blob(['zip']), {
        status: 200,
        headers: { 'content-type': 'application/zip' },
      })
    }
    if (url.includes('/me/delete') && init.method === 'POST') {
      return new Response(null, { status: 202 })
    }
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/settings/privacy" element={<PrivacySettingsPage />} />
      <Route path="/login" element={<div>Tela de login</div>} />
    </Routes>,
    { route: '/settings/privacy' },
  )
}

describe('configurações de privacidade', () => {
  let createObjectUrl: ReturnType<typeof vi.fn>
  let revokeObjectUrl: ReturnType<typeof vi.fn>
  let anchorClick: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
    createObjectUrl = vi.fn(() => 'blob:export')
    revokeObjectUrl = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', {
      value: createObjectUrl,
      configurable: true,
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: revokeObjectUrl,
      configurable: true,
    })
    anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    delete (URL as unknown as Record<string, unknown>).createObjectURL
    delete (URL as unknown as Record<string, unknown>).revokeObjectURL
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('baixa a exportação e informa o início do download', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { name: 'Exportar meus dados' })
    await user.click(screen.getByRole('button', { name: 'Baixar exportação' }))

    expect(
      await screen.findByText(/Exportação gerada. O download deve iniciar automaticamente./),
    ).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/me/export'))).toBe(true)
    expect(createObjectUrl).toHaveBeenCalled()
    expect(anchorClick).toHaveBeenCalled()
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:export')
  })

  it('desconecta a integração com exclusão explícita dos dados importados', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByText(/Estado atual:/)
    await user.click(screen.getByLabelText(/Excluir também os dados importados/))
    await user.click(screen.getByRole('button', { name: 'Desconectar integração' }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirmar desconexão' }))

    expect(
      await screen.findByText(/Integração desconectada. Novas sincronizações foram interrompidas/),
    ).toBeInTheDocument()
    await waitFor(() => {
      expect(
        fetchMock.mock.calls.some(
          ([url, init]) =>
            String(url).includes('/integrations/pluggy') &&
            String(url).includes('deleteImportedData=true') &&
            init.method === 'DELETE',
        ),
      ).toBe(true)
    })
  })

  it('exige senha, EXCLUIR e ciência da política antes de excluir a conta', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { name: 'Excluir minha conta' })
    await user.click(screen.getByRole('button', { name: 'Excluir minha conta' }))
    await user.click(screen.getByRole('button', { name: 'Excluir definitivamente' }))
    expect(await screen.findByText(/Informe sua senha para confirmar/)).toBeInTheDocument()

    await user.type(screen.getByLabelText('Senha atual'), 'minha senha longa')
    await user.type(screen.getByLabelText('Digite EXCLUIR para confirmar'), 'APAGAR')
    await user.click(screen.getByLabelText(/Entendi que os dados ativos serão removidos/))
    await user.click(screen.getByRole('button', { name: 'Excluir definitivamente' }))
    expect(
      await screen.findByText(/Digite EXCLUIR \(em maiúsculas\) para confirmar/),
    ).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/me/delete'))).toBe(false)

    await user.clear(screen.getByLabelText('Digite EXCLUIR para confirmar'))
    await user.type(screen.getByLabelText('Digite EXCLUIR para confirmar'), 'EXCLUIR')
    await user.click(screen.getByRole('button', { name: 'Excluir definitivamente' }))

    expect(await screen.findByText('Tela de login')).toBeInTheDocument()
    await waitFor(() => {
      const call = fetchMock.mock.calls.find(
        ([url, init]) => String(url).includes('/me/delete') && init.method === 'POST',
      )
      expect(call).toBeDefined()
      expect(JSON.parse(String(call?.[1].body))).toEqual({
        password: 'minha senha longa',
        confirmation: 'EXCLUIR',
      })
    })
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    stubApi()
    renderPage()

    await screen.findByRole('heading', { name: 'Exportar meus dados' })
    await expectNoA11yViolations()
  })
})
