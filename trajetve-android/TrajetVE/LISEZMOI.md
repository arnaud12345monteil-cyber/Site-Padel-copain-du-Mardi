# Trajet VE — appli Android (téléphone, tablette, Android Auto)

L'appli ne remplace pas le site : elle l'ouvre, et ajoute l'écran Android Auto.
Les deux partagent le même projet Firebase (trajet-ve) et le même compte Google.

## Contenu
- `app/` : code de l'appli (Kotlin)
- `keystore/` : clé de signature de l'appli (à garder, dépôt **privé** obligatoire)
- `.github/workflows/build.yml` : compilation automatique sur GitHub à chaque envoi

## Mise en place (une seule fois)
1. Console Firebase → projet trajet-ve → Paramètres du projet → Tes applications → Ajouter une appli → Android
   - Nom du package : `fr.trajetve.app`
   - Certificat SHA-1 : `BF:83:D4:1F:67:7C:5C:6D:F5:A6:F4:27:E5:15:94:B8:E4:87:FC:5E`
2. Télécharger `google-services.json` et le mettre dans le dossier `app/`
3. Créer un dépôt GitHub **privé** (ex. `trajetve-android`) et y envoyer tout le contenu de ce dossier
4. Onglet Actions du dépôt → « Compiler l'APK » → à la fin, télécharger l'artefact `TrajetVE-apk`
5. Sur la tablette : ouvrir l'APK, autoriser l'installation, puis se connecter avec Google dans l'appli

## Android Auto
- Dans la voiture, l'appli apparaît dans le menu des applis Android Auto (catégorie recharge).
- Installée par APK (hors Play Store), Android Auto ne l'affiche qu'avec les « sources inconnues »
  activées dans ses paramètres développeur (Paramètres Android Auto → toucher 10 fois « Version »
  → menu ⋮ → Paramètres pour les développeurs → Sources inconnues).
- Pour le téléphone pro : publication en test interne sur Google Play (compte développeur requis).
