import { Pipe, PipeTransform } from '@angular/core';

const COUNTRY_NAMES = new Intl.DisplayNames(['en'], { type: 'region' });

@Pipe({ name: 'countryName' })
export class CountryNamePipe implements PipeTransform {
  transform(countryCode: string): string {
    return COUNTRY_NAMES.of(countryCode) ?? countryCode;
  }
}
