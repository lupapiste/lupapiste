(ns lupapalvelu.notice-utils)

(defn demolition-notice-document? [doc]
  (get-in doc [:schema :info :demolition-notice?] false))
