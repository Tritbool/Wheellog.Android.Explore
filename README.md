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

## Migration Compose : pages, scan et conteneur principal

Le dashboard et les pages de télémétrie, d'événements, de trajets et Smart BMS utilisent Compose par défaut.
Le contenu du dialogue de scan utilise aussi Compose par défaut.
Le conteneur principal utilise un `HorizontalPager` Compose et un indicateur
de pages Compose par défaut, dans la vraie `MainActivity`.
Les Views restent la référence de comparaison et un affichage de secours.
Les services et le BLE ne sont pas déplacés vers Compose.

Les réglages généraux permettent de désactiver séparément Compose pour la
télémétrie, les événements, les trajets, le BMS, le dashboard, le scan et le
conteneur principal. Les options sont persistantes et indépendantes.
Un choix explicite de retour aux Views déjà enregistré
reste respecté : réactiver la bascule si nécessaire après mise à jour.
La désactivation restaure les Views sans redémarrer la session BLE.
Chaque affichage conserve son propre défilement pendant la bascule.
Les pages événements et trajets doivent aussi être activées dans les réglages des pages ;
le choix de son moteur de rendu ne force pas sa visibilité.
La bascule BMS ne force pas non plus l'apparition de sa page : les règles
existantes de sélection par constructeur/modèle et de repli sont conservées.

### Référence fonctionnelle à conserver

- `MainActivity` reste l'hôte. Les deux pagers utilisent le même catalogue :
  dashboard, paramètres, graphique/trajets/événements optionnels et insertion
  du BMS selon la roue. La sélection suit l'identité de la page plutôt que
  son index lorsqu'une page est ajoutée ou retirée.
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
  éclairage, appui long pour remplacer un bloc. Les deux rendus BMS partagent
  les variantes par modèle, les colonnes un/deux packs et l'affichage de repli.
- L'enregistrement CSV reste piloté par le service au premier plan, même
  lorsque l'interface est arrêtée.

### Recette de parité

Effectuer les comparaisons avec les mêmes données et préférences, puis utiliser
les bascules pour revenir aux Views. L'activation par défaut facilite les essais,
mais ne signifie pas que la recette visuelle ou sur roue réelle est validée.

- [ ] Captures Views/Compose en portrait et paysage, thèmes Original/AJDM,
      clair/sombre, français/anglais et plusieurs tailles de texte.
- [ ] Même ordre des champs et mêmes valeurs pour chaque famille de roues ;
      km/h/mph, km/miles, Celsius/Fahrenheit, arrondis et états textuels.
- [ ] Démarrage sans roue, connexion, premières données, perte de connexion,
      reconnexion, changement de constructeur et données partielles.
- [ ] Modification des unités sans nouveau paquet BLE : rafraîchissement de
      la page Compose, sans commande envoyée à la roue.
- [ ] Dashboard : mêmes jauges Original/AJDM, arcs de vitesse/courant/courant
      de phase/PWM, couleurs, valeurs centrales et nom de roue.
- [ ] Batterie et température : valeur courante, minimum batterie et maximum
      température aux mêmes positions que les Views, y compris les valeurs nulles.
- [ ] Blocs : sélection et ordre identiques, formats et unités de référence,
      appui long et remplacement persistant, disposition selon la taille disponible.
- [ ] Tap simple : klaxon uniquement si activé ; double tap : éclairage selon
      les commandes disponibles, sans klaxon ou commande supplémentaire.
- [ ] Préférences de jauge, unités, thèmes et blocs actualisées sans nouveau
      paquet BLE ; bascule dashboard Views/Compose sans redémarrer la connexion.
- [ ] Remise à zéro des maxima et de la distance utilisateur reflétée sans
      nouveau paquet BLE, y compris après déconnexion, sans ligne CSV supplémentaire.
- [ ] Bascule dans les deux sens, défilement, navigation entre pages,
      rotation et retour au premier plan.
- [ ] Conteneur : mêmes marges, horloge/police, menus/icônes actives,
      glissement entre pages et indicateur, portrait/paysage, thèmes Original/AJDM.
- [ ] Activer/retirer graphique, trajets et événements depuis les réglages ;
      insertion/retrait du BMS sans changer la page consultée si elle existe encore.
- [ ] Bascule conteneur Compose/Views en conservant la page sélectionnée,
      sans connexion, commande ou logging supplémentaire ; aucun collecteur dupliqué.
- [ ] Recréation : restaurer la page sélectionnée par identité, y compris
      le BMS lorsqu'il réapparaît ; suppression de la page sélectionnée avec repli valide.
- [ ] Réglages : animation, navigation interne et Retour conservés lors
      d'une bascule du conteneur ; double Retour pour quitter hors réglages.
- [ ] PiP : entrée/sortie, Home, reprise, changements de taille et widget
      existant ; clavier, barres système et Snackbar non masqués.
- [ ] Événements ajoutés en direct sans nouveau paquet BLE, mêmes messages
      en Views et Compose, historique et séparateur conservés au redémarrage.
- [ ] Journal borné, messages complets, absence de doublons après navigation
      et suppression/réactivation de la page des événements.
- [ ] Trajets : mêmes dates lisibles, deux colonnes de statistiques, unités,
      police, couleurs et menu accessible par bouton ou appui long.
- [ ] Partage CSV via le sélecteur Android avec autorisation de lecture ;
      suppression uniquement après confirmation, fichier et base cohérents.
- [ ] En cas d'échec de suppression, message d'erreur et trajet conservé.
- [ ] Liste actualisée au retour au premier plan, après import, arrêt du
      logging et suppression, sans résultat périmé remplaçant une liste récente.
