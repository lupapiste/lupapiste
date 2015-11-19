# Lupapiste

## Requirements

* leiningen
* mongodb running on localhost, on default port

## Dependencies

### iText

Lupapiste uses the last MPL/LGPL licensed iText version 4.2. However, as of
2015-07-10 the correct jar package can not be downloaded automatically with
leiningen because a new pom.xml redirects to iText 5.5.6.

The solution is to install the correct pom xml for iText 4.2.1 manually.

First, create the correct directory to your local m2 reposiory and ensure
that the directory is empty:

    mkdir -p ~/.m2/repository/com/lowagie/itext/4.2.1
    rm -f ~/.m2/repository/com/lowagie/itext/4.2.1/*

Download the pom.xml as itext-4.2.1.pom:

    wget https://raw.githubusercontent.com/weiyeh/iText-4.2.0/4480c5971f784aa45db06aebd3a48150d1b61f07/pom.xml -O ~/.m2/repository/com/lowagie/itext/4.2.1/itext-4.2.1.pom

Proceeed to download other dependencies.

### Download dependencies

    lein deps

## Usage

lein run

## Testing

### All midje tests

    lein verify

### Only unit tests

    lein midje

### End-to-end test (Robot Framework)

    cd robot
    ./local.sh

## Packaging

lein with-profile uberjar uberjar
