(ns lupapalvelu.attachment.file
  (:require [sade.strings :as ss]))

(defn filename-for-pdfa [filename]
  {:pre [(string? filename)]}
  (ss/replace filename #"(-PDFA)?\.(?i)pdf$" "-PDFA.pdf"))
