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
  { path: '**', redirectTo: 'employees' },
];
