import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmployeeDetail } from '../../core/models/employee.model';
import { EmployeeDetailPanelComponent } from './employee-detail-panel.component';

const JANE_ITO: EmployeeDetail = {
  id: 7,
  name: 'Jane Ito',
  department: 'ENGINEERING',
  jobLevel: 'SENIOR',
  countryCode: 'JP',
  hireDate: '2021-03-15',
  managerId: null,
  managerName: null,
  salaryAmount: 9800000,
  salaryCurrency: 'JPY',
};

describe('employee detail panel', () => {
  let fixture: ComponentFixture<EmployeeDetailPanelComponent>;
  let httpTesting: HttpTestingController;
  let triggerButton: HTMLButtonElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [EmployeeDetailPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeDetailPanelComponent);

    triggerButton = document.createElement('button');
    triggerButton.textContent = 'Jane Ito';
    document.body.appendChild(triggerButton);

    await fixture.whenStable();
  });

  afterEach(() => {
    httpTesting.verify();
    triggerButton.remove();
  });

  function drawerElement(): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('.detail-drawer') as HTMLElement;
  }

  async function openForJaneIto(): Promise<void> {
    fixture.componentRef.setInput('employeeId', 7);
    fixture.componentRef.setInput('triggerElement', triggerButton);
    await fixture.whenStable();
    httpTesting.expectOne('/api/employees/7').flush(JANE_ITO);
    await fixture.whenStable();
  }

  it('moves keyboard focus inside the panel once it opens', async () => {
    await openForJaneIto();

    expect(drawerElement().contains(document.activeElement)).toBe(true);
  });

  it('returns focus to the triggering element when the close button is activated', async () => {
    await openForJaneIto();

    const closeButton = drawerElement().querySelector('button') as HTMLButtonElement;
    closeButton.click();
    await fixture.whenStable();

    expect(document.activeElement).toBe(triggerButton);
  });

  it('returns focus to the triggering element when Escape is pressed inside the panel', async () => {
    await openForJaneIto();

    drawerElement().dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }),
    );
    await fixture.whenStable();

    expect(document.activeElement).toBe(triggerButton);
  });
});
