# ETAPE 1: Compilatioon et build du code Angular

# Utilise l'image officiel de Nodejs pour compiler l'applicaiton
FROM node:lts AS build

# Définit le répertoire de travail
WORKDIR /usr/local/app

# Copie le code source dans le répertoire de travail
COPY ./ /usr/local/app/

# Installe les dépendances de l'application
RUN npm install

# Génère les fichiers de production de l'application
RUN npm run build


# ETAPE 2: Déployer l'application avec Nginx
# Utilise l'image officiel de Nginx pour déployer l'applciation Angular
FROM nginx:latest

# Copie les fichiers générés vers le répertoire par défaut de Nginx pour qu'ils soient accessibles via Nginx
COPY --from=build /usr/local/app/dist/angular-front-end/browser /usr/share/nginx/html

# Expose le port 80
EXPOSE 80