- [ ] BMS : mêmes champs, ordre, unités, précisions et numéros de cellules
      pour chaque constructeur/modèle ; mêmes grilles en portrait/paysage.
- [ ] Un/deux packs : tensions, courants, températures et cellules distincts,
      extrema avec leur indice, écarts et marqueurs d'équilibrage.
- [ ] Paquets incomplets : pas de disparition des cellules déjà reçues ;
      déconnexion réelle et changement de roue sans données de l'ancienne roue.
- [ ] CSV BMS : aucune mesure de l'ancienne roue après reconnexion/réinitialisation ;
      une réponse BMS vide invalide les mesures de logging sans effacer la grille.
- [ ] Repli sans détails BMS, bascule Views/Compose, défilement et consultation
      de la page sans commandes ou collecteurs dupliqués.
- [ ] Scan, menus, notifications, touches volume, alarmes et PiP inchangés ;
      CSV toujours alimenté en arrière-plan, sans doublons.
- [ ] Scan : même dialogue en haut sans assombrissement, titre/progression,
      hauteur de liste, nom inconnu, ordre de découverte et adresses.
- [ ] Fin du scan à 10 secondes, arrêt sur Retour/Home/recréation, reprise
      au retour ; Bluetooth désactivé, permissions refusées/accordées et scan refusé.
- [ ] Tap sur un appareil et appui long : résultat `MAC`/`NAME`, choix automatique
      sans `PROTOCOL_ID`, protocole forcé avec son identifiant exact, annulation sans connexion.
- [ ] MAC manuelle : validation et erreur existantes, clavier/curseur,
      brouillon conservé lors d'une bascule et d'une recréation ; mot de passe effacé à la sélection.
- [ ] Bascule scan Views/Compose sans nouveau scan ni changement de résultat ;
      défilement conservé séparément et aucune action depuis la recomposition.
- [ ] Validation sur roue réelle des parcours BLE concernés.

Ces contrôles visuels et matériels ne sont pas remplacés par les tests
unitaires. Les prototypes Compose des autres pages
restent hors périmètre ; ils ne doivent pas servir de référence de migration.
Le journal auparavant figé et les caches divergents de la page des événements
sont remplacés par une source observable commune aux deux affichages.
Les trajets reprennent les statistiques existantes et leurs dates lisibles ;
la lecture des fichiers et de la base est effectuée hors du rendu Compose.
Les actions de partage/suppression reprennent les menus existants, avec
correction des identifiants de menu incohérents dans le fallback Views.
Les fonctions d'upload et d'ouverture electro.club auparavant incomplètes
ne sont pas réactivées par cette migration.
Le prototype BMS simplifié est remplacé par la présentation de référence :
libellés traduits, champs propres aux modèles et comparaison des packs.
Compose reçoit un état de présentation figé, pas les tableaux de cellules
mutables du décodeur. Les températures BMS gardent les formats Celsius de
la référence ; la préférence Fahrenheit de la télémétrie ne change pas ce lot.
Les cellules proviennent de l'API par pack : en son absence, les valeurs déjà
reçues sont conservées, ou la page reste sur la télémétrie de repli. Le tableau
global potentiellement concaténé n'est pas attribué arbitrairement au pack 1.
Le masque d'équilibrage actuel étant limité à 32 bits, aucun marqueur `[B]`
n'est inventé au-delà de la cellule 32.
Le logging BMS lit le dernier instantané publié avant la télémétrie ;
déconnexion, réinitialisation et changement de roue invalident cet instantané
pour ne pas réutiliser les mesures de la session précédente.
Le lot dashboard extrait le dessin de `WheelView` dans un moteur Canvas
commun, utilisé par la View de repli et le Canvas Compose. Les deux affichages
partagent la présentation, le catalogue de blocs et les actions ; le repli
n'est donc plus une copie indépendante du moteur historique.
L'ancien geste du prototype qui échangeait vitesse/PWM au tap est supprimé :
ce choix reste un réglage, indépendant de la grandeur affichée sur l'arc.
Le lot scan conserve `ScanActivity`, sa fenêtre transparente, le dialogue
Android et le sélecteur de protocole existant. Compose remplace le titre et
la liste ; la saisie MAC Material et l'indicateur de progression restent des
Views intégrées via `AndroidView`, volontairement, pour conserver leur rendu
et leur comportement clavier. Les deux affichages partagent un état de scan
et les mêmes callbacks d'activité. Permissions, durée, arrêt/reprise et
résultat vers `MainActivity` ne sont jamais pilotés par la recomposition.
Le lot conteneur ne démarre pas `MainActivityCompose` ni son catalogue prototype.
Les pages utilisent les renderers des lots précédents via un pont `AndroidView`
vers `MainPageAdapter`, avec les mêmes notifications d'attachement, de retrait
et de recyclage. Le graphique MPAndroidChart reste en interop : ses marges et
ses traitements existants sont conservés, pas réimplémentés.
La vraie Toolbar/ActionBar, l'horloge, le NavHost des réglages et le widget PiP
gardent leurs instances dans un en-tête View réutilisé. Ce lot migre donc le
conteneur, le pager et son indicateur, pas les menus ni le graphique vers des
widgets Compose natifs. Le repli restaure `ViewPager2` sans modifier les
bascules de rendu de chaque page.

Pour la validation automatisée, utiliser les tâches existantes :
`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
La tentative de référence dans le sandbox a été bloquée avant compilation par
la résolution du plugin Android Gradle `9.2.1`. Cela ne constitue pas un résultat
de tests réussi ; la recette et la compilation restent nécessaires dans un
environnement capable de résoudre les dépendances configurées.

## 💤 Based on

[palachzzz fork](https://github.com/palachzzz/WheelLogAndroid)
