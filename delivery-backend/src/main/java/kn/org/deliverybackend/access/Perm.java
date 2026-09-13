package kn.org.deliverybackend.access;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every permission a moderator can be given, one per action in the admin
 * panel. This list is the single source of truth: it is written into the
 * {@code permission} table at startup, shown as the checklist on the Staff
 * page, and referenced by {@link RequiresPermission} on each admin endpoint.
 *
 * <p>Keys are stable identifiers stored in the database. Never rename one;
 * add a new constant and stop using the old one instead.
 *
 * <p>Adding an admin feature? The steps (permission, endpoint marker, admin
 * panel, checklist dependencies, verify script) are in TESTING.md, section 9.
 */
public enum Perm {

    FINANCE_REVENUE("finance.revenue", "Finance", "See money totals",
            "Revenue on the dashboard, analytics, customer lifetime value, and every report."),

    DASHBOARD_VIEW("dashboard.view", "Dashboard", "See the dashboard", null),
    ANALYTICS_VIEW("analytics.view", "Analytics", "See analytics", null),
    REPORTS_VIEW("reports.view", "Reports", "See business reports", "Also needs \"See money totals\"."),
    REPORTS_EXPORT("reports.export", "Reports", "Export and print reports", null),

    ORDERS_VIEW("orders.view", "Orders", "See orders", null),
    ORDERS_STATUS("orders.status", "Orders", "Change order status", "Every status except Cancelled."),
    ORDERS_CANCEL("orders.cancel", "Orders", "Cancel orders", null),
    ORDERS_INVOICE_PRINT("orders.invoice.print", "Orders", "Download invoices", null),
    ORDERS_INVOICE_SEND("orders.invoice.send", "Orders", "Email invoices to customers", null),
    ORDERS_NOTES_VIEW("orders.notes.view", "Orders", "Read order notes", null),
    ORDERS_NOTES_ADD("orders.notes.add", "Orders", "Add order notes", null),
    ORDERS_NOTES_DELETE("orders.notes.delete", "Orders", "Delete order notes", null),

    RETURNS_VIEW("returns.view", "Returns", "See returns", null),
    RETURNS_DECIDE("returns.decide", "Returns", "Approve or reject returns", null),
    RETURNS_PICKUP("returns.pickup", "Returns", "Mark returns picked up", null),
    RETURNS_REFUND("returns.refund", "Returns", "Refund returns", null),

    CUSTOM_ORDERS_VIEW("custom_orders.view", "Custom orders", "See custom orders", null),
    CUSTOM_ORDERS_UPDATE("custom_orders.update", "Custom orders", "Update custom orders", null),
    CUSTOM_ORDERS_DELETE("custom_orders.delete", "Custom orders", "Delete custom orders", null),

    CUSTOMERS_VIEW("customers.view", "Customers", "See customers", null),

    SUPPORT_VIEW("support.view", "Support", "Read support messages", null),
    SUPPORT_UPDATE("support.update", "Support", "Mark support messages handled", null),
    SUPPORT_DELETE("support.delete", "Support", "Delete support messages", null),

    PRODUCTS_VIEW("products.view", "Products", "See products", null),
    PRODUCTS_CREATE("products.create", "Products", "Add products",
            "Also needs \"Change prices\", because every new product needs a price."),
    PRODUCTS_EDIT("products.edit", "Products", "Edit products", null),
    PRODUCTS_PRICE("products.price", "Products", "Change prices", "Price and sale discount, when adding or editing."),
    PRODUCTS_DELETE("products.delete", "Products", "Delete products", null),
    PRODUCTS_FEATURE("products.feature", "Products", "Feature products on the home page", null),
    PRODUCTS_IMAGES("products.images", "Products", "Manage product photos", null),
    PRODUCTS_VARIANTS("products.variants", "Products", "Manage sizes and colours", null),

    INVENTORY_VIEW("inventory.view", "Inventory", "See stock", null),
    INVENTORY_EDIT("inventory.edit", "Inventory", "Change stock", null),

    CATEGORIES_VIEW("categories.view", "Categories", "See categories", null),
    CATEGORIES_CREATE("categories.create", "Categories", "Add categories", null),
    CATEGORIES_EDIT("categories.edit", "Categories", "Edit categories", null),
    CATEGORIES_DELETE("categories.delete", "Categories", "Delete categories", null),

    REVIEWS_VIEW("reviews.view", "Reviews", "See reviews", null),
    REVIEWS_DELETE("reviews.delete", "Reviews", "Delete reviews", null),

