# Dailymotion Downloader

Application Android simple pour télécharger des vidéos Dailymotion avec une interface rétro Windows 95.

## 📥 Téléchargement

### Télécharger l'APK directement
[![Télécharger APK](https://img.shields.io/badge/Télécharger-APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/val34/Dailymotion-Downloader-Android-APK/releases/latest/download/dailymotion-downloader.apk)

**OU**

1. Allez sur [Releases](https://github.com/val34/Dailymotion-Downloader-Android-APK/releases)
2. Téléchargez le fichier `dailymotion-downloader.apk`
3. Installez-le sur votre téléphone Android

> **Note:** Vous devrez peut-être autoriser l'installation d'applications depuis des sources inconnues dans les paramètres de votre téléphone.

## Fonctionnalités

- **Interface rétro Windows 95** - Design nostalgique et simple à utiliser
- **Téléchargement facile** - Collez simplement le lien de la vidéo
- **Partage depuis Dailymotion** - Partagez directement depuis l'app Dailymotion
- **Qualité automatique** - Télécharge automatiquement la meilleure qualité disponible
- **Historique** - Gardez une trace de tous vos téléchargements
- **Notifications** - Suivez la progression du téléchargement

## Utilisation

### Méthode 1: Coller le lien
1. Ouvrez l'application
2. Collez le lien de la vidéo Dailymotion dans le champ texte
3. Appuyez sur "Télécharger"

### Méthode 2: Partager depuis Dailymotion
1. Ouvrez l'app Dailymotion ou le navigateur
2. Trouvez la vidéo que vous voulez télécharger
3. Appuyez sur "Partager"
4. Sélectionnez "Dailymotion Downloader"

## Configuration requise

- Android 5.0 (Lollipop) ou supérieur
- Connexion Internet
- Espace de stockage disponible

## 🔧 Construction de l'APK (Pour développeurs)

### Méthode automatique avec GitHub Actions
1. Poussez votre code sur GitHub
2. L'APK sera automatiquement compilé
3. Téléchargez-le depuis l'onglet "Actions"

### Méthode manuelle
Pour générer l'APK localement:

```bash
# En mode debug
./gradlew assembleDebug

# En mode release
./gradlew assembleRelease
```

L'APK sera généré dans: `app/build/outputs/apk/`

### Créer une release
Pour créer une nouvelle version publique:

```bash
git tag v1.0.0
git push origin v1.0.0
```

L'APK sera automatiquement publié dans les Releases GitHub.

## Permissions

L'application nécessite les permissions suivantes:
- **Internet** - Pour télécharger les vidéos
- **Stockage** - Pour sauvegarder les vidéos téléchargées
- **Notifications** - Pour afficher la progression du téléchargement

## Emplacement des fichiers

Les vidéos téléchargées sont sauvegardées dans:
`/Android/data/com.dailymotion.downloader/files/Dailymotion/`

## Licence

Usage personnel uniquement. Respectez les droits d'auteur et les conditions d'utilisation de Dailymotion.
