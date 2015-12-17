@echo off
set REL=%1
if [%REL%]==[] set /P REL=Release numero, esim. 1.30:
echo Pulling latest revisions...
git pull
git checkout "release/%REL%"
git merge develop
git commit -m"Merged develop"
git checkout develop
echo Merge complete, don't forget to push
