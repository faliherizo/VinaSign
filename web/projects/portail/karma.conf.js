// Karma configuration file, see link for more information
// https://karma-runner.github.io/1.0/config/configuration-file.html

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
    ],
    client: {
      jasmine: {
        // you can add configuration options for Jasmine here
        // the possible options are listed at https://jasmine.github.io/api/edge/Configuration.html
        // for example, you can disable the random execution with `random: false`
        // or set a specific seed with `seed: 4321`
      },
    },
    jasmineHtmlReporter: {
      suppressAll: true // removes the duplicated traces
    },
    coverageReporter: {
      dir: require('path').join(__dirname, '../../coverage/portail'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' },
        { type: 'json-summary' }
      ],
      // Portes bloquantes (CLAUDE.md) : la construction échoue sous ces seuils, globalement et pour chaque fichier.
      check: {
        global: { lines: 96, branches: 92, statements: 96, functions: 96 },
        each: { lines: 96 }
      }
    },
    reporters: ['progress', 'kjhtml'],
    // Chrome sans interface pour la CI ; CHROME_BIN désigne le navigateur (ex. celui installé par Playwright).
    browsers: ['ChromeSansInterface'],
    customLaunchers: {
      ChromeSansInterface: { base: 'ChromeHeadless', flags: ['--no-sandbox'] }
    },
    restartOnFileChange: true
  });
};
