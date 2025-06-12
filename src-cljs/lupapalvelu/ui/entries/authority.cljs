(ns lupapalvelu.ui.entries.authority
  "Views used exclusively by the authority user"
  (:require [lupapalvelu.ui.biller.view :as biller]
            [lupapalvelu.ui.entries.dev-reload :as reload]
            [lupapalvelu.ui.imports.view :as imports]
            [lupapalvelu.ui.invoices.view :as invoices]))

(reload/register! :authority
                  [biller/mounter
                   invoices/mounter
                   imports/mounter])
