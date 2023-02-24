(ns lupapalvelu.sftp.util
  "Convenience namespace for some widely referenced definitions."
  (:require [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]))

(sc/defn ^:always-validate get-file-name-on-server :- ssc/NonBlankStr
  [file-id :- ssc/NonBlankStr file-name :- ssc/NonBlankStr]
  (str file-id "_" (ss/encode-filename file-name)))

(sc/defn ^:always-validate get-submitted-filename :- ssc/NonBlankStr
  [application-id :- ssc/NonBlankStr]
  (str application-id "_submitted_application.pdf"))

(sc/defn ^:always-validate get-current-filename :- ssc/NonBlankStr
  [application-id :- ssc/NonBlankStr]
  (str application-id "_current_application.pdf"))
