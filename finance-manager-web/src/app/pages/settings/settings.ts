import { Component, inject } from '@angular/core';
import { LucideCheck, LucideEye, LucideEyeOff, LucideMoon, LucideSettings, LucideSun } from '@lucide/angular';
import { Theme } from '../../core/theme';
import { ValuePrivacy } from '../../core/value-privacy';

@Component({
  selector: 'app-settings',
  imports: [LucideCheck, LucideEye, LucideEyeOff, LucideMoon, LucideSettings, LucideSun],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class Settings {
  // O serviço Theme é injetado no componente Settings para gerenciar o tema da aplicação. Ele fornece métodos para alternar entre os modos claro e escuro, bem como para salvar a preferência do usuário.
  protected readonly theme = inject(Theme);
  protected readonly valuePrivacy = inject(ValuePrivacy);

  // O método setDarkMode é responsável por alterar o modo de tema da aplicação para escuro ou claro, dependendo do valor booleano fornecido como argumento. Ele utiliza o serviço Theme injetado para aplicar a mudança de tema e salvar a preferência do usuário.
  protected setDarkMode(enabled: boolean): void {
    this.theme.setDark(enabled);
  }

  protected setValuesHidden(hidden: boolean): void {
    this.valuePrivacy.setHidden(hidden);
  }
}
