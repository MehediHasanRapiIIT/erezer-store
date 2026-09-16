package kn.org.deliverybackend.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Money for customer emails: "৳1,260.00". Emails are UTF-8, so the taka sign
 * shows; SMS and the invoice PDF deliberately write "Tk" instead (plain-text SMS
 * length, and the PDF font has no taka glyph).
 */
public final class Taka {

    private Taka() {
    }

    public static String format(BigDecimal amount) {
        DecimalFormat digits = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "\u09F3" + digits.format(amount == null ? BigDecimal.ZERO : amount);
    }
}
