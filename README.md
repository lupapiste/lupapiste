# Lupapiste

## Requirements

* leiningen
* mongodb running on localhost, on default port
* pdftk
* compass
* optionally pdf2pdf for PDF/A conversions

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
