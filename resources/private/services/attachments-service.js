//
// Provides services for attachments tab.
//
//
LUPAPISTE.AttachmentsService = function() {
  "use strict";
  var self = this;
  self.APPROVED = "ok";
  self.REJECTED = "requires_user_action";

  var filters = {
        preVerdict: ko.observable(false),
        postVerdict: ko.observable(false),
        notNeeded: ko.observable(false),
        ivSuunnitelmat: ko.observable(false),
        kvvSuunnitelmat: ko.observable(false),
        rakennesuunnitelmat: ko.observable(false),
        paapiirrustukset: ko.observable(false)
      };
  var dummyData = [
    {
      "type" : {
        "type-group" : "paapiirustus",
        "type-id" : "asemapiirros"
      },
      "state" : "requires_authority_action",
      "op" : {
        "name" : "linjasaneeraus",
        "description" : null,
        "created" : 1463307103457,
        "id" : "replaced-uid-56ew8dw",
        "optional" : [

        ]
      },
      "auth" : [
        {
          "id" : "replaced-uid-nrdw6ao",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463309624206,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-bwsyh5u",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-frm4u7o",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463307823583,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-ixkjvm6"
        },
        {
          "user" : {
            "id" : "replaced-uid-ymdvmbm",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-ho6hdqt"
        },
        {
          "user" : {
            "id" : "replaced-uid-wmth9a3",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-r8epa89"
        }
      ],
      "forPrinting" : false,
      "contents" : "",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463307726318,
          "size" : 11183033,
          "filename" : "01 asemapiirustus.pdf",
          "originalFileId" : "replaced-uid-ncj3q69",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-05s2o7q",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-og0b21z"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463307726318,
          "size" : 11183033,
          "filename" : "01 asemapiirustus-PDFA.pdf",
          "originalFileId" : "replaced-uid-bfvev2u",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-7q3q34x",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-7blj53j"
        }
      ],
      "required" : true,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463307726318,
        "size" : 11183033,
        "filename" : "01 asemapiirustus-PDFA.pdf",
        "originalFileId" : "replaced-uid-6dl8r3y",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-7udktho",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-uscmlzu"
      },
      "scale" : "1:500"
    },
    {
      "type" : {
        "type-group" : "suunnitelmat",
        "type-id" : "hankeselvitys"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-8dmy8jp",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "hulevesisuunnitelma"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-eh8dy58",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "iv_suunnitelma"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-9de57y9",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-hqr4ch1",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "paapiirustus",
        "type-id" : "leikkauspiirustus"
      },
      "state" : "requires_user_action",
      "op" : {
        "id" : "replaced-uid-1bvx9mb",
        "name" : "linjasaneeraus",
        "description" : null,
        "created" : 1463307103457
      },
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-yfr0pji",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "ennakkoluvat_ja_lausunnot",
        "type-id" : "naapurin_suostumus"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-7l1cbgl",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "ennakkoluvat_ja_lausunnot",
        "type-id" : "naapurin_kuuleminen"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-awiix0e",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "paapiirustus",
        "type-id" : "pohjapiirustus"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-f05y8uw",
        "name" : "linjasaneeraus",
        "description" : null,
        "created" : 1463307103457
      },
      "auth" : [
        {
          "id" : "replaced-uid-caydqrx",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463309648593,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-jnjehcq",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-atzc63y",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463307885024,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-hop1l8a"
        },
        {
          "user" : {
            "id" : "replaced-uid-e4rk6rz",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-lkialm6"
        },
        {
          "user" : {
            "id" : "replaced-uid-xd6cafx",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-y2rdefz"
        }
      ],
      "forPrinting" : false,
      "contents" : "Kellarikerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463307861524,
          "size" : 188098,
          "filename" : "02 kellari.pdf",
          "originalFileId" : "replaced-uid-8dvxu84",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-rb436md",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-seqeh1d"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463307861524,
          "size" : 188098,
          "filename" : "02 kellari-PDFA.pdf",
          "originalFileId" : "replaced-uid-6fwyugz",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-379ki3w",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-9z3nf8a"
        }
      ],
      "required" : true,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463307861524,
        "size" : 188098,
        "filename" : "02 kellari-PDFA.pdf",
        "originalFileId" : "replaced-uid-ri7l8sv",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-we5hbas",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-f344qpx"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "pohjaveden_hallintasuunnitelma"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-6hcm3at",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "selvitykset",
        "type-id" : "selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-l0b2568",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "selvitykset",
        "type-id" : "rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-0fytrh3",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "rakennuspaikan_hallinta",
        "type-id" : "rasitesopimus"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-gpuhb9k",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "selvitykset",
        "type-id" : "selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo"
      },
      "state" : "requires_user_action",
      "op" : null,
      "auth" : [

      ],
      "modified" : 1463307103457,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-lvlb31n",
      "notNeeded" : true,
      "signatures" : [

      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [

      ],
      "required" : true
    },
    {
      "type" : {
        "type-group" : "hakija",
        "type-id" : "valtakirja"
      },
      "state" : "requires_authority_action",
      "op" : null,
      "auth" : [
        {
          "id" : "replaced-uid-6mp1z1d",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463307664107,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-am2yuhj",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-1d38pyr",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463308391407,
          "version" : {
            "minor" : 0,
            "major" : 6
          },
          "fileId" : "replaced-uid-3qrhq9k"
        },
        {
          "user" : {
            "id" : "replaced-uid-puo0lp2",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 6
          },
          "fileId" : "replaced-uid-bs2me71"
        },
        {
          "user" : {
            "id" : "replaced-uid-4fjgcae",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 6
          },
          "fileId" : "replaced-uid-fpblq2s"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463307606427,
          "size" : 213612,
          "filename" : "20160404_valtakirja VeVi.pdf",
          "originalFileId" : "replaced-uid-nepztcn",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-hpe84pf",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-jlukuse"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463307606427,
          "size" : 213612,
          "filename" : "20160404_valtakirja VeVi-PDFA.pdf",
          "originalFileId" : "replaced-uid-wohtrmd",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-wd5akpy",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-b0wnn8y"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463307639223,
          "size" : 607623,
          "filename" : "Y_YHK_PTK allekirjoitettu.pdf",
          "originalFileId" : "replaced-uid-eivx791",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 3,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-18623fa",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-vkqk3iv"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463307639223,
          "size" : 607623,
          "filename" : "Y_YHK_PTK allekirjoitettu-PDFA.pdf",
          "originalFileId" : "replaced-uid-iqcvobu",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 4,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-phmi0g1",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-laqpfol"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463307664107,
          "size" : 213612,
          "filename" : "20160404_valtakirja VeVi.pdf",
          "originalFileId" : "replaced-uid-l6hpaxn",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 5,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-9oifxbs",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-uoc43gw"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463307664107,
          "size" : 213612,
          "filename" : "20160404_valtakirja VeVi-PDFA.pdf",
          "originalFileId" : "replaced-uid-lrl8vrk",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 6,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-zg6pvhm",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-lpncvns"
        }
      ],
      "required" : true,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463307664107,
        "size" : 213612,
        "filename" : "20160404_valtakirja VeVi-PDFA.pdf",
        "originalFileId" : "replaced-uid-f8wa4lx",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 6,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-zvxuc94",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-dbj77s8"
      }
    },
    {
      "type" : {
        "type-group" : "ennakkoluvat_ja_lausunnot",
        "type-id" : "vesi_ja_viemariliitoslausunto_tai_kartta"
      },
      "state" : "requires_authority_action",
      "op" : null,
      "auth" : [
        {
          "id" : "replaced-uid-gr0wcwt",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463308327546,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-q00sqo3",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-vompdq1",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463308340538,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-t4t4pfw"
        },
        {
          "user" : {
            "id" : "replaced-uid-gt00m5g",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-8hwpyl5"
        },
        {
          "user" : {
            "id" : "replaced-uid-grwvg8t",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-4ptw6dd"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463308327546,
          "size" : 688601,
          "filename" : "HSY liitoskohtalausunto.pdf",
          "originalFileId" : "replaced-uid-nwy44xn",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-7jptcgb",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-vdn4x1t"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463308327546,
          "size" : 688601,
          "filename" : "HSY liitoskohtalausunto-PDFA.pdf",
          "originalFileId" : "replaced-uid-pj8swl8",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-0h0mi56",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-q6pga4o"
        }
      ],
      "required" : true,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463308327546,
        "size" : 688601,
        "filename" : "HSY liitoskohtalausunto-PDFA.pdf",
        "originalFileId" : "replaced-uid-5kazq6i",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-d6ybj6t",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-1jiyq5h"
      }
    },
    {
      "type" : {
        "type-group" : "selvitykset",
        "type-id" : "energiataloudellinen_selvitys"
      },
      "state" : "requires_authority_action",
      "op" : null,
      "auth" : [
        {
          "id" : "replaced-uid-x51p758",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463481286024,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-059z7hb",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-zhwzlg8",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463481301226,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-x88qlk2"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463481286024,
          "size" : 60441,
          "filename" : "Energiaselvitys.pdf",
          "originalFileId" : "replaced-uid-s63ctp3",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-1zdm1h0",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-ozj3ihv"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463481286024,
          "size" : 60441,
          "filename" : "Energiaselvitys-PDFA.pdf",
          "originalFileId" : "replaced-uid-d7uz86y",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-s8v45ug",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-udspiz7"
        }
      ],
      "required" : true,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463481286024,
        "size" : 60441,
        "filename" : "Energiaselvitys-PDFA.pdf",
        "originalFileId" : "replaced-uid-aetydbl",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-tyb45vj",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-vaoufnl"
      }
    },
    {
      "type" : {
        "type-group" : "paapiirustus",
        "type-id" : "pohjapiirustus"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-4dsxh4w"
      },
      "auth" : [
        {
          "id" : "replaced-uid-76r0g2x",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463309663120,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-q5fg3q6",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-ek1hdsc",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463309237844,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-xm9et9m"
        },
        {
          "user" : {
            "id" : "replaced-uid-vjh7qda",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-kucxrdd"
        },
        {
          "user" : {
            "id" : "replaced-uid-mdc98mg",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-57sanh7"
        }
      ],
      "forPrinting" : false,
      "contents" : "1.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463308683928,
          "size" : 287263,
          "filename" : "03 1.kerros.pdf",
          "originalFileId" : "replaced-uid-gzxwubj",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-qf8up6w",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-pti5jn8"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463308683928,
          "size" : 287263,
          "filename" : "03 1.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-ud4bbf9",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-4xuv5l4",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-32t591e"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463308683928,
        "size" : 287263,
        "filename" : "03 1.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-kndq3o0",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-7xrouov",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-4088ide"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "paapiirustus",
        "type-id" : "pohjapiirustus"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-grltums"
      },
      "auth" : [
        {
          "id" : "replaced-uid-83rglz9",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463309673127,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-5sxqknx",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-r77hem4",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463309254006,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-v6o4srv"
        },
        {
          "user" : {
            "id" : "replaced-uid-mgvjrq8",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-5xduki2"
        },
        {
          "user" : {
            "id" : "replaced-uid-lgem3vk",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-x7touhg"
        }
      ],
      "forPrinting" : false,
      "contents" : "2.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463308753876,
          "size" : 265729,
          "filename" : "04 2.kerros.pdf",
          "originalFileId" : "replaced-uid-qz1bamh",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-253j2la",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-obn86ce"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463308753876,
          "size" : 265729,
          "filename" : "04 2.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-6lyfnh6",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-e4oer0b",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-2vcte47"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463308753876,
        "size" : 265729,
        "filename" : "04 2.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-g5scr9x",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-ydpps1g",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-8lqyswo"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "paapiirustus",
        "type-id" : "pohjapiirustus"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-ayozn58"
      },
      "auth" : [
        {
          "id" : "replaced-uid-a529i4a",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463309683563,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-dvjnddj",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-3ssj59e",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463309267955,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-um315p7"
        },
        {
          "user" : {
            "id" : "replaced-uid-rjeg4m4",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-a58a8qy"
        },
        {
          "user" : {
            "id" : "replaced-uid-2jdrz1i",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-ruisxqb"
        }
      ],
      "forPrinting" : false,
      "contents" : "3.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463308800265,
          "size" : 291992,
          "filename" : "05 3.kerros.pdf",
          "originalFileId" : "replaced-uid-0zacd3g",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-m4ax7lj",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-4tllo5j"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463308800265,
          "size" : 291992,
          "filename" : "05 3.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-wxo1nq4",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-g0bqlk2",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-hb4w35f"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463308800265,
        "size" : 291992,
        "filename" : "05 3.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-f6ww31x",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-ele6vvs",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-o0zv8g1"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "paapiirustus",
        "type-id" : "pohjapiirustus"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-pqpy3fy"
      },
      "auth" : [
        {
          "id" : "replaced-uid-gp5xblo",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463309692547,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-eqvcgb8",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-vpu3brs",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463309283487,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-9fgsx4h"
        },
        {
          "user" : {
            "id" : "replaced-uid-zau69eo",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-syzep4j"
        },
        {
          "user" : {
            "id" : "replaced-uid-gu36a01",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-35gi0nj"
        }
      ],
      "forPrinting" : false,
      "contents" : "4.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463308866183,
          "size" : 266405,
          "filename" : "06 4.kerros.pdf",
          "originalFileId" : "replaced-uid-jzdmnhv",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-z3iweqq",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-p1i9ijp"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463308866183,
          "size" : 266405,
          "filename" : "06 4.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-njidd1o",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-44hyb5x",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-7swcsrb"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463308866183,
        "size" : 266405,
        "filename" : "06 4.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-338yt9c",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-7xcsaov",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-5imetpj"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "paapiirustus",
        "type-id" : "pohjapiirustus"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-ydi07ek"
      },
      "auth" : [
        {
          "id" : "replaced-uid-eoj64n7",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463309700976,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-69kw4j7",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-uxka8jn",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463309297975,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-u0t1hy5"
        },
        {
          "user" : {
            "id" : "replaced-uid-oxyws00",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-j5vkzqn"
        },
        {
          "user" : {
            "id" : "replaced-uid-czdou6k",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-vstqe8a"
        }
      ],
      "forPrinting" : false,
      "contents" : "5.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463308976373,
          "size" : 279603,
          "filename" : "07 5.kerros.pdf",
          "originalFileId" : "replaced-uid-v3nxxsb",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-tt2s3xh",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-8mlmc54"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463308976373,
          "size" : 279603,
          "filename" : "07 5.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-dt2b356",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-7ij8mr8",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-94bdulp"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463308976373,
        "size" : 279603,
        "filename" : "07 5.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-lqtqsgx",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-aunk105",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-cfqu792"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "paapiirustus",
        "type-id" : "pohjapiirustus"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-zpsmtq6"
      },
      "auth" : [
        {
          "id" : "replaced-uid-jgx4qy2",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463309711138,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-twc4qpj",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-pd6m62j",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463309322877,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-5f246hv"
        },
        {
          "user" : {
            "id" : "replaced-uid-zfv2ikh",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-5285p3d"
        },
        {
          "user" : {
            "id" : "replaced-uid-c1irfhx",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-uh5d1cu"
        }
      ],
      "forPrinting" : false,
      "contents" : "6.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463309018414,
          "size" : 290367,
          "filename" : "08 6.kerros.pdf",
          "originalFileId" : "replaced-uid-564cshz",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-i20auew",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-ewmx1be"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463309018414,
          "size" : 290367,
          "filename" : "08 6.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-1tuqgih",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-d4eu227",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-7kl7znp"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463309018414,
        "size" : 290367,
        "filename" : "08 6.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-uw3tadn",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-1vyzctb",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-mcs2bxz"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "paapiirustus",
        "type-id" : "pohjapiirustus"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-nqws10s"
      },
      "auth" : [
        {
          "id" : "replaced-uid-sw24b3i",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463309722270,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-432abn1",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-ea7gamh",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463309345411,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-bnpwcil"
        },
        {
          "user" : {
            "id" : "replaced-uid-3jgii5k",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-a6uqvxl"
        },
        {
          "user" : {
            "id" : "replaced-uid-8twihol",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-phwp7ng"
        }
      ],
      "forPrinting" : false,
      "contents" : "Ullakkokerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463309090684,
          "size" : 133009,
          "filename" : "09 ullakko.pdf",
          "originalFileId" : "replaced-uid-9gp4y3u",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-umnsqkx",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-f3jf2ka"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463309090684,
          "size" : 133009,
          "filename" : "09 ullakko-PDFA.pdf",
          "originalFileId" : "replaced-uid-ddodjg4",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-xdb222o",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-d1pm4fx"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463309090684,
        "size" : 133009,
        "filename" : "09 ullakko-PDFA.pdf",
        "originalFileId" : "replaced-uid-raeor02",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-t869fxg",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-gv36en9"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "hakija",
        "type-id" : "ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-wfglidp"
      },
      "auth" : [
        {
          "id" : "replaced-uid-6l2qje7",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463309160662,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-n21jnxi",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-3wek840",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463309200688,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-g7s3ekd"
        },
        {
          "user" : {
            "id" : "replaced-uid-69tnwi2",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-2u2nix2"
        },
        {
          "user" : {
            "id" : "replaced-uid-7wqfgzs",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-bpxrpzd"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463309160662,
          "size" : 607623,
          "filename" : "Y_YHK_PTK allekirjoitettu.pdf",
          "originalFileId" : "replaced-uid-g4bxs7l",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-19u26jl",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-0k46ti6"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463309160662,
          "size" : 607623,
          "filename" : "Y_YHK_PTK allekirjoitettu-PDFA.pdf",
          "originalFileId" : "replaced-uid-dz5bgrx",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-6bn1occ",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-qnyakox"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463309160662,
        "size" : 607623,
        "filename" : "Y_YHK_PTK allekirjoitettu-PDFA.pdf",
        "originalFileId" : "replaced-uid-1huxj86",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-d5owaz6",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-d8w7xib"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-t7sgs4t"
      },
      "auth" : [
        {
          "id" : "replaced-uid-byfr1le",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463320658905,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-jzgmp75",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-fe0x3z0",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-f0jjxj4"
        },
        {
          "user" : {
            "id" : "replaced-uid-szp9gkb",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-karbsv2"
        }
      ],
      "forPrinting" : false,
      "contents" : "LVI asemapiirustus",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320106840,
          "size" : 153969,
          "filename" : "001 (LVI-asemapiirustus).pdf",
          "originalFileId" : "replaced-uid-sr7swqo",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-si9umut",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-o3jzgot"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320106840,
          "size" : 153969,
          "filename" : "001 (LVI-asemapiirustus)-PDFA.pdf",
          "originalFileId" : "replaced-uid-3mz5rrw",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-kze1u2j",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-j5qpbc9"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320106840,
        "size" : 153969,
        "filename" : "001 (LVI-asemapiirustus)-PDFA.pdf",
        "originalFileId" : "replaced-uid-5z90vyi",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-nvrnivc",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-sk8xfzo"
      },
      "scale" : "1:500"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-qzdj39t"
      },
      "auth" : [
        {
          "id" : "replaced-uid-ntyfqrd",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463320668998,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-6h21wzo",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-e7szur6",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-mo0x38t"
        },
        {
          "user" : {
            "id" : "replaced-uid-qe7hq5l",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-ksjmlff"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 1, Kellarikerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320139929,
          "size" : 265669,
          "filename" : "200.1 (Kellarikerros).pdf",
          "originalFileId" : "replaced-uid-o4d59hf",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-zx5yxf6",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-4fhacpf"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320139929,
          "size" : 265669,
          "filename" : "200.1 (Kellarikerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-69vhbjh",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-jtqn9b6",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-kpwfhgb"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320139929,
        "size" : 265669,
        "filename" : "200.1 (Kellarikerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-ebze7ey",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-r03f0ud",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-dop7h4h"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-mnz900e"
      },
      "auth" : [
        {
          "id" : "replaced-uid-oh8rzeh",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463320676484,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-gobbs71",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-mwehyq3",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-04odjpj"
        },
        {
          "user" : {
            "id" : "replaced-uid-mfunnmn",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-c8wcdmc"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 1, 1.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320249710,
          "size" : 319714,
          "filename" : "201.1 (1.kerros).pdf",
          "originalFileId" : "replaced-uid-t30coml",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-l9lul19",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-he1ydrk"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320249710,
          "size" : 319714,
          "filename" : "201.1 (1.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-ivkn16m",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-dx3ptbw",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-qazlp1m"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320249710,
        "size" : 319714,
        "filename" : "201.1 (1.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-1e7yad0",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-6kgrn7a",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-9w0hf35"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-am5sz9t"
      },
      "auth" : [
        {
          "id" : "replaced-uid-84t2zfr",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463320682239,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-ek0baxk",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-k8mqjqo",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-vcbfab5"
        },
        {
          "user" : {
            "id" : "replaced-uid-te10nhl",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-rmgwniq"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 1, 2.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320279692,
          "size" : 311860,
          "filename" : "202.1 (2.kerros).pdf",
          "originalFileId" : "replaced-uid-iqjkv1k",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-nyuddq5",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-2ikctbv"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320279692,
          "size" : 311860,
          "filename" : "202.1 (2.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-k0zvd10",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-qwf9xj5",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-biapwbk"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320279692,
        "size" : 311860,
        "filename" : "202.1 (2.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-5lclyeq",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-omk0e7z",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-80we4n7"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-n59an2a"
      },
      "auth" : [
        {
          "id" : "replaced-uid-x8yx4o0",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463320688869,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-9ks6oks",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-f76y659",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-jkfafiv"
        },
        {
          "user" : {
            "id" : "replaced-uid-68howf2",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-twnof0w"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 1, 3.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320320946,
          "size" : 344202,
          "filename" : "203.1 (3.kerros).pdf",
          "originalFileId" : "replaced-uid-32704wj",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-fvy55w4",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-ujwf5l2"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320320946,
          "size" : 344202,
          "filename" : "203.1 (3.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-i17xi45",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-go05db8",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-gemsd6q"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320320946,
        "size" : 344202,
        "filename" : "203.1 (3.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-p49zptb",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-ly85pou",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-6mhzz36"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-9mngsqa"
      },
      "auth" : [
        {
          "id" : "replaced-uid-25rt2n5",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463320694953,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-kfyvnml",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-6c1kjfa",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-mt0ibux"
        },
        {
          "user" : {
            "id" : "replaced-uid-rzwfom8",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-2chnbf6"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 1, 4.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320374602,
          "size" : 327810,
          "filename" : "204.1 (4.kerros).pdf",
          "originalFileId" : "replaced-uid-2n93hfa",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-h1oi6os",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-6muhrpg"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320374602,
          "size" : 327810,
          "filename" : "204.1 (4.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-871qvpg",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-getklev",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-5j27osg"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320374602,
        "size" : 327810,
        "filename" : "204.1 (4.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-avik280",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-6ezwvtx",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-ilk9up2"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-wr9exw9"
      },
      "auth" : [
        {
          "id" : "replaced-uid-iq5pkb4",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463320700648,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-6go33k5",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-c0k5x52",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-zvg257i"
        },
        {
          "user" : {
            "id" : "replaced-uid-f4fts5m",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-502q6ug"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 1, 5.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320419436,
          "size" : 327471,
          "filename" : "205.1 (5.kerros).pdf",
          "originalFileId" : "replaced-uid-5almnt0",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-hh404zo",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-r2bix3s"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320419436,
          "size" : 327471,
          "filename" : "205.1 (5.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-lmdbhli",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-g28q8c9",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-z2afoxw"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320419436,
        "size" : 327471,
        "filename" : "205.1 (5.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-kzm0ado",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-2vgs5eb",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-zd33awm"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-u7e1ine"
      },
      "auth" : [
        {
          "id" : "replaced-uid-nkw16jm",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463320706312,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-g2hz1uz",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-k1wrau7",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-3n3xl0w"
        },
        {
          "user" : {
            "id" : "replaced-uid-6q11e8e",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-7yhl1vh"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 1, 6.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320450753,
          "size" : 316125,
          "filename" : "206.1 (6.kerros).pdf",
          "originalFileId" : "replaced-uid-xg1evum",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-764yns2",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-ghtys2x"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320450753,
          "size" : 316125,
          "filename" : "206.1 (6.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-5c09knz",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-3oudkhm",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-w7htp7i"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320450753,
        "size" : 316125,
        "filename" : "206.1 (6.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-5ic0l2h",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-uyjvnls",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-5he1ja7"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-9gri8hx"
      },
      "auth" : [
        {
          "id" : "replaced-uid-qi8pf63",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463320712102,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-evhqdpk",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-a53u72l",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463320788158,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-lzu6nvg"
        },
        {
          "user" : {
            "id" : "replaced-uid-iq8wkqe",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-11y39w4"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 1, Ullakkokerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320487001,
          "size" : 165947,
          "filename" : "207.1 (Ullakkokerros).pdf",
          "originalFileId" : "replaced-uid-sy5ni7x",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-do4eya5",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-o7soriv"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320487001,
          "size" : 165947,
          "filename" : "207.1 (Ullakkokerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-e82gcwv",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-grs6y02",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-noqaysu"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320487001,
        "size" : 165947,
        "filename" : "207.1 (Ullakkokerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-5y1ld3h",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-d24volo",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-zpthnp0"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-15mxcza"
      },
      "auth" : [
        {
          "id" : "replaced-uid-rujunnc",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321812921,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-3zwq2i5",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-tl15ykr",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-8044r5s"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 2, Kellarikerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320845022,
          "size" : 304700,
          "filename" : "200.2 (Kellarikerros).pdf",
          "originalFileId" : "replaced-uid-gcofd2k",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-p00wool",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-d82gyuw"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320845022,
          "size" : 304700,
          "filename" : "200.2 (Kellarikerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-8qkc3j1",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-anwjvge",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-4153aiw"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320845022,
        "size" : 304700,
        "filename" : "200.2 (Kellarikerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-omd0liz",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-e87sy9i",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-lpcq23z"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-5ybzz1d"
      },
      "auth" : [
        {
          "id" : "replaced-uid-8rh2nnl",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321901442,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-z8vh9ih",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-pc00l71",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-hp5iix6"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 2, 1.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320878439,
          "size" : 374581,
          "filename" : "201.2 (1.kerros).pdf",
          "originalFileId" : "replaced-uid-zhw89fk",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-p6523xi",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-7a0l7r6"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320878439,
          "size" : 374581,
          "filename" : "201.2 (1.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-b6ppvr4",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-nzt1oth",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-8w3rfxv"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320878439,
        "size" : 374581,
        "filename" : "201.2 (1.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-boq6cv4",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-j2p73id",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-p5v8jg2"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-ny2583m"
      },
      "auth" : [
        {
          "id" : "replaced-uid-nfrjh10",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321893306,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-nctgnr8",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-ye57tyd",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-h3i8jzz"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 2, 2.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320922516,
          "size" : 324570,
          "filename" : "202.2 (2.kerros).pdf",
          "originalFileId" : "replaced-uid-poxau0p",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-bifbjgn",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-jzxbe1u"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320922516,
          "size" : 324570,
          "filename" : "202.2 (2.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-cn97shm",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-4dvkmv2",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-8qp3bwj"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320922516,
        "size" : 324570,
        "filename" : "202.2 (2.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-78pjja8",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-9b07rdg",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-3wik7ao"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-m6fs8si"
      },
      "auth" : [
        {
          "id" : "replaced-uid-7wofvdu",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321886285,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-3ingwbv",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-rfyjkn4",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-wxfb7mc"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 2, 3.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320960504,
          "size" : 362687,
          "filename" : "203.2 (3.kerros).pdf",
          "originalFileId" : "replaced-uid-efzyye8",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-q0koon7",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-dblb49z"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320960504,
          "size" : 362687,
          "filename" : "203.2 (3.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-c002joy",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-m7qfrng",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-21jdnau"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320960504,
        "size" : 362687,
        "filename" : "203.2 (3.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-xelflii",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-x61z9hd",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-la5aac7"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-92rnpxr"
      },
      "auth" : [
        {
          "id" : "replaced-uid-m2mz1r7",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321876797,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-vly1uv8",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-e607540",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-7zajr8q"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 2, 4.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463320996040,
          "size" : 330274,
          "filename" : "204.2 (4.kerros).pdf",
          "originalFileId" : "replaced-uid-23fiunq",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-6oiq2j9",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-dlbv8f2"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463320996040,
          "size" : 330274,
          "filename" : "204.2 (4.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-q2a2umv",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-4m3wvjy",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-284pwau"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463320996040,
        "size" : 330274,
        "filename" : "204.2 (4.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-qm42fkf",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-wx3igvi",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-nurb6js"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-mjpvk29"
      },
      "auth" : [
        {
          "id" : "replaced-uid-qfxyla5",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321925565,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-rlqi3zl",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-g3hgmbx",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-20u3bye"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 2, 5.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321077334,
          "size" : 338616,
          "filename" : "205.2 (5.kerros).pdf",
          "originalFileId" : "replaced-uid-xmxay2z",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-9w0xa1i",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-y09ifas"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321077334,
          "size" : 338616,
          "filename" : "205.2 (5.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-4vx8ldo",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-uiy7ta9",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-b33oxtn"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321077334,
        "size" : 338616,
        "filename" : "205.2 (5.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-gr9dzi6",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-p0gvseg",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-mdi37ed"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-9h8vjfp"
      },
      "auth" : [
        {
          "id" : "replaced-uid-0m0v1ed",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321945305,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-vdc6b35",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-icewk3m",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-fn4hz2g"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 2, 6.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321107536,
          "size" : 333104,
          "filename" : "206.2 (6.kerros).pdf",
          "originalFileId" : "replaced-uid-vlr58my",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-63kfzvh",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-rvqypdt"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321107536,
          "size" : 333104,
          "filename" : "206.2 (6.kerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-agbdaiz",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-ty6aewb",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-u6vzjc2"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321107536,
        "size" : 333104,
        "filename" : "206.2 (6.kerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-xx07x08",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-yf63w9i",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-ahf5f3l"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-uywaub1"
      },
      "auth" : [
        {
          "id" : "replaced-uid-t7zu0us",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321964567,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-r8onggh",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-zyzbhcx",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-8zyuoji"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 2, Ullakkokerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321141617,
          "size" : 175161,
          "filename" : "207.2 (Ullakkokerros).pdf",
          "originalFileId" : "replaced-uid-bzic9hu",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-amf7u4v",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-zrv0sqj"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321141617,
          "size" : 175161,
          "filename" : "207.2 (Ullakkokerros)-PDFA.pdf",
          "originalFileId" : "replaced-uid-shpcsop",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-eb89aso",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-qieh4yr"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321141617,
        "size" : 175161,
        "filename" : "207.2 (Ullakkokerros)-PDFA.pdf",
        "originalFileId" : "replaced-uid-dck0ljo",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-w4zm42x",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-9lwhyle"
      },
      "scale" : "1:50"
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-4pqj7l9"
      },
      "auth" : [
        {
          "id" : "replaced-uid-i2kcpdo",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321981727,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-ry1d9a7",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-cfztwva",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-u3ws173"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 1, Linjakaavio",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321170910,
          "size" : 624613,
          "filename" : "208.1 (Linjakaavio).pdf",
          "originalFileId" : "replaced-uid-iayd5uz",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-oietlzc",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-djhlpua"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321170910,
          "size" : 624613,
          "filename" : "208.1 (Linjakaavio)-PDFA.pdf",
          "originalFileId" : "replaced-uid-vu5y9f8",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-oax09oo",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-w6yv37s"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321170910,
        "size" : 624613,
        "filename" : "208.1 (Linjakaavio)-PDFA.pdf",
        "originalFileId" : "replaced-uid-ugw1e2n",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-n6utyhn",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-89mszqq"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-41s009q"
      },
      "auth" : [
        {
          "id" : "replaced-uid-273npoj",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321998362,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-9skliw3",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-k5a81lc",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-aja77k5"
        }
      ],
      "forPrinting" : false,
      "contents" : "RAK 2, Linjakaavio",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321200316,
          "size" : 624200,
          "filename" : "208.2 (Linjakaavio).pdf",
          "originalFileId" : "replaced-uid-jlsyrwl",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-so6r3h0",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-zaenhj9"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321200316,
          "size" : 624200,
          "filename" : "208.2 (Linjakaavio)-PDFA.pdf",
          "originalFileId" : "replaced-uid-0bz77bg",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-bi5knv0",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-y2op5sj"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321200316,
        "size" : 624200,
        "filename" : "208.2 (Linjakaavio)-PDFA.pdf",
        "originalFileId" : "replaced-uid-tdvxo0m",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-1tlae51",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-vikncds"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-xiwndkk"
      },
      "auth" : [
        {
          "id" : "replaced-uid-dr6509l",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463322013733,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-lq9uju2",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-9n84egq",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-lylghzc"
        }
      ],
      "forPrinting" : false,
      "contents" : "Kalusteloettelo",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321230886,
          "size" : 156141,
          "filename" : "209 (Kalusteloettelo).pdf",
          "originalFileId" : "replaced-uid-2995acy",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-kqu0xug",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-i737z7o"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321230886,
          "size" : 156141,
          "filename" : "209 (Kalusteloettelo)-PDFA.pdf",
          "originalFileId" : "replaced-uid-oiy6n4r",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-6omai1u",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-ela8328"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321230886,
        "size" : 156141,
        "filename" : "209 (Kalusteloettelo)-PDFA.pdf",
        "originalFileId" : "replaced-uid-2jer845",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-9krh5kq",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-h39cflk"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-j1z7av6"
      },
      "auth" : [
        {
          "id" : "replaced-uid-4o726q4",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463322042766,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-f1s7m88",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-jjijtwd",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-85wcjzu"
        }
      ],
      "forPrinting" : false,
      "contents" : "Pinta-asennushanakulma kytkentäperiaate",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321265556,
          "size" : 121934,
          "filename" : "210 (Pinta-asennushanakulma kytkentäperiaate).pdf",
          "originalFileId" : "replaced-uid-u4sj5qr",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-bqmfvj2",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-ghx25is"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321265556,
          "size" : 121934,
          "filename" : "210 (Pinta-asennushanakulma kytkentäperiaate)-PDFA.pdf",
          "originalFileId" : "replaced-uid-ltbmohq",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-3yu4p4q",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-y943r0z"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321265556,
        "size" : 121934,
        "filename" : "210 (Pinta-asennushanakulma kytkentäperiaate)-PDFA.pdf",
        "originalFileId" : "replaced-uid-vsefdjg",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-m6hs9s9",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-6goyhlc"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "kvv_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-wzptjiy"
      },
      "auth" : [
        {
          "id" : "replaced-uid-1k2wdey",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463322075463,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-o9jbwpi",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-nvlvi7f",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-6p7hxyi"
        }
      ],
      "forPrinting" : false,
      "contents" : "Kylpyhuoneen kalusteiden kytkentäesimerkki",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321296010,
          "size" : 148563,
          "filename" : "211 (Kylpyhuoneen kalusteiden kytkentäesimerkki).pdf",
          "originalFileId" : "replaced-uid-a6o77jh",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-ruxmhh6",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-jzzck60"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321296010,
          "size" : 148563,
          "filename" : "211 (Kylpyhuoneen kalusteiden kytkentäesimerkki)-PDFA.pdf",
          "originalFileId" : "replaced-uid-744hx0m",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-6h6gaiv",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-kqsxa8q"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321296010,
        "size" : 148563,
        "filename" : "211 (Kylpyhuoneen kalusteiden kytkentäesimerkki)-PDFA.pdf",
        "originalFileId" : "replaced-uid-4me3r5l",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-pnigq00",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-dyn5tj9"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "rakennesuunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-23459ha"
      },
      "auth" : [
        {
          "id" : "replaced-uid-jtinu77",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321354038,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-dkfdgcx",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-wldl90s",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-74mux3n"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321354038,
          "size" : 138264,
          "filename" : "RAK-520_Agrikolankatu7_DET 520.pdf",
          "originalFileId" : "replaced-uid-ihvsfyb",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-lh71v6p",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-lpamh1m"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321354038,
          "size" : 138264,
          "filename" : "RAK-520_Agrikolankatu7_DET 520-PDFA.pdf",
          "originalFileId" : "replaced-uid-jeu0e41",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-yq58ve4",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-zi1oyfp"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321354038,
        "size" : 138264,
        "filename" : "RAK-520_Agrikolankatu7_DET 520-PDFA.pdf",
        "originalFileId" : "replaced-uid-lwcystr",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-c1bb6nt",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-z8v96wc"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "rakennesuunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-d0dqwmi"
      },
      "auth" : [
        {
          "id" : "replaced-uid-41m2nzp",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321388386,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-3ut4pn7",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-b5ozwsj",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-rmrlk9z"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321388386,
          "size" : 387231,
          "filename" : "RAK-521_Agrikolankatu7_DET 521.pdf",
          "originalFileId" : "replaced-uid-m4hvpi9",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-zvqmid3",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-aagde0b"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321388386,
          "size" : 387231,
          "filename" : "RAK-521_Agrikolankatu7_DET 521-PDFA.pdf",
          "originalFileId" : "replaced-uid-81oop51",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-5ppav22",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-o3q4xtr"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321388386,
        "size" : 387231,
        "filename" : "RAK-521_Agrikolankatu7_DET 521-PDFA.pdf",
        "originalFileId" : "replaced-uid-im0bgmg",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-zxjuu16",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-yz00fw7"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "rakennesuunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-fejamed"
      },
      "auth" : [
        {
          "id" : "replaced-uid-0qj4lr8",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321418883,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-miveunp",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-90kssvy",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-umgnw02"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321418883,
          "size" : 86171,
          "filename" : "RAK-522_Agrikolankatu7_DET 522.pdf",
          "originalFileId" : "replaced-uid-rk0ai9k",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-uuyz6df",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-inkumqz"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321418883,
          "size" : 86171,
          "filename" : "RAK-522_Agrikolankatu7_DET 522-PDFA.pdf",
          "originalFileId" : "replaced-uid-8vr61fo",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-6229ymq",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-05dy4pi"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321418883,
        "size" : 86171,
        "filename" : "RAK-522_Agrikolankatu7_DET 522-PDFA.pdf",
        "originalFileId" : "replaced-uid-htkkyx2",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-z3vnesi",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-79fqp1t"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "rakennesuunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-08q4prb"
      },
      "auth" : [
        {
          "id" : "replaced-uid-3hph1nj",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321486414,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-9sp5g6f",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-nv1atcg",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-ehs2hh6"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321486414,
          "size" : 377572,
          "filename" : "RAK-523_Agrikolankatu7_DET 523.pdf",
          "originalFileId" : "replaced-uid-5ckkjw2",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-f6izmna",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-ca192bt"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321486414,
          "size" : 377572,
          "filename" : "RAK-523_Agrikolankatu7_DET 523-PDFA.pdf",
          "originalFileId" : "replaced-uid-zfqt036",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-gi4p68l",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-ctzalor"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321486414,
        "size" : 377572,
        "filename" : "RAK-523_Agrikolankatu7_DET 523-PDFA.pdf",
        "originalFileId" : "replaced-uid-18xfgmr",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-8g6kb69",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-p5przwu"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "rakennesuunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-gff3f9g"
      },
      "auth" : [
        {
          "id" : "replaced-uid-euqx1h3",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321515289,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-0hiusga",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-7qdrsnd",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-uabml2d"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321515289,
          "size" : 283967,
          "filename" : "RAK-524_Agrikolankatu7_DET 524.pdf",
          "originalFileId" : "replaced-uid-je7lv4f",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-mxqv50g",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-jshctsj"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321515289,
          "size" : 283967,
          "filename" : "RAK-524_Agrikolankatu7_DET 524-PDFA.pdf",
          "originalFileId" : "replaced-uid-i905app",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-qbbkzsz",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-aog8wf4"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321515289,
        "size" : 283967,
        "filename" : "RAK-524_Agrikolankatu7_DET 524-PDFA.pdf",
        "originalFileId" : "replaced-uid-0n7vi53",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-qzcdo36",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-umjq0fg"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "rakennesuunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-bl2sypu"
      },
      "auth" : [
        {
          "id" : "replaced-uid-csagqug",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321547377,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-5y4ccpq",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-yu9u975",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-izyp7nb"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321547377,
          "size" : 100868,
          "filename" : "RAK-525_Agrikolankatu7_DET 525.pdf",
          "originalFileId" : "replaced-uid-0d8h8mw",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-2m040tc",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-m537y5n"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321547377,
          "size" : 100868,
          "filename" : "RAK-525_Agrikolankatu7_DET 525-PDFA.pdf",
          "originalFileId" : "replaced-uid-vkx0iu0",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-0uba2g5",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-w2vuio7"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321547377,
        "size" : 100868,
        "filename" : "RAK-525_Agrikolankatu7_DET 525-PDFA.pdf",
        "originalFileId" : "replaced-uid-go5rto3",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-wymduhf",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-yrk8g1f"
      }
    },
    {
      "type" : {
        "type-group" : "erityissuunnitelmat",
        "type-id" : "rakennesuunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-5u5p6f3"
      },
      "auth" : [
        {
          "id" : "replaced-uid-uqq6jdc",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463321587553,
      "requestedByAuthority" : false,
      "applicationState" : "draft",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-pkvq5mo",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-3lkx6vx",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-bvvfusa"
        }
      ],
      "forPrinting" : false,
      "contents" : null,
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463321587553,
          "size" : 244496,
          "filename" : "RAK-526_Agrikolankatu7_RAKTYYPIT.pdf",
          "originalFileId" : "replaced-uid-h875lbe",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-zp6agfl",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-hvx2tsf"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463321587553,
          "size" : 244496,
          "filename" : "RAK-526_Agrikolankatu7_RAKTYYPIT-PDFA.pdf",
          "originalFileId" : "replaced-uid-4dsmzyh",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-jfwt5mn",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-0jf3f1b"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463321587553,
        "size" : 244496,
        "filename" : "RAK-526_Agrikolankatu7_RAKTYYPIT-PDFA.pdf",
        "originalFileId" : "replaced-uid-znyq094",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-rwboglv",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-fycmjgc"
      }
    },
    {
      "type" : {
        "type-group" : "suunnitelmat",
        "type-id" : "muu_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-hkvp53n"
      },
      "auth" : [
        {
          "id" : "replaced-uid-el7vjrs",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463329171495,
      "requestedByAuthority" : false,
      "applicationState" : "submitted",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-hmmcfc1",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-awzl0gm",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-3qah7gx"
        }
      ],
      "forPrinting" : false,
      "contents" : "Palokatkosuunnitelma, Kellarikerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463328739803,
          "size" : 224957,
          "filename" : "600 Kellarikerros.pdf",
          "originalFileId" : "replaced-uid-36jkafs",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-9etnhy4",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-wuib6vr"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463328739803,
          "size" : 224957,
          "filename" : "600 Kellarikerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-ieikx85",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-z4qudxc",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-x2i8xf4"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463328739803,
        "size" : 224957,
        "filename" : "600 Kellarikerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-yaw07q3",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-9jyrl5n",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-et6dzqp"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "suunnitelmat",
        "type-id" : "muu_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-e06f9iq"
      },
      "auth" : [
        {
          "id" : "replaced-uid-n5lfmve",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463329200257,
      "requestedByAuthority" : false,
      "applicationState" : "submitted",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-sxw2nct",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-g25vvoj",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-zuwmmrz"
        }
      ],
      "forPrinting" : false,
      "contents" : "Palokatkosuunnitelma, 1.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463328853850,
          "size" : 321487,
          "filename" : "601 1.kerros.pdf",
          "originalFileId" : "replaced-uid-z6vs582",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-rqmw536",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-osk4gmt"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463328853850,
          "size" : 321487,
          "filename" : "601 1.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-p6q3wkk",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-x23eay0",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-4ximra6"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463328853850,
        "size" : 321487,
        "filename" : "601 1.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-ce4dm5m",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-7vqk5bq",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-ddmws9e"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "suunnitelmat",
        "type-id" : "muu_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-vdq0byp"
      },
      "auth" : [
        {
          "id" : "replaced-uid-6guycv1",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463329219666,
      "requestedByAuthority" : false,
      "applicationState" : "submitted",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-ru8rbwm",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-g2wsqcn",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-8deir8i"
        }
      ],
      "forPrinting" : false,
      "contents" : "Palokatkosuunnitelma, 2.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463328894188,
          "size" : 300104,
          "filename" : "602 2.kerros.pdf",
          "originalFileId" : "replaced-uid-a9c1tsv",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-0qv6tt7",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-ptoz6il"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463328894188,
          "size" : 300104,
          "filename" : "602 2.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-2yjvq6u",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-2s2m98g",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-wsrdfcd"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463328894188,
        "size" : 300104,
        "filename" : "602 2.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-sxg1l92",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-6301t4x",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-k4lm0se"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "suunnitelmat",
        "type-id" : "muu_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-mec5m06"
      },
      "auth" : [
        {
          "id" : "replaced-uid-zam2d84",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463329233326,
      "requestedByAuthority" : false,
      "applicationState" : "submitted",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-qedb49j",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-tg5hh4w",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-6m8c1wy"
        }
      ],
      "forPrinting" : false,
      "contents" : "Palokatkosuunnitelma, 3.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463328927115,
          "size" : 326768,
          "filename" : "603 3.kerros.pdf",
          "originalFileId" : "replaced-uid-srfkg1r",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-yr2jmbc",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-ttgb7y9"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463328927115,
          "size" : 326768,
          "filename" : "603 3.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-ct1rxkg",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-4cs4mpf",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-80aye0o"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463328927115,
        "size" : 326768,
        "filename" : "603 3.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-gxz7kio",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-58lc2kp",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-35ralhk"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "suunnitelmat",
        "type-id" : "muu_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-s2gomvk"
      },
      "auth" : [
        {
          "id" : "replaced-uid-s1oanf9",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463329246646,
      "requestedByAuthority" : false,
      "applicationState" : "submitted",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-xj1ilxv",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-mol8v71",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-yep66iz"
        }
      ],
      "forPrinting" : false,
      "contents" : "Palokatkosuunnitelma, 4.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463328960810,
          "size" : 301430,
          "filename" : "604 4.kerros.pdf",
          "originalFileId" : "replaced-uid-ohc2lxj",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-unenj53",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-n45e810"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463328960810,
          "size" : 301430,
          "filename" : "604 4.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-xow7nf1",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-rsssfp2",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-4hkeb95"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463328960810,
        "size" : 301430,
        "filename" : "604 4.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-oplipck",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-6lcuqxj",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-5nz2bxf"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "suunnitelmat",
        "type-id" : "muu_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-8nryjkm"
      },
      "auth" : [
        {
          "id" : "replaced-uid-6l8on5j",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463329260138,
      "requestedByAuthority" : false,
      "applicationState" : "submitted",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-c18cnyw",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-e1n18f8",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-yal6xbe"
        }
      ],
      "forPrinting" : false,
      "contents" : "Palokatkosuunnitelma, 5.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463329000323,
          "size" : 312418,
          "filename" : "605 5.kerros.pdf",
          "originalFileId" : "replaced-uid-jovo11c",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-9hjnt5f",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-sost7b3"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463329000323,
          "size" : 312418,
          "filename" : "605 5.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-9ouso7a",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-c5rk7q6",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-036eff2"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463329000323,
        "size" : 312418,
        "filename" : "605 5.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-180edlp",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-z7rfoya",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-jh7blo5"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "suunnitelmat",
        "type-id" : "muu_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-cb1mgw1"
      },
      "auth" : [
        {
          "id" : "replaced-uid-2roguyh",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463329274255,
      "requestedByAuthority" : false,
      "applicationState" : "submitted",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-ersf1xl",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-kxvrny9",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-7ovu5pa"
        }
      ],
      "forPrinting" : false,
      "contents" : "Palokatkosuunnitelma, 6.kerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463329058298,
          "size" : 314766,
          "filename" : "606 6.kerros.pdf",
          "originalFileId" : "replaced-uid-j553xi2",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-juvs71u",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-n4aqeq5"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463329058298,
          "size" : 314766,
          "filename" : "606 6.kerros-PDFA.pdf",
          "originalFileId" : "replaced-uid-vc4935p",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-a9ouaqu",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-o32phbh"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463329058298,
        "size" : 314766,
        "filename" : "606 6.kerros-PDFA.pdf",
        "originalFileId" : "replaced-uid-8lwnmie",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-xo6ovc5",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-f9isnyk"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "suunnitelmat",
        "type-id" : "muu_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-e99rxix"
      },
      "auth" : [
        {
          "id" : "replaced-uid-etele7c",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463329296696,
      "requestedByAuthority" : false,
      "applicationState" : "submitted",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-83sg1p4",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-qa1ohvw",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-zvvhybh"
        }
      ],
      "forPrinting" : false,
      "contents" : "Palokatkosuunnitelma, Ullakkokerros",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463329104773,
          "size" : 167798,
          "filename" : "607 Ullakko.pdf",
          "originalFileId" : "replaced-uid-t1q2j00",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-gg1tnnq",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-q64rhbz"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463329104773,
          "size" : 167798,
          "filename" : "607 Ullakko-PDFA.pdf",
          "originalFileId" : "replaced-uid-1ub9igb",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-xr24g8u",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-4kk2244"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463329104773,
        "size" : 167798,
        "filename" : "607 Ullakko-PDFA.pdf",
        "originalFileId" : "replaced-uid-qnilw5x",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-b74epyc",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-vystnfh"
      },
      "scale" : "1:100"
    },
    {
      "type" : {
        "type-group" : "suunnitelmat",
        "type-id" : "muu_suunnitelma"
      },
      "state" : "requires_authority_action",
      "op" : {
        "id" : "replaced-uid-car6vig"
      },
      "auth" : [
        {
          "id" : "replaced-uid-5k1eeok",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "uploader"
        }
      ],
      "modified" : 1463329309352,
      "requestedByAuthority" : false,
      "applicationState" : "submitted",
      "readOnly" : false,
      "locked" : false,
      "id" : "replaced-uid-jqvcnpa",
      "notNeeded" : false,
      "signatures" : [
        {
          "user" : {
            "id" : "replaced-uid-35cnbwg",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "created" : 1463329353595,
          "version" : {
            "minor" : 0,
            "major" : 2
          },
          "fileId" : "replaced-uid-cykmxg5"
        }
      ],
      "forPrinting" : false,
      "contents" : "Palokatkosuunnitelma",
      "target" : null,
      "versions" : [
        {
          "missing-fonts" : [

          ],
          "created" : 1463329148356,
          "size" : 57567,
          "filename" : "A3 Palokatkosuunnitelma.pdf",
          "originalFileId" : "replaced-uid-qif2qw0",
          "archivabilityError" : "invalid-mime-type",
          "contentType" : "application/pdf",
          "archivable" : false,
          "version" : {
            "major" : 1,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-axzwlgi",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-kxi8to0"
        },
        {
          "missing-fonts" : [

          ],
          "created" : 1463329148356,
          "size" : 57567,
          "filename" : "A3 Palokatkosuunnitelma-PDFA.pdf",
          "originalFileId" : "replaced-uid-w09ukk6",
          "contentType" : "application/pdf",
          "archivable" : true,
          "version" : {
            "major" : 2,
            "minor" : 0
          },
          "stamped" : false,
          "user" : {
            "id" : "replaced-uid-9r8f2gt",
            "username" : "erkki.esimerkki@example.com",
            "firstName" : "Erkki",
            "lastName" : "Esimerkki",
            "role" : "applicant"
          },
          "fileId" : "replaced-uid-1w58o0b"
        }
      ],
      "metadata" : {
        "nakyvyys" : "julkinen"
      },
      "required" : false,
      "latestVersion" : {
        "missing-fonts" : [

        ],
        "created" : 1463329148356,
        "size" : 57567,
        "filename" : "A3 Palokatkosuunnitelma-PDFA.pdf",
        "originalFileId" : "replaced-uid-rc0dkqo",
        "contentType" : "application/pdf",
        "archivable" : true,
        "version" : {
          "major" : 2,
          "minor" : 0
        },
        "stamped" : false,
        "user" : {
          "id" : "replaced-uid-j6neqy7",
          "username" : "erkki.esimerkki@example.com",
          "firstName" : "Erkki",
          "lastName" : "Esimerkki",
          "role" : "applicant"
        },
        "fileId" : "replaced-uid-iq83vfe"
      }
    }
  ];

  //
  // Filter manipulation
  //

  // returns an observable
  self.getFilter = function(filterId) {
    return filters[filterId];
  };

  self.setFilter = function(filterId, isFilterEnabled) {
    filters[filterId](isFilterEnabled);
    return filters[filterId]();
  };
  self.disableAllFilters = function() {
    _.forEach(_.values(filters), function(filter) {
      filter(false);
    });
  };


  //
  // Attachments
  //

  function createAttachmentModel(attachment) {
    return ko.observable(attachment);
  }

  self.attachments = ko.observableArray([]);
  self.setAttachments = function(attachments) {
    self.attachments(_.map(attachments, createAttachmentModel));
  };
  self.setAttachments(dummyData);

  self.getAttachment = function(attachmentId) {
    return _.find(self.attachments(), function(attachment) {
      return attachment.peek().id === attachmentId;
    });
  };
  self.removeAttachment = function(attachmentId) {
    self.attachments.remove(function(attachment) {
      return attachment().id === attachmentId;
    });
  };

  self.updateAttachment = function(attachmentId, updates) {
    var oldAttachment = self.getAttachment(attachmentId);
    if (oldAttachment) {
      self.getAttachment(attachmentId)(_.merge(oldAttachment(), updates));
    }
  };

  // Approving and rejecting attachments
  self.approveAttachment = function(attachmentId) {
    self.updateAttachment(attachmentId, {state: self.APPROVED});
  };
  self.rejectAttachment = function(attachmentId) {
    self.updateAttachment(attachmentId, {state: self.REJECTED});
  };

  //helpers for checking relevant attachment states
  self.isApproved = function(attachment) {
    return attachment && attachment.state === self.APPROVED;
  };
  self.isRejected = function(attachment) {
    return attachment && attachment.state === self.REJECTED;
  };
  self.isNotNeeded = function(attachment) {
    return attachment && attachment.notNeeded;
  };

  function getAttachmentValue(attachmentId) {
    var attachment = self.getAttachment(attachmentId);
    if (attachment) {
      return attachment();
    } else {
      return null;
    }
  }

  // returns a function for use in computed
  self.attachmentsStatus = function(attachmentIds) {
    return function() {
      if (_.every(_.map(attachmentIds, getAttachmentValue),
                  self.isApproved)) {
        return self.APPROVED;
      } else {
        return _.some(_.map(attachmentIds, getAttachmentValue),
                      self.isRejected) ? self.REJECTED : null;
      }
    };
  };


  //
  // Attachment hierarchy
  //

  var preVerdictStates = [
    "draft", "info", "answered", "open", "submitted", "complementNeeded", "sent"
  ];

  function isPreVerdict(attachment) {
    return _.includes(preVerdictStates, attachment.applicationState);
  }

  function isPostVerdict(attachment) {
    return !isPreVerdict(attachment);
  }

  function getVerdictGroup(attachment) {
    if (isPreVerdict(attachment)) {
      return "preVerdict";
    } else {
      return "postVerdict";
    }
  }

  function getMainGroup(attachment) {
    // Dummy implementation for dummy data
    switch (attachment.type["type-group"]) {
    case "erityissuunnitelmat":     return "erityissuunnitelmat";
    case "osapuolet":               return "osapuolet";
    case "rakennuspaikan_hallinta":
    case "rakennuspaikka":
                                    return "rakennuspaikka";
    default:                        return "yleiset";
    }
  }

  function getSubGroup(attachment) {
    // Dummy implementation for dummy data
    var mainGroup = getMainGroup(attachment);
    if (mainGroup === "erityissuunnitelmat") {
      return attachment.type["type-id"];
    } else {
      return "no-sub-group";
    }
  }

  function isTypeId(typeId) {
    return function(attachment) {
      return attachment.type["type-id"] === typeId;
    };
  }

  function isTypeGroup(typeGroup) {
    return function(attachment) {
      return attachment.type["type-group"] === typeGroup;
    };
  }

  var filterFunctions = {
    preVerdict: isPreVerdict,
    postVerdict: isPostVerdict,
    notNeeded: self.isNotNeeded,
    ivSuunnitelmat: isTypeId("iv_suunnitelma"),
    kvvSuunnitelmat: isTypeId("kvv_suunnitelma"),
    rakennesuunnitelmat: isTypeId("rakennesuunnitelma"),
    paapiirrustukset: isTypeGroup("paapiirrustus")
  };

  function showAll() {
    var filterValues = _.mapValues(filters, function(f) { return f(); });
    return _(filterValues).omit("notNeeded").values()
           .every(function(f) { return !f; });
  }

  function preVerdictAttachments(attachments) {
    var subFilters = [
      "ivSuunnitelmat",
      "kvvSuunnitelmat",
      "rakennesuunnitelmat",
      "paapiirrustukset"
    ];
    if (filters.preVerdict() &&
        !_.some(subFilters, function(f) {
          return filters[f]();
        })) {
      return attachments;
    }
    return _.filter(attachments, function(attachment) {
      return _.some(subFilters, function(f) {
        return filters[f]() && filterFunctions[f](attachment);
      });
    });
  }

  function postVerdictAttachments(attachments) {
    if (filters.postVerdict()) {
      return attachments;
    } else {
      return [];
    }
  }

  function applyFilters(attachments) {
    var atts = filters.notNeeded() ?
        attachments :
        _.filter(attachments, {notNeeded: false});
    if (showAll()) {
      return atts;
    }
    return _.concat(
      preVerdictAttachments(_.filter(atts, isPreVerdict)),
      postVerdictAttachments(_.filter(atts, isPostVerdict))
    );
  }

  // Return attachment ids grouped first by type-groups and then by type ids.
  self.getAttachmentsHierarchy = function() {
    var attachments = _.map(self.attachments(), function (a) { return a(); });
    return _(applyFilters(attachments))
      .groupBy(getVerdictGroup)
      .mapValues(function(attachmentsOfVerdictGroup) {
        return _(attachmentsOfVerdictGroup)
          .groupBy(getMainGroup)
          .mapValues(function(attachmentsOfMainGroup) {
            var subGroups = _(attachmentsOfMainGroup)
              .groupBy(getSubGroup)
              .mapValues(function(attachments) {
                return _.map(attachments, "id");
              })
              .value();
            return subGroups["no-sub-group"] || subGroups;
          })
          .value();
      })
      .value();
  };
  self.getAttachmentsOfMainGroup = function(mainGroupId) {
    return self.getAttachmentsHierarchy()[mainGroupId];
  };
  self.getAttachmentsOfSubGroup = function(mainGroupId, subGroupId) {
    return self.getAttachmentsHierarchy()[mainGroupId][subGroupId];
  };
};
