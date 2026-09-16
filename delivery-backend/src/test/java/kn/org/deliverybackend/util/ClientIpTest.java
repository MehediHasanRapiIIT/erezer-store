package kn.org.deliverybackend.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rate limits must key on an address the caller can't choose. */
class ClientIpTest {

    @Test
    void usesTheAddressTheProxySaw() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Real-IP", "103.10.20.30");
        assertEquals("103.10.20.30", ClientIp.of(request));
    }

    @Test
    void ignoresAForwardedForHeaderTheCallerMadeUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("103.10.20.30");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        assertEquals("103.10.20.30", ClientIp.of(request));
    }
}
