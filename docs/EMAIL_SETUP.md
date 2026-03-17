# Email receipt setup

Order receipts are sent from the **Company email** (configured in the app under **Settings**). You can configure SMTP either in the app (recommended) or via environment variables.

## Option 1: Configure in the app (recommended)

1. In the POS app go to **Settings**.
2. Set **Company email** (in “Company details”) to the address that should appear as “From” on receipts.
3. In the **Email (receipts)** card:
   - Choose **Method**: **SMTP** or **Microsoft sign-in**.
   - If you choose **Microsoft sign-in**:
     - Click **Connect Microsoft** (login popup).
     - Save, then click **Verify setup**.
     - Requires Azure app registration (see below).
   - If you choose **SMTP**: choose **Provider**: Gmail, Microsoft Outlook, or Custom SMTP.
   - For **Gmail** or **Outlook**: host and port are pre-filled; enter your email and **App password** (if you use 2-step verification / 2FA, create an App Password in your Google or Microsoft account and use that instead of your normal password).
   - For **Custom**: enter SMTP host, port, email, and password.
4. Click **Save**, then **Verify setup**. A test email is sent to your company email; when it succeeds, a green tick and “Email is set up” are shown.

**Server requirement:** To store the email password in the app, set the environment variable `SMTP_ENCRYPTION_KEY` (base64-encoded 16-byte key) so the API can encrypt it. Generate a key with: `openssl rand -base64 16`.

### Microsoft sign-in (Azure app registration)

The flow uses a **backend callback**: Microsoft redirects to the API; the API exchanges the code and then redirects the browser to a small frontend “Connected” page that closes the popup.

1. In Azure Portal → **Microsoft Entra ID** → **App registrations** → **New registration**.
2. Supported account types:
   - For a single organisation: **Single tenant**
   - For any Microsoft account: **Multitenant + personal** (optional)
3. Add a **Redirect URI** (type: **Web**) pointing to your **API** callback URL, e.g.:
   - `https://<your-api-domain>/api/company/microsoft/callback`
   - Local: `http://localhost:8080/api/company/microsoft/callback`
4. In the app registration:
   - **API permissions** → add **Microsoft Graph** delegated permissions:
     - `Mail.Send`
     - `User.Read`
     - `offline_access`
   - **Grant admin consent** if required in your tenant.
5. **Certificates & secrets** → create a **Client secret**.
6. Set these environment variables on the API server:
   - `MS_OAUTH_CLIENT_ID`
   - `MS_OAUTH_CLIENT_SECRET`
   - `MS_OAUTH_TENANT` (or `common`)
   - `MS_OAUTH_REDIRECT_URI` — must exactly match the **backend** Redirect URI (e.g. `https://<api>/api/company/microsoft/callback`)
   - `MS_OAUTH_FRONTEND_SUCCESS_URL` — URL of the frontend “success” page (e.g. `https://<your-ui-domain>/auth/microsoft-callback`). The API redirects the user here after a successful connect so the popup shows “Connected — no need to verify again” and closes.
   - `SMTP_ENCRYPTION_KEY` (used to encrypt the Microsoft refresh token in DB)

## Option 2: Configure via environment variables

Use one of the options below. Credentials are read from **environment variables** (never put passwords in config files).

### A: Microsoft Outlook / Office 365

Use your Outlook or Office 365 account (e.g. `imcsingh@outlook.com`). If you use two-factor authentication, create an [App password](https://support.microsoft.com/en-us/account-billing/using-app-passwords-with-apps-that-don-t-support-two-step-verification-5896ed9b-4263-e681-128a-a6f2979a7944) and use that instead of your normal password.

1. Set Company email in the app (Settings) to `imcsingh@outlook.com` (or your Outlook address).
2. Set these environment variables before starting the API:

   ```bash
   export MAIL_HOST=smtp.office365.com
   export MAIL_PORT=587
   export MAIL_USERNAME=imcsingh@outlook.com
   export MAIL_PASSWORD=your-password-or-app-password
   ```

3. Start the API. “Email receipt” will send from your Outlook address.

### B: Proton Mail (Bridge) – local testing

Proton Mail does not provide direct SMTP for servers. Use **Proton Mail Bridge** on your machine so the API can send via localhost.

1. Install [Proton Mail Bridge](https://proton.me/mail/bridge) and sign in with your Proton account.
2. In Bridge, enable **SMTP** (default port is usually **1025**).
3. Set these in your environment before starting the API (e.g. in your shell or in Run Configuration):

   ```bash
   export MAIL_USERNAME=your-proton@proton.me
   export MAIL_PASSWORD=your-bridge-or-account-password
   ```

   Leave `MAIL_HOST` and `MAIL_PORT` unset when using the **dev** profile; the app defaults to `127.0.0.1:1025` for Bridge.

4. Run the API with the **dev** profile (default when `SPRING_PROFILES_ACTIVE` is not set). Receipts will be sent through Bridge from your Company email address.

### C: Gmail (app password)

1. Use a Gmail account and create an [App Password](https://support.google.com/accounts/answer/185833).
2. Set Company email in the app to that Gmail address.
3. Set environment variables:

   ```bash
   export MAIL_HOST=smtp.gmail.com
   export MAIL_PORT=587
   export MAIL_USERNAME=your@gmail.com
   export MAIL_PASSWORD=your-16-char-app-password
   ```

### D: Azure Communication Services / SendGrid / other

Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, and `MAIL_PASSWORD` to your provider’s SMTP values. Set Company email in the app to the “From” address allowed by that provider.

---

**Errors you may see**

- **EM001** – Company email is not set in Settings. Set it and try again.
- **EM002** – SMTP is not configured or sending failed. Check `MAIL_*` env vars and that Bridge (or your SMTP server) is running.
- **OR005** – The order’s customer has no email address. Add an email to the customer or choose an order with a customer that has an email.
