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

Le dashboard et les pages de télémétrie, d'événements, de trajets et Smart BMS utilisent exclusivement Compose.
Le contenu du dialogue de scan utilise aussi exclusivement Compose.
Le conteneur principal utilise un `HorizontalPager` Compose et un indicateur
de pages Compose, dans la vraie `MainActivity`.
Les anciens renderers Views et les sept bascules de rendu sont supprimés.
Les préférences de retour aux Views déjà enregistrées sont ignorées :
aucun réglage n'est nécessaire pour activer Compose.
Pour déboguer ces écrans, utiliser le Layout Inspector Compose d'Android Studio.
Les services et le BLE ne sont pas déplacés vers Compose.

Le défilement Compose est conservé pendant les cycles de vie des pages.
Les pages événements et trajets doivent aussi être activées dans les réglages des pages ;
Compose ne force pas leur visibilité.
L'apparition du BMS reste soumise aux règles
existantes de sélection par constructeur/modèle ; sa présentation sans détails est conservée.

### Référence fonctionnelle à conserver

- `MainActivity` reste l'hôte. Le pager Compose utilise le catalogue de production :
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
- Le dashboard conserve ses gestes historiques : tap klaxon si activé, double tap
  éclairage, appui long pour remplacer un bloc. La présentation BMS conserve
  les variantes par modèle, les colonnes un/deux packs et l'affichage de repli.
- L'enregistrement CSV reste piloté par le service au premier plan, même
  lorsque l'interface est arrêtée.

### Recette de parité

Vérifier le rendu Compose avec les mêmes données et préférences que les captures
historiques de référence. Le retrait des replis ne signifie pas que la recette
visuelle ou sur roue réelle est validée.

- [ ] Captures Compose comparées aux références historiques en portrait et paysage, thèmes Original/AJDM,
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
      paquet BLE ni redémarrage de la connexion.
- [ ] Remise à zéro des maxima et de la distance utilisateur reflétée sans
      nouveau paquet BLE, y compris après déconnexion, sans ligne CSV supplémentaire.
- [ ] Défilement Compose, navigation entre pages,
      rotation et retour au premier plan.
- [ ] Conteneur : mêmes marges, horloge/police, menus/icônes actives,
      glissement entre pages et indicateur, portrait/paysage, thèmes Original/AJDM.
- [ ] Activer/retirer graphique, trajets et événements depuis les réglages ;
      insertion/retrait du BMS sans changer la page consultée si elle existe encore.
- [ ] Préférences historiques de rendu à `false` ignorées : conteneur et pages
      restent Compose, sans scan, connexion, commande ou logging supplémentaire.
- [ ] Recréation : restaurer la page sélectionnée par identité, y compris
      le BMS lorsqu'il réapparaît ; suppression de la page sélectionnée avec repli valide.
- [ ] Réglages : animation, navigation interne et Retour conservés dans
      le conteneur Compose ; double Retour pour quitter hors réglages.
- [ ] PiP : entrée/sortie, Home, reprise, changements de taille et widget
      existant ; clavier, barres système et Snackbar non masqués.
- [ ] Événements ajoutés en direct sans nouveau paquet BLE, messages,
      historique et séparateur conservés au redémarrage.
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
- [ ] Présentation sans détails BMS, défilement et consultation
      de la page sans commandes ou collecteurs dupliqués.
- [ ] Scan, menus, notifications, touches volume, alarmes et PiP inchangés ;
      CSV toujours alimenté en arrière-plan, sans doublons.
- [ ] Scan : même dialogue en haut sans assombrissement, titre/progression,
      hauteur de liste, nom inconnu, ordre de découverte et adresses.
- [ ] Fin du scan à 10 secondes, arrêt sur Retour/Home/recréation, reprise
      au retour ; Bluetooth désactivé, permissions refusées/accordées et scan refusé.
- [ ] Tap sur un appareil et appui long : résultat `MAC`/`NAME`, choix automatique
      sans `PROTOCOL_ID`, protocole forcé avec son identifiant exact, annulation sans connexion.
- [ ] MAC manuelle Compose : validation et erreur existantes, clavier/curseur,
      brouillon conservé lors d'une recréation ; mot de passe effacé à la sélection.
- [ ] Arrêt/reprise du rendu scan : défilement conservé et aucune action
      depuis la recomposition, sans changement du contrat de résultat.
- [ ] Validation sur roue réelle des parcours BLE concernés.