    BANNERS_VIEW("banners.view", "Banners", "See banners", null),
    BANNERS_CREATE("banners.create", "Banners", "Add banners", null),
    BANNERS_EDIT("banners.edit", "Banners", "Edit banners", null),
    BANNERS_DELETE("banners.delete", "Banners", "Delete banners", null),

    COUPONS_VIEW("coupons.view", "Coupons", "See coupons", null),
    COUPONS_CREATE("coupons.create", "Coupons", "Add coupons", null),
    COUPONS_EDIT("coupons.edit", "Coupons", "Edit coupons", null),
    COUPONS_DELETE("coupons.delete", "Coupons", "Delete coupons", null),
    COUPONS_SWITCH("coupons.switch", "Coupons", "Turn promo codes on or off",
            "Off hides the promo code box in the shop and stops every code from applying."),

    DISCOUNTS_VIEW("discounts.view", "Discounts", "See discount rules", null),
    DISCOUNTS_CREATE("discounts.create", "Discounts", "Add discount rules", null),
    DISCOUNTS_EDIT("discounts.edit", "Discounts", "Edit discount rules", null),
    DISCOUNTS_DELETE("discounts.delete", "Discounts", "Delete discount rules", null),
    DISCOUNTS_SWITCHES("discounts.switches", "Discounts", "Turn discounts on or off",
            "The master switch, the store-wide, category and product switches, "
                    + "and \"Never discount\" on products and categories."),

    FLASH_SALES_VIEW("flash_sales.view", "Flash sales", "See flash sales", null),
    FLASH_SALES_CREATE("flash_sales.create", "Flash sales", "Add flash sales", null),
    FLASH_SALES_EDIT("flash_sales.edit", "Flash sales", "Edit flash sales", null),
    FLASH_SALES_DELETE("flash_sales.delete", "Flash sales", "Delete flash sales", null),

    BUNDLES_VIEW("bundles.view", "Bundles", "See bundles", null),
    BUNDLES_CREATE("bundles.create", "Bundles", "Add bundles", null),
    BUNDLES_EDIT("bundles.edit", "Bundles", "Edit bundles", null),
    BUNDLES_DELETE("bundles.delete", "Bundles", "Delete bundles", null),

    NEWSLETTER_SUBSCRIBERS("newsletter.subscribers", "Newsletter", "See subscribers", null),
    NEWSLETTER_CAMPAIGNS_VIEW("newsletter.campaigns.view", "Newsletter", "See campaigns", null),
    NEWSLETTER_CAMPAIGNS_EDIT("newsletter.campaigns.edit", "Newsletter", "Write campaigns", null),
    NEWSLETTER_CAMPAIGNS_SEND("newsletter.campaigns.send", "Newsletter", "Send campaigns", null),

    DESIGN_VIEW("design.view", "Design library", "See garments and designs", null),
    DESIGN_ITEMS("design.items", "Design library", "Manage garments and mockups", null),
    DESIGN_LOGOS("design.logos", "Design library", "Manage the design library", null),

    SETTINGS_VIEW("settings.view", "Settings", "See store settings", null),
    SETTINGS_STORE("settings.store", "Settings", "Edit policies and support contacts",
            "Return policy, exchange window, support phone, email and hours."),
    SETTINGS_HOMEPAGE("settings.homepage", "Settings", "Edit home page content",
            "Brand story, trust strip and highlights."),
    SETTINGS_FOOTER("settings.footer", "Settings", "Edit the footer", "Columns, promises and outlets."),
    SETTINGS_SIZECHART("settings.sizechart", "Settings", "Edit the size chart", null),
    SETTINGS_PAYMENTS("settings.payments", "Settings", "Turn payment methods on or off", null),

    STAFF_VIEW("staff.view", "Staff", "See staff", null),
    STAFF_MANAGE("staff.manage", "Staff", "Add staff, deactivate, reset passwords", null),
    STAFF_DELETE("staff.delete", "Staff", "Delete staff", null),
    STAFF_PERMISSIONS("staff.permissions", "Staff", "Give permissions",
            "Only permissions the person giving them already has."),

    ACTIVITY_VIEW("activity.view", "Activity log", "See the activity log", null);

    private static final Map<String, Perm> BY_KEY =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(Perm::key, Function.identity()));

    private final String key;
    private final String area;
    private final String label;
    private final String description;

    Perm(String key, String area, String label, String description) {
        this.key = key;
        this.area = area;
        this.label = label;
        this.description = description;
    }

    public String key() { return key; }
    public String area() { return area; }
    public String label() { return label; }
    public String description() { return description; }

    public static Optional<Perm> byKey(String key) {
        return Optional.ofNullable(key == null ? null : BY_KEY.get(key));
    }
}
