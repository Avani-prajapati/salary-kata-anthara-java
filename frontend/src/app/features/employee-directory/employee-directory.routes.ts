import { Routes } from '@angular/router';

export const EMPLOYEE_DIRECTORY_ROUTES: Routes = [
  {
    path: '',
    title: 'Employee directory · ACME Salary Management',
    loadComponent: () =>
      import('./employee-directory-page.component').then(
        (module) => module.EmployeeDirectoryPageComponent,
      ),
  },
];