Ces contrôles visuels et matériels ne sont pas remplacés par les tests
unitaires. Le conteneur prototype et ses points d'entrée alternatifs sont
supprimés ; le comportement historique reste la référence fonctionnelle.
Le journal auparavant figé et les caches divergents de la page des événements
sont remplacés par une source observable utilisée par Compose.
Les trajets reprennent les statistiques existantes et leurs dates lisibles ;
la lecture des fichiers et de la base est effectuée hors du rendu Compose.
Les actions de partage/suppression reprennent les menus existants.
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
Le dashboard utilise le moteur `DashboardCanvasRenderer` dans un Canvas Compose.
Ce moteur conserve le dessin historique, la présentation, le catalogue de blocs
et les actions ; la View de repli `WheelView` est supprimée.
L'ancien geste du prototype qui échangeait vitesse/PWM au tap est supprimé :
ce choix reste un réglage, indépendant de la grandeur affichée sur l'arc.
Le lot scan conserve `ScanActivity`, sa fenêtre transparente, le dialogue
Android et le sélecteur de protocole existant. Le titre, la liste, la saisie MAC
et l'indicateur de progression sont des composants Compose, alimentés par un
état de scan et les callbacks d'activité. Permissions, durée, arrêt/reprise et
résultat vers `MainActivity` ne sont jamais pilotés par la recomposition.
Le lot conteneur utilise uniquement la vraie `MainActivity` ; le lot 9 supprime
`MainActivityCompose`, `MainScreen` et leur catalogue de pages prototype.
Les pages utilisent les renderers des lots précédents via un pont `AndroidView`
vers `MainPageAdapter`, avec les mêmes notifications d'attachement, de retrait
et de recyclage. Le graphique MPAndroidChart reste en interop : ses marges et
ses traitements existants sont conservés, pas réimplémentés.
La vraie Toolbar/ActionBar, l'horloge, le NavHost des réglages et le widget PiP
gardent leurs instances dans un en-tête View réutilisé. Ce lot migre donc le
conteneur, le pager et son indicateur, pas les menus ni le graphique vers des
widgets Compose natifs. `ViewPager2` et son indicateur natif sont supprimés ;
le view binding reste activé pour l'interop encore utilisée.

### Nettoyage Compose-only et validation restante

Le nettoyage retire les points d'entrée Compose inutilisés et les collecteurs
autonomes de télémétrie, d'événements et de BMS qui ne servaient qu'au prototype.
Les écrans correspondants reçoivent désormais leur présentation depuis les
renderers de production. L'écran des trajets exige explicitement ses données
et son action de suppression : aucun parcours vide ou callback silencieux
n'est conservé comme défaut du prototype.

Les replis Views, leurs layouts, `DeviceListAdapter`, `TripAdapter`, `WheelView`
et les bascules persistantes sont supprimés. `MainPageAdapter` reste le pont
de cycle de vie utilisé par le conteneur Compose. L'interop du graphique,
de la Toolbar, de l'horloge, des dialogues et de l'en-tête réglages/PiP est conservée.
La migration est progressive, pas une conversion intégrale en widgets
Compose natifs.

Le retrait des replis est effectué ; la validation complète reste à réaliser :

- [ ] Résoudre la configuration Gradle puis réussir `:app:assembleDebug`,
      `:app:lintDebug` et `:app:testDebugUnitTest` sans contourner les contrôles.
- [ ] Exécuter la recette ci-dessus sur appareil et sur les familles de roues
      concernées, avec vérification du CSV en arrière-plan.
- [ ] Corriger les écarts de rendu, de navigation et de cycle de vie dans Compose,
      sans réintroduire de renderer de secours.
- [ ] Vérifier les régressions Compose-only : préférences historiques ignorées,
      cycle de vie, défilement, navigation et actions, sans effacer les réglages métier.
- [ ] Ne retirer une dépendance que lorsque ni l'interop ni un autre écran
      ne l'utilisent encore.

Pour la validation automatisée, utiliser les tâches existantes :
`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
La tentative dans l'environnement de migration a été bloquée avant compilation par
la résolution du plugin Android Gradle `9.2.1`. Cela ne constitue pas un résultat
de tests réussi ; la recette et la compilation restent nécessaires dans un
environnement capable de résoudre les dépendances configurées.

## 💤 Based on

[palachzzz fork](https://github.com/palachzzz/WheelLogAndroid)
