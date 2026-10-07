# WheelLog.Android

[![Latest release](https://img.shields.io/github/release/wheellog/wheellog.android.svg)](https://github.com/wheellog/wheellog.android/releases/latest)
[![Build Status](https://github.com/wheellog/wheellog.android/workflows/Gradle%20CI/badge.svg?branch=master)](https://github.com/wheellog/wheellog.android/actions)

Unofficial App for EUC on Android. 
The following manufacturers are supported:
- InMotion
- KingSong
- Gotway
- Veteran
- Ninebot Zx, S2, C/P/E+, Mini

| <img src='https://github.com/Wheellog/Wheellog.Android/assets/27482193/2dd0d43b-53a6-4dc4-9794-ddee620628bb' width=400 /> | <img src='https://user-images.githubusercontent.com/27482193/114169041-bdb6ee00-9939-11eb-8fb9-f07b9eac1b2f.png' width=400 /> | <img src='https://github.com/Wheellog/Wheellog.Android/assets/27482193/d7fb7006-1ab3-4ca1-be9f-8d59ec88e376' width=400 />
|--|--|--|

## Contributing

### 🛠️ Pull requests
A pull request is a way to suggest changes in this repository. We accept pull requests in the `master` branch.

## App stores

 [![google-play-badge](http://github.com/Wheellog/Wheellog.Android/assets/27482193/8cc988b8-d5af-4c3f-a87a-9cd2c72f4b65)](https://play.google.com/store/apps/details?id=com.cooper.wheellog)
 [![huawei-app-gallery](http://github.com/Wheellog/Wheellog.Android/assets/27482193/bbf2dbea-95cf-465e-9244-ba12e9aa2fe0)](https://appgallery.huawei.com/#/app/C109077151)

## ⌚ Smart watch applications

- [Samsung Gear](https://github.com/juliomap/WheelLog-Tizen)
- Smart watch from Pebble company. Discontinued in 2016. [app code](https://github.com/JumpMaster/WheelLogPebble)
- Garmin Connect IQ [watch application](https://github.com/Wheellog/Companion.Garmin).
You can also download the app from [ConnectIQ Store](https://apps.garmin.com/en-US/apps/35719a02-8a5d-46bc-b474-f26c54c4e045).
- Xiaomi [Mi band 3/4/5/6](https://github.com/Wheellog/Wheellog.Android/wiki/Work-with-Mi-Band)

## 🚀 Quick start

Minimum requirements:
- Device running Windows, macOS, Linux or chromeOS
- Android device [in dev mode and enable USB debugging](https://developer.android.com/studio/debug/dev-options)

Let's start
- Download, install and open [Android studio](https://developer.android.com/studio/)
- In main menu `File -> New -> Project From Version Control` 

![image](https://user-images.githubusercontent.com/27482193/115096600-8abebc80-9f2e-11eb-9ba5-3a70dba14e17.png)
- Insert URL `https://github.com/Wheellog/Wheellog.Android.git` and click on `Clone`
- Install SDK and connect android device
- Click `Run` button - Enjoy!

## Migration Compose : première étape

Le périmètre initial est limité à la coexistence Views/Compose et à la page des
paramètres de télémétrie. Les Views restent activées par défaut et constituent
la référence. Le dashboard, le scan, les services, le BLE et la navigation
principale ne sont pas migrés dans cette étape.

### Référence fonctionnelle à conserver

- `MainActivity` et `ViewPager2` restent les hôtes : dashboard, paramètres,
  graphique/trajets/événements optionnels et insertion du BMS selon la roue.
- La page des paramètres conserve les champs et leur ordre par famille :
  KingSong, Veteran, Gotway, InMotion V2, InMotion, Ninebot/Ninebot Z.
  Les familles non reconnues ne reçoivent pas une liste générique inventée.
- Les valeurs gardent les unités, précisions, traductions et conversions de
  température utilisées par les Views. La pression du prototype Compose
  n'est pas ajoutée à la liste de référence.
- Les actions existantes restent hors du rendu : connexion/déconnexion,
  scan et sélection du protocole, logging et confirmation de nouveau fichier,
  remise à zéro des extrema, réglages, permissions et actions de notification.
- Le dashboard conserve ses gestes Views : tap klaxon si activé, double tap
  éclairage, appui long pour remplacer un bloc. Les règles BMS restent dans
  l'adapter : variantes par modèle, un/deux packs et affichage de repli.
- L'enregistrement CSV reste piloté par le service au premier plan, même
  lorsque l'interface est arrêtée.

### Recette avant activation par défaut

Effectuer les comparaisons avec les mêmes données et préférences, en activant
uniquement la bascule de la page des paramètres, puis revenir aux Views.

- [ ] Captures Views/Compose en portrait et paysage, thèmes Original/AJDM,
      clair/sombre, français/anglais et plusieurs tailles de texte.
- [ ] Même ordre des champs et mêmes valeurs pour chaque famille de roues ;
      km/h/mph, km/miles, Celsius/Fahrenheit, arrondis et états textuels.
- [ ] Démarrage sans roue, connexion, premières données, perte de connexion,
      reconnexion, changement de constructeur et données partielles.
- [ ] Modification des unités sans nouveau paquet BLE : rafraîchissement de
      la page Compose, sans commande envoyée à la roue.
- [ ] Bascule dans les deux sens, défilement, navigation entre pages,
      rotation et retour au premier plan.
- [ ] Scan, menus, notifications, touches volume, alarmes et PiP inchangés ;
      CSV toujours alimenté en arrière-plan, sans doublons.
- [ ] Validation sur roue réelle des parcours BLE concernés.

Ces contrôles visuels et matériels ne sont pas remplacés par les tests
unitaires. Les prototypes Compose des autres pages et leurs écarts connus
(journal figé, trajets simplifiés, BMS incomplet, gestes du dashboard différents)
restent hors périmètre ; ils ne doivent pas servir de référence de migration.

Pour la validation automatisée, utiliser les tâches existantes :
`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
La tentative de référence dans le sandbox a été bloquée avant compilation par
la résolution du plugin Android Gradle `9.2.1`. Cela ne constitue pas un résultat
de tests réussi ; la recette et la compilation restent nécessaires dans un
environnement capable de résoudre les dépendances configurées.

## 💤 Based on

[palachzzz fork](https://github.com/palachzzz/WheelLogAndroid)
