import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'employees' },
  {
    path: 'employees',
    loadChildren: () =>
      import('./features/employee-directory/employee-directory.routes').then(
        (module) => module.EMPLOYEE_DIRECTORY_ROUTES,
      ),
  },
  {
    path: 'insights',
    title: 'Pay insights · ACME Salary Management',
    loadComponent: () =>
      import('./features/pay-insights/pay-insights-page.component').then(
        (module) => module.PayInsightsPageComponent,
      ),
  },
  { path: '**', redirectTo: 'employees' },
];
