import { DOCUMENT } from '@angular/common';
import { inject, Injectable, signal } from '@angular/core';
const translations = {
  fr: {
    workspace: 'Espace de gestion',
    overview: 'Vue d’ensemble',
    branches: 'Établissements',
    catalog: 'Produits & stock',
    purchases: 'Achats & réceptions',
    sales: 'Ventes & commandes',
    finance: 'Caisse & règlements',
    sav: 'Retours & SAV',
    soon: 'À venir',
    welcome: 'Tout RH, au même endroit.',
    intro: 'Le dépôt, vos magasins et vos équipes réunis dans un espace de travail.',
    signin: 'Se connecter',
    secure: 'Connexion sécurisée',
    signinNote:
      'Utilisez votre compte RH. Vos accès dépendent de votre rôle et de votre établissement.',
    foundation: 'Catalogue et stock',
    foundationBody:
      'Le catalogue et le stock sont disponibles pour le dépôt et les magasins. Les ventes et la caisse sont les prochaines étapes.',
    connected: 'Connecté',
    signout: 'Se déconnecter',
    network: 'Votre réseau RH',
    networkNote: 'Établissements accessibles avec votre compte.',
    depot: 'Dépôt',
    store: 'Magasin',
    active: 'Actif',
    loading: 'Chargement de votre espace…',
    error: 'Impossible de charger les données. Vérifiez la connexion et réessayez.',
    retry: 'Réessayer',
    empty: 'Aucun établissement affecté à votre compte. Contactez le responsable.',
    local: 'Environnement de développement',
    localNote: 'Noms et affectations de démonstration, à confirmer avec RH.',
    access: 'Accès par rôle',
    accessNote: 'Chaque personne consulte les établissements qui lui sont attribués.',
    trace: 'Une base commune',
    traceNote: 'Le dépôt et les magasins partagent des informations cohérentes.',
    next: 'La suite du projet',
    nextNote: 'Achats, ventes, crédit, caisse et service après-vente.',
    print: 'Imprimer la fiche des établissements',
    printTitle: 'RH — Établissements accessibles',
    all: 'Réseau de magasins & dépôt',
    language: 'Langue',
    accessDenied:
      'Votre compte ne dispose pas des accès nécessaires. Contactez le responsable ou changez de compte.',
    reference: 'Réf.',
    type: 'Type',
    status: 'État',
  },
  en: {
    workspace: 'Management workspace',
    overview: 'Overview',
    branches: 'Locations',
    catalog: 'Products & stock',
    purchases: 'Purchases & receipts',
    sales: 'Sales & orders',
    finance: 'Cash & payments',
    sav: 'Returns & service',
    soon: 'Coming next',
    welcome: 'All of RH, in one place.',
    intro: 'Your warehouse, stores and team in one working space.',
    signin: 'Sign in',
    secure: 'Secure sign-in',
    signinNote: 'Use your RH account. Access depends on your role and assigned location.',
    foundation: 'Catalog and inventory',
    foundationBody:
      'Catalog and inventory are available for the warehouse and stores. Sales and cash management are the next steps.',
    connected: 'Signed in',
    signout: 'Sign out',
    network: 'Your RH network',
    networkNote: 'Locations accessible with your account.',
    depot: 'Warehouse',
    store: 'Store',
    active: 'Active',
    loading: 'Loading your workspace…',
    error: 'Unable to load data. Check your connection and try again.',
    retry: 'Try again',
    empty: 'No locations assigned to your account. Contact your manager.',
    local: 'Development environment',
    localNote: 'Demo names and assignments, to be confirmed with RH.',
    access: 'Role-based access',
    accessNote: 'Each person sees the locations assigned to them.',
    trace: 'One shared foundation',
    traceNote: 'The warehouse and stores work from consistent information.',
    next: 'What comes next',
    nextNote: 'Purchasing, sales, credit, cash management and after-sales service.',
    print: 'Print locations sheet',
    printTitle: 'RH — Accessible locations',
    all: 'Stores & warehouse network',
    language: 'Language',
    accessDenied:
      'Your account does not have the required access. Contact your manager or switch accounts.',
    reference: 'Ref.',
    type: 'Type',
    status: 'Status',
  },
  ar: {
    workspace: 'فضاء التسيير',
    overview: 'نظرة عامة',
    branches: 'الفروع',
    catalog: 'المنتجات والمخزون',
    purchases: 'المشتريات والاستلام',
    sales: 'المبيعات والطلبات',
    finance: 'الصندوق والأداءات',
    sav: 'المرتجعات وخدمة ما بعد البيع',
    soon: 'قريباً',
    welcome: 'كل أعمال RH، في مكان واحد.',
    intro: 'المستودع والمحلات وفريق العمل في فضاء موحد.',
    signin: 'تسجيل الدخول',
    secure: 'دخول آمن',
    signinNote: 'استعمل حساب RH الخاص بك. الصلاحيات حسب دورك والفرع المخصص لك.',
    foundation: 'المنتجات والمخزون',
    foundationBody:
      'المنتجات والمخزون متاحان للمستودع والمحلات. المبيعات والصندوق في المراحل المقبلة.',
    connected: 'متصل',
    signout: 'تسجيل الخروج',
    network: 'شبكة فروع RH',
    networkNote: 'الفروع التي يمكنك الاطلاع عليها بحسابك.',
    depot: 'المستودع',
    store: 'محل',
    active: 'نشط',
    loading: 'جارٍ تحميل فضاء العمل…',
    error: 'تعذر تحميل البيانات. تحقق من الاتصال وحاول مجدداً.',
    retry: 'إعادة المحاولة',
    empty: 'لا يوجد فرع مرتبط بحسابك. تواصل مع المسؤول.',
    local: 'بيئة التطوير',
    localNote: 'أسماء وتعيينات تجريبية في انتظار تأكيد RH.',
    access: 'صلاحيات حسب الدور',
    accessNote: 'كل شخص يطلع على الفروع المخصصة له.',
    trace: 'قاعدة عمل موحدة',
    traceNote: 'معلومات متناسقة بين المستودع والمحلات.',
    next: 'المراحل المقبلة',
    nextNote: 'المشتريات والمبيعات والائتمان والصندوق وخدمة ما بعد البيع.',
    print: 'طباعة قائمة الفروع',
    printTitle: 'RH — الفروع المتاحة',
    all: 'شبكة المحلات والمستودع',
    language: 'اللغة',
    accessDenied: 'حسابك لا يتوفر على الصلاحيات المطلوبة. تواصل مع المسؤول أو غيّر الحساب.',
    reference: 'المرجع',
    type: 'النوع',
    status: 'الحالة',
  },
};
export type Language = 'fr' | 'ar' | 'en';
export type TranslationKey = keyof typeof translations.fr;
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly document = inject(DOCUMENT);
  readonly language = signal<Language>('fr');
  constructor() {
    try {
      const saved = localStorage.getItem('rh-language');
      if (saved === 'fr' || saved === 'ar' || saved === 'en') this.set(saved);
    } catch {
      /* Storage can be disabled; French remains usable. */
    }
  }
  set(language: Language) {
    this.language.set(language);
    this.document.documentElement.lang = language;
    this.document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
    try {
      localStorage.setItem('rh-language', language);
    } catch {
      /* Language still applies for this page. */
    }
  }
  t(key: TranslationKey): string {
    return translations[this.language()][key];
  }
}
