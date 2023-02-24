#!/bin/sh

saml-idp --acsUrl http://localhost:8000/api/saml/pori.fi \
         --audience Lupapiste \
         --cert $PWD/idp-public-cert.pem \
         --key $PWD/idp-private-key.pem \
         --encryptionCert $PWD/lupapiste-public-cert.pem \
         --encryptionPublicKey $PWD/lupapiste-public-key.pem \
         --configFile  $PWD/terttu.js \
         --rollSession true \
         $1 $2 \
         $3 $4
