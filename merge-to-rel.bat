@echo off
set REL=%1
if [%REL%]==[] set /P REL=Release numero, esim. 1.30:
echo Pulling latest revisions...
hg pull
hg up "release/%REL%"
hg merge develop
hg commit -m"Merged develop"
hg up develop
echo Merge complete, don't forget to push
