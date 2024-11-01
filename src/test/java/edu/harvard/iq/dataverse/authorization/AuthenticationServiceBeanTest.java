package edu.harvard.iq.dataverse.authorization;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import edu.harvard.iq.dataverse.authorization.exceptions.AuthorizationException;
import edu.harvard.iq.dataverse.authorization.providers.oauth2.oidc.OIDCAuthProvider;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.util.BundleUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticationServiceBeanTest {

    private AuthenticationServiceBean sut;
    private static final String TEST_BEARER_TOKEN = "Bearer test";

    @BeforeEach
    public void setUp() {
        sut = new AuthenticationServiceBean();
        sut.authProvidersRegistrationService = Mockito.mock(AuthenticationProvidersRegistrationServiceBean.class);
        sut.em = Mockito.mock(EntityManager.class);
    }

    @Test
    void testLookupUserByOidcBearerToken_no_OidcProvider() {
        // Given no OIDC providers are configured
        Mockito.when(sut.authProvidersRegistrationService.getAuthenticationProvidersMap()).thenReturn(Map.of());

        // When invoking lookupUserByOidcBearerToken
        AuthorizationException exception = assertThrows(AuthorizationException.class,
                () -> sut.lookupUserByOidcBearerToken(TEST_BEARER_TOKEN));

        // Then the exception message should indicate no OIDC provider is configured
        assertEquals(BundleUtil.getStringFromBundle("authenticationServiceBean.errors.bearerTokenDetectedNoOIDCProviderConfigured"), exception.getMessage());
    }

    @Test
    void testLookupUserByOidcBearerToken_oneProvider_invalidToken_1() throws ParseException, IOException {
        // Given a single OIDC provider that cannot find a user
        OIDCAuthProvider oidcAuthProvider = mockOidcAuthProvider("OIEDC");
        BearerAccessToken token = BearerAccessToken.parse(TEST_BEARER_TOKEN);
        Mockito.when(oidcAuthProvider.getUserIdentifier(token)).thenReturn(Optional.empty());

        // When invoking lookupUserByOidcBearerToken
        AuthorizationException exception = assertThrows(AuthorizationException.class,
                () -> sut.lookupUserByOidcBearerToken(TEST_BEARER_TOKEN));

        // Then the exception message should indicate an unauthorized token
        assertEquals(BundleUtil.getStringFromBundle("authenticationServiceBean.errors.unauthorizedBearerToken"), exception.getMessage());
    }

    @Test
    void testLookupUserByOidcBearerToken_oneProvider_invalidToken_2() throws ParseException, IOException {
        // Given a single OIDC provider that throws an IOException
        OIDCAuthProvider oidcAuthProvider = mockOidcAuthProvider("OIEDC");
        BearerAccessToken token = BearerAccessToken.parse(TEST_BEARER_TOKEN);
        Mockito.when(oidcAuthProvider.getUserIdentifier(token)).thenThrow(IOException.class);

        // When invoking lookupUserByOidcBearerToken
        AuthorizationException exception = assertThrows(AuthorizationException.class,
                () -> sut.lookupUserByOidcBearerToken(TEST_BEARER_TOKEN));

        // Then the exception message should indicate an unauthorized token
        assertEquals(BundleUtil.getStringFromBundle("authenticationServiceBean.errors.unauthorizedBearerToken"), exception.getMessage());
    }

    @Test
    void testLookupUserByOidcBearerToken_oneProvider_validToken() throws ParseException, IOException, AuthorizationException {
        // Given a single OIDC provider that returns a valid user identifier
        OIDCAuthProvider oidcAuthProvider = mockOidcAuthProvider("OIEDC");
        AuthenticatedUser authenticatedUser = setupAuthenticatedUserQueryWithResult(new AuthenticatedUser());
        UserRecordIdentifier userInfo = new UserRecordIdentifier("OIEDC", "KEY");
        BearerAccessToken token = BearerAccessToken.parse(TEST_BEARER_TOKEN);
        Mockito.when(oidcAuthProvider.getUserIdentifier(token)).thenReturn(Optional.of(userInfo));

        // When invoking lookupUserByOidcBearerToken
        User actualUser = sut.lookupUserByOidcBearerToken(TEST_BEARER_TOKEN);

        // Then the actual user should match the expected authenticated user
        assertEquals(authenticatedUser, actualUser);
    }

    @Test
    void testLookupUserByOidcBearerToken_oneProvider_validToken_noAccount() throws ParseException, IOException, AuthorizationException {
        // Given a single OIDC provider with a valid user identifier but no account exists
        OIDCAuthProvider oidcAuthProvider = mockOidcAuthProvider("OIEDC");
        setupAuthenticatedUserQueryWithNoResult();
        UserRecordIdentifier userInfo = new UserRecordIdentifier("OIEDC", "KEY");
        BearerAccessToken token = BearerAccessToken.parse(TEST_BEARER_TOKEN);
        Mockito.when(oidcAuthProvider.getUserIdentifier(token)).thenReturn(Optional.of(userInfo));

        // When invoking lookupUserByOidcBearerToken
        User actualUser = sut.lookupUserByOidcBearerToken(TEST_BEARER_TOKEN);

        // Then no user should be found, and result should be null
        assertNull(actualUser);
    }

    private OIDCAuthProvider mockOidcAuthProvider(String providerID) {
        OIDCAuthProvider oidcAuthProvider = Mockito.mock(OIDCAuthProvider.class);
        Mockito.when(oidcAuthProvider.getId()).thenReturn(providerID);
        Mockito.when(sut.authProvidersRegistrationService.getAuthenticationProvidersMap()).thenReturn(Map.of(providerID, oidcAuthProvider));
        return oidcAuthProvider;
    }

    private AuthenticatedUser setupAuthenticatedUserQueryWithResult(AuthenticatedUser authenticatedUser) {
        TypedQuery<AuthenticatedUserLookup> queryMock = Mockito.mock(TypedQuery.class);
        AuthenticatedUserLookup lookupMock = Mockito.mock(AuthenticatedUserLookup.class);
        Mockito.when(lookupMock.getAuthenticatedUser()).thenReturn(authenticatedUser);
        Mockito.when(queryMock.getSingleResult()).thenReturn(lookupMock);
        Mockito.when(sut.em.createNamedQuery("AuthenticatedUserLookup.findByAuthPrvID_PersUserId", AuthenticatedUserLookup.class)).thenReturn(queryMock);
        return authenticatedUser;
    }

    private void setupAuthenticatedUserQueryWithNoResult() {
        TypedQuery<AuthenticatedUserLookup> queryMock = Mockito.mock(TypedQuery.class);
        Mockito.when(queryMock.getSingleResult()).thenThrow(new NoResultException());
        Mockito.when(sut.em.createNamedQuery("AuthenticatedUserLookup.findByAuthPrvID_PersUserId", AuthenticatedUserLookup.class)).thenReturn(queryMock);
    }
}
