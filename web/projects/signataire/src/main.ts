import { bootstrapApplication } from '@angular/platform-browser';
import { chargerConfiguration } from '@vinasign/commun';
import { configurationApplication } from './app/app.config';
import { App } from './app/app';

chargerConfiguration()
  .then((configuration) => bootstrapApplication(App, configurationApplication(configuration)))
  .catch((erreur: Error) => {
    document.body.textContent = erreur.message;
  });
