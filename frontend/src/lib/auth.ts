import { UserManager, WebStorageStateStore, User } from 'oidc-client-ts';

const authority = import.meta.env.VITE_AUTH_AUTHORITY || 'http://localhost:8080';
const clientId = import.meta.env.VITE_AUTH_CLIENT_ID || 'lms-frontend';
const redirectUri = window.location.origin + import.meta.env.BASE_URL + 'callback';
const postLogoutRedirectUri = window.location.origin + import.meta.env.BASE_URL;

export const userManager = new UserManager({
  authority,
  client_id: clientId,
  redirect_uri: redirectUri,
  post_logout_redirect_uri: postLogoutRedirectUri,
  response_type: 'code',
  scope: 'openid profile email roles',
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  loadUserInfo: true,
  automaticSilentRenew: true,
  silent_redirect_uri: window.location.origin + import.meta.env.BASE_URL + 'silent-check-sso.html',
});

export const login = () => userManager.signinRedirect();

export const logout = () => userManager.signoutRedirect();

export const getUser = async (): Promise<User | null> => {
  return await userManager.getUser();
};

export const getToken = async (): Promise<string | null> => {
  const user = await userManager.getUser();
  if (!user) return null;
  
  if (user.expired) {
    try {
      const newUser = await userManager.signinSilent();
      return newUser?.access_token || null;
    } catch (e) {
      console.warn('Silent signin failed, user needs to re-login', e);
      return null;
    }
  }
  
  return user.access_token;
};

export const signinCallback = async (): Promise<void> => {
  try {
    await userManager.signinRedirectCallback();
  } catch (e) {
    console.error('Error during signin callback:', e);
    throw e;
  }
};
