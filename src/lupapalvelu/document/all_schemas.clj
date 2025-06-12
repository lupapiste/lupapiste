(ns lupapalvelu.document.all-schemas
  "Require this namespace to ensure all document schemas are loaded.

  This namespace exists to provide a single require for test namespaces
  and other code that needs all document schemas to be registered."
  (:require [lupapalvelu.document.allu-schemas]
            [lupapalvelu.document.poikkeamis-schemas]
            [lupapalvelu.document.rekisterointi-ilmoitus-schemas]
            [lupapalvelu.document.schemas]
            [lupapalvelu.document.vahahiilisyys-schemas]
            [lupapalvelu.document.vesihuolto-schemas]
            [lupapalvelu.document.waste-schemas]
            [lupapalvelu.document.yhteiskasittely-maa-aines-ja-ymparistoluvalle-schemas]
            [lupapalvelu.document.yleinen-ilmoitus-schemas]
            [lupapalvelu.document.yleiset-alueet-schemas]
            [lupapalvelu.document.ymparisto-schemas]))
