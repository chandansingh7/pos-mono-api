package com.pos.exception;

/**
 * Centralised application error codes.
 *
 * Format: 2-letter category prefix + 3-digit sequence = 5 characters (e.g. AU001).
 *
 * Categories:
 *   AU – Authentication / Authorisation
 *   US – User management
 *   PR – Product
 *   CT – Category
 *   CM – Customer
 *   OR – Order
 *   IN – Inventory
 *   SH – Shift / cashier session
 *   VA – Validation
 *   SV – Server / unexpected
 */
public enum ErrorCode {

    // ── Authentication / Authorisation ────────────────────────────────────────
    AU001("AU001", "Invalid username or password"),
    AU002("AU002", "Account is disabled"),
    AU003("AU003", "Session expired, please log in again"),
    AU004("AU004", "Access denied: you do not have permission to perform this action"),
    AU005("AU005", "Current password is incorrect"),
    AU006("AU006", "New password and confirmation do not match"),
    AU007("AU007", "New password must be different from the current password"),
    AU008("AU008", "Access denied: login from this IP is not allowed. Contact an administrator to whitelist your IP."),
    AU009("AU009", "You cannot block the IP you are currently using. Use another account or IP to block it."),

    // ── User management ───────────────────────────────────────────────────────
    US001("US001", "User not found"),
    US002("US002", "Username is already taken"),
    US003("US003", "Email address is already registered"),
    US004("US004", "You cannot deactivate your own account"),

    // ── Product ───────────────────────────────────────────────────────────────
    PR001("PR001", "Product not found"),
    PR002("PR002", "SKU already exists"),
    PR003("PR003", "Barcode already exists"),
    PR004("PR004", "Product is not available for sale"),
    PR005("PR005", "Image file is required"),
    PR006("PR006", "File must be an image (JPEG, PNG, GIF or WebP)"),

    // ── Category ──────────────────────────────────────────────────────────────
    CT001("CT001", "Category not found"),
    CT002("CT002", "Category name already exists"),

    // ── Customer ──────────────────────────────────────────────────────────────
    CM001("CM001", "Customer not found"),
    CM002("CM002", "Email address is already registered for another customer"),

    // ── Order ─────────────────────────────────────────────────────────────────
    OR001("OR001", "Order not found"),
    OR002("OR002", "Insufficient stock"),
    OR003("OR003", "Order is already cancelled"),
    OR004("OR004", "Cannot cancel a refunded order"),
    OR005("OR005", "Customer has no email address; cannot send receipt"),
    OR006("OR006", "Order is already refunded"),
    OR007("OR007", "Only completed orders can be refunded"),
    OR008("OR008", "Invalid partial refund: order item not found or quantity exceeds original"),

    // ── Email (receipt) ───────────────────────────────────────────────────────
    EM001("EM001", "Company email is not set in Settings; cannot send receipt"),
    EM002("EM002", "Email (SMTP) is not configured; contact administrator"),
    EM003("EM003", "Email verification failed; check SMTP settings and app password (use App Password if you have 2FA)"),
    EM004("EM004", "Failed to send email via Microsoft; check Microsoft account permissions or try again later"),
    EM005("EM005", "Microsoft sign-in account cannot send mail via Graph API (personal Outlook accounts are not supported). Use SMTP instead — go to Settings → Email → choose Outlook / Hotmail (personal) and enter your password."),
    EM006("EM006", "SMTP authentication failed — wrong email or password. If 2FA is ON, you must use an App Password (not your regular password). For Gmail: myaccount.google.com/apppasswords. For Microsoft: account.microsoft.com/security."),
    EM007("EM007", "Microsoft has disabled basic authentication for personal Outlook/Hotmail accounts (error 535 5.7.139). SMTP with a password is no longer supported for @outlook.com/@hotmail.com. Use Gmail SMTP instead: create a Gmail account, enable 2FA, generate an App Password at myaccount.google.com/apppasswords, then select Gmail as your provider in Settings → Email."),
    EM008("EM008", "Gmail requires an App Password, not your regular password (error 534 5.7.9). Go to myaccount.google.com/apppasswords, generate an App Password for CicdPOS, and enter the 16-character code (no spaces) as the password in Settings → Email."),

    // ── Inventory ─────────────────────────────────────────────────────────────
    IN001("IN001", "Inventory record not found for this product"),

    // ── Shifts / cashier sessions ─────────────────────────────────────────────
    SH001("SH001", "Cash drawer difference exceeds allowed tolerance"),
    SH002("SH002", "Shift cannot be closed yet (minimum open time not reached)"),
    SH003("SH003", "Shift has been open longer than the allowed maximum duration"),
    SH004("SH004", "Shift cannot be closed because it was opened on a previous day; ask an administrator to review"),

    // ── Label ─────────────────────────────────────────────────────────────────
    LB001("LB001", "Label not found"),
    LB002("LB002", "Barcode already exists for a label or product"),

    // ── Member rewards ───────────────────────────────────────────────────────
    RW001("RW001", "Insufficient reward points for redemption"),

    // ── Validation ────────────────────────────────────────────────────────────
    VA001("VA001", "One or more fields failed validation"),

    // ── Tax rules ─────────────────────────────────────────────────────────────
    TX001("TX001", "Tax rule not found"),
    TX002("TX002", "Tax category already exists"),
    TX003("TX003", "Cannot delete a tax rule that is assigned to one or more products"),

    // ── Backup / Restore ────────────────────────────────────────────────────
    BR001("BR001", "Backup or restore failed"),

    // ── Server / unexpected ───────────────────────────────────────────────────
    SV001("SV001", "An unexpected server error occurred, please try again"),
    SV002("SV002", "Failed to store image");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code    = code;
        this.message = message;
    }

    public String getCode()    { return code; }
    public String getMessage() { return message; }

    @Override
    public String toString() { return code + ": " + message; }
}
