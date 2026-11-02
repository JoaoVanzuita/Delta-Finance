package com.deltafinance.unit.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.security.ClientAddressResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** O IP de rate limit não pode ser escolhido pelo cliente via X-Forwarded-For. */
class ClientAddressResolverTest {

  private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddr);
    if (forwardedFor != null) {
      request.addHeader("X-Forwarded-For", forwardedFor);
    }
    return request;
  }

  @Test
  void ignoraPrefixoForjadoEUsaOClienteAntesDosDoisProxies() {
    ClientAddressResolver resolver = new ClientAddressResolver(2);

    assertThat(resolver.resolve(request("172.18.0.4", "1.2.3.4, 203.0.113.7, 172.18.0.3")))
        .isEqualTo("203.0.113.7");
  }

  @Test
  void prefixoForjadoDiferenteNaoMudaOEnderecoResolvido() {
    ClientAddressResolver resolver = new ClientAddressResolver(2);

    assertThat(resolver.resolve(request("172.18.0.4", "9.9.9.9, 203.0.113.7, 172.18.0.3")))
        .isEqualTo("203.0.113.7");
    assertThat(resolver.resolve(request("172.18.0.4", "198.51.100.1, 203.0.113.7, 172.18.0.3")))
        .isEqualTo("203.0.113.7");
  }

  @Test
  void comUmProxyOClienteEUltimaEntradaDoCabecalho() {
    ClientAddressResolver resolver = new ClientAddressResolver(1);

    assertThat(resolver.resolve(request("172.18.0.4", "203.0.113.7"))).isEqualTo("203.0.113.7");
  }

  @Test
  void semCabecalhoUsaORemoteAddr() {
    ClientAddressResolver resolver = new ClientAddressResolver(2);

    assertThat(resolver.resolve(request("172.18.0.4", null))).isEqualTo("172.18.0.4");
  }

  @Test
  void cadeiaCurtaDemaisUsaORemoteAddrEmVezDeConfiarNoCliente() {
    ClientAddressResolver resolver = new ClientAddressResolver(2);

    assertThat(resolver.resolve(request("172.18.0.4", "1.2.3.4"))).isEqualTo("172.18.0.4");
  }

  @Test
  void hopsZeroIgnoraOCabecalho() {
    ClientAddressResolver resolver = new ClientAddressResolver(0);

    assertThat(resolver.resolve(request("172.18.0.4", "1.2.3.4, 203.0.113.7, 172.18.0.3")))
        .isEqualTo("172.18.0.4");
  }
}
