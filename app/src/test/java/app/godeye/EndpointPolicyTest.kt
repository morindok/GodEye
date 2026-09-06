package app.godeye
import org.junit.Assert.*
import org.junit.Test
class EndpointPolicyTest {
    @Test fun acceptsHttpsEndpoint() { assertNull(EndpointPolicy.validate("https://example.com/v1/chat/completions")) }
    @Test fun rejectsHttp() { assertNotNull(EndpointPolicy.validate("http://example.com/v1/chat/completions")) }
    @Test fun acceptsHttpLoopback() { assertNull(EndpointPolicy.validate("http://127.0.0.1:8122/v1/chat/completions")) }
    @Test fun acceptsHttpLocalhost() { assertNull(EndpointPolicy.validate("http://localhost:8211/v1/chat/completions")) }
    @Test fun rejectsCredentialsInUrl() { assertNotNull(EndpointPolicy.validate("https://user:secret@example.com/api")) }
    @Test fun rejectsQuerySecrets() { assertNotNull(EndpointPolicy.validate("https://example.com/api?key=secret")) }
    @Test fun rejectsFragments() { assertNotNull(EndpointPolicy.validate("https://example.com/api#fragment")) }
    @Test fun rejectsBareHost() { assertNotNull(EndpointPolicy.validate("https://example.com")) }
    @Test fun rejectsMalformedUrl() { assertNotNull(EndpointPolicy.validate("not a URL")) }
    @Test fun acceptsCustomHttpsPort() { assertNull(EndpointPolicy.validate("https://example.com:8443/v1/chat/completions")) }
}
