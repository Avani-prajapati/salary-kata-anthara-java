export interface PagedResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}
