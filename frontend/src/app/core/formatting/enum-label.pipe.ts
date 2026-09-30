import { Pipe, PipeTransform } from '@angular/core';

const ACRONYMS = new Set(['VP']);

@Pipe({ name: 'enumLabel' })
export class EnumLabelPipe implements PipeTransform {
  transform(value: string): string {
    if (ACRONYMS.has(value)) {
      return value;
    }
    const words = value.toLowerCase().replaceAll('_', ' ');
    return words.charAt(0).toUpperCase() + words.slice(1);
  }
}
