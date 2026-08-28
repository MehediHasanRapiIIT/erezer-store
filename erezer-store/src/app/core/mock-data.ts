import { Product } from './models';

export const PRODUCTS: Product[] = [
  {
    id: 'p1',
    slug: 'essential-white-shirt',
    name: 'Essential White Shirt',
    description: 'Crisp organic cotton shirt with a contemporary relaxed silhouette.',
    price: 59,
    image:
      'https://images.unsplash.com/photo-1621072156002-e2fccdc0b176?auto=format&fit=crop&w=900&q=80',
    category: 'Tops',
    sizes: ['S', 'M', 'L', 'XL'],
    rating: 4.8,
    isFeatured: true,
    inStock: 22
  },
  {
    id: 'p2',
    slug: 'minimal-black-blazer',
    name: 'Minimal Black Blazer',
    description: 'Structured tailoring with soft shoulders for day-to-night wear.',
    price: 149,
    image:
      'https://images.unsplash.com/photo-1593032465174-6593ea8650ad?auto=format&fit=crop&w=900&q=80',
    category: 'Outerwear',
    sizes: ['S', 'M', 'L'],
    rating: 4.7,
    isFeatured: true,
    inStock: 10
  },
  {
    id: 'p3',
    slug: 'sandwide-trouser',
    name: 'Sand Wide Trouser',
    description: 'Wide-leg trouser crafted in breathable linen blend.',
    price: 89,
    image:
      'https://images.unsplash.com/photo-1473966968600-fa801b869a1a?auto=format&fit=crop&w=900&q=80',
    category: 'Bottoms',
    sizes: ['S', 'M', 'L', 'XL'],
    rating: 4.6,
    inStock: 18
  },
  {
    id: 'p4',
    slug: 'leather-shoulder-bag',
    name: 'Leather Shoulder Bag',
    description: 'Clean profile bag with premium hardware and magnetic closure.',
    price: 129,
    image:
      'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80',
    category: 'Accessories',
    sizes: ['One Size'],
    rating: 4.9,
    isFeatured: true,
    inStock: 9
  },
  {
    id: 'p5',
    slug: 'charcoal-knit-top',
    name: 'Charcoal Knit Top',
    description: 'Ribbed knit top with premium stretch and subtle matte finish.',
    price: 69,
    image:
      'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=900&q=80',
    category: 'Tops',
    sizes: ['S', 'M', 'L'],
    rating: 4.5,
    inStock: 14
  },
  {
    id: 'p6',
    slug: 'olive-trench-coat',
    name: 'Olive Trench Coat',
    description: 'Water-resistant trench with clean lapel and adjustable waist tie.',
    price: 199,
    image:
      'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=900&q=80',
    category: 'Outerwear',
    sizes: ['M', 'L', 'XL'],
    rating: 4.8,
    inStock: 7
  }
];
