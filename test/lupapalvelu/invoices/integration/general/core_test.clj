(ns lupapalvelu.invoices.integration.general.core-test
  (:require [lupapalvelu.invoices.integration.general.core]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.invoices.integration.general.core
                   filename-for-xml-sftp)

(fact "filenames"
  (filename-for-xml-sftp nil 1560772700083)
  => "Lupapiste_190617145820.xml"
  (filename-for-xml-sftp {:invoice-file-prefix "Prefix_"}
                         1560772700083)
  => "Prefix_190617145820.xml")
