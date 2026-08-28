/**
 * Translation dictionaries for the storefront.
 *
 * Add a new key in {@link en} first, then mirror it in {@link bn}. Missing
 * Bangla keys fall back to the English value (see TranslateService.t()).
 *
 * Naming: dot-separated, namespace-by-area. e.g. `header.shop`, `home.welcome`.
 */
export type Lang = 'en' | 'bn';

export const SUPPORTED_LANGS: { id: Lang; label: string; native: string }[] = [
  { id: 'en', label: 'English', native: 'English' },
  { id: 'bn', label: 'Bangla',  native: 'বাংলা' },
];

export const en: Record<string, string> = {
  // Header
  'header.home':       'Home',
  'header.shop':       'Shop',
  'header.custom_design': 'Custom Design',
  'header.bundles':    'Bundles',
  'header.flash_sale': 'Flash Sale',
  'header.cart':       'Cart',
  'header.wishlist':   'Wishlist',
  'header.account':    'Account',
  'header.orders':     'Orders',
  'header.contact':    'Contact',
  'header.language':   'Language',

  // Home
  'home.featured':        'Featured pieces',
  'home.shop_all':        'Shop the latest',
  'home.recently_viewed': 'Recently viewed',
  'home.newsletter.eyebrow':  'Members only',
  'home.newsletter.headline': 'Get 10% off your first order',
  'home.newsletter.copy':     'Join the EREZER community for early drops, member-only offers, and seasonal style edits.',
  'home.newsletter.email_placeholder': 'Enter your email',
  'home.newsletter.cta':       'Subscribe',
  'home.newsletter.cta_done':  'Subscribed',
  'home.newsletter.cta_busy':  'Sending…',

  // Account
  'account.title':          'Account',
  'account.signin':         'Sign in',
  'account.register':       'Register',
  'account.forgot':         'Forgot',
  'account.email':          'you@example.com',
  'account.password':       'Password',
  'account.first_name':     'First name',
  'account.last_name':      'Last name',
  'account.signin_button':  'Sign in',
  'account.signin_busy':    'Signing in…',
  'account.signout':        'Sign out',
  'account.addresses':      'Addresses',
  'account.order_history':  'Order history',
  'account.view_all_orders':'View all orders',

  // Cart
  'cart.title':            'Your Cart',
  'cart.empty':            'Your cart is empty.',
  'cart.size':             'Size',
  'cart.remove':           'Remove',
  'cart.promo':            'Promo code',
  'cart.apply':            'Apply',
  'cart.subtotal':         'Subtotal',
  'cart.shipping':         'Shipping',
  'cart.discount':         'Discount',
  'cart.estimated_total':  'Estimated total',
  'cart.continue_checkout':'Continue to checkout',
};

/**
 * Bangla translations. Anything missing falls back to English.
 * Native speakers should refine these — these are first-pass for shipping.
 */
export const bn: Record<string, string> = {
  'header.home':       'হোম',
  'header.shop':       'শপ',
  'header.custom_design': 'কাস্টম ডিজাইন',
  'header.bundles':    'বান্ডেল',
  'header.flash_sale': 'ফ্ল্যাশ সেল',
  'header.cart':       'কার্ট',
  'header.wishlist':   'উইশলিস্ট',
  'header.account':    'অ্যাকাউন্ট',
  'header.orders':     'অর্ডার',
  'header.contact':    'যোগাযোগ',
  'header.language':   'ভাষা',

  'home.featured':        'বাছাইকৃত পোশাক',
  'home.shop_all':        'সর্বশেষ কালেকশন দেখুন',
  'home.recently_viewed': 'সম্প্রতি দেখা',
  'home.newsletter.eyebrow':  'মেম্বার অনলি',
  'home.newsletter.headline': 'প্রথম অর্ডারে ১০% ছাড়',
  'home.newsletter.copy':     'নতুন কালেকশন এবং মেম্বার-অনলি অফারের জন্য আমাদের সাথে যুক্ত হন।',
  'home.newsletter.email_placeholder': 'আপনার ইমেইল লিখুন',
  'home.newsletter.cta':       'সাবস্ক্রাইব',
  'home.newsletter.cta_done':  'সাবস্ক্রাইব হয়েছে',
  'home.newsletter.cta_busy':  'পাঠানো হচ্ছে…',

  'account.title':          'অ্যাকাউন্ট',
  'account.signin':         'লগইন',
  'account.register':       'নিবন্ধন',
  'account.forgot':         'পাসওয়ার্ড ভুলে গেছেন',
  'account.email':          'you@example.com',
  'account.password':       'পাসওয়ার্ড',
  'account.first_name':     'নাম',
  'account.last_name':      'পদবি',
  'account.signin_button':  'লগইন করুন',
  'account.signin_busy':    'লগইন হচ্ছে…',
  'account.signout':        'লগআউট',
  'account.addresses':      'ঠিকানাসমূহ',
  'account.order_history':  'অর্ডার ইতিহাস',
  'account.view_all_orders':'সব অর্ডার দেখুন',

  'cart.title':            'আপনার কার্ট',
  'cart.empty':            'আপনার কার্ট খালি।',
  'cart.size':             'সাইজ',
  'cart.remove':           'মুছুন',
  'cart.promo':            'প্রোমো কোড',
  'cart.apply':            'প্রয়োগ',
  'cart.subtotal':         'সাবটোটাল',
  'cart.shipping':         'শিপিং',
  'cart.discount':         'ডিসকাউন্ট',
  'cart.estimated_total':  'আনুমানিক মোট',
  'cart.continue_checkout':'চেকআউটে যান',
};

export const DICTIONARIES: Record<Lang, Record<string, string>> = { en, bn };
