# SAML Testing

Lupapiste uses SAML for facilitating Single Sign On for Active
Directory counterparts (e.g., pori.fi). Thus, the corresponding code
refers to SAML functionality as AD login. For the development it is
advisable to install a SAML IdP locally.

[saml-idp](https://github.com/mcguinness/saml-idp) is a good choice
since it is quite straigh-forward to set up and supports also
encrypted assertions.

[SAMLING](https://github.com/capriza/samling) is a good lightweight
alternative that allows flexible response modification and
access. However, it does not support encrypted assertions.

## Keys and Certificates
This folder contains the following keys and certifcates:

- [idp-private-key.pem](idp-private-key.pem) IdP private key that is
  used by IdP to sign responses. The mock responses used in itests are
  signed with this key as well.
- [idp-public-cert.pem](idp-public-cert.pem) IdP certificate for
  checking the IdP signatures in Lupapiste. This is also in Pori
  minimal.
- [lupapiste-public-cert.pem](lupapiste-public-cert.pem) Lupapiste
  certificate and its [public key](lupapiste-public-key.pem). These
  are used by IdP to encrypt the assertions, when needed. Note that
  the certifcate is the same as in local/dev.properties.
- [unknown-public-cert.pem](unknown-public-cert.pem) and its [public
  key](unknown-public-key.pem) are used to generate mock responses
  that are encrypted with a key that Lupapiste does not know.

## Development IdP

1. Install saml-idp as a global executable: `npm install saml-idp -g`
2. Go to this folder
3. Execute [start-idp.sh](start-idp.sh)
4. The IdP is now running in port 7000. This is in line with minimal.

The configuration file [terttu.js](terttu.js) defines the AD
attributes with Pori-friendly user values. You can give some
additional arguments to the script. For example, if you want to
encrypt assertions:

```
./start-idp.sh --enc true
```

Some features can only (?) be configured via saml-idp UI. For example,
whether the whole response or only the assertions are signed.
