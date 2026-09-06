export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Category {
  id: number;
  name: string;
  description: string | null;
}

export interface Product {
  id: number;
  name: string;
  sku: string;
  description: string | null;
  price: number;
  cost: number;
  stock: number;
  minimumStock: number;
  active: boolean;
  categoryId: number;
}

export interface Dashboard {
  date: string;
  timeZone: string;
  saleCount: number;
  revenue: number;
  unitsSold: number;
  grossProfit: number;
  lowStockProducts: PageResponse<Pick<Product, 'id' | 'name' | 'sku' | 'stock' | 'minimumStock'>>;
}

export interface ApiError {
  message?: string;
  fieldErrors?: Record<string, string[]>;
}
