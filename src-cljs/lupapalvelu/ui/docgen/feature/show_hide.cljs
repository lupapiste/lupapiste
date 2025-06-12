(ns lupapalvelu.ui.docgen.feature.show-hide
  "Handle `:show-when` & `:hide-when` options."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.flags :as flags]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [sade.strings :as ss]
            [sade.util :as util]))

;; Source: the field providing the value
;; Target: the field to be shown / hidden based on the value

(defn get-full-source-path
  "The source path may be given as `foo`, `/foo`, `foo/bar` etc.

  If the path starts with `/`, it is considered to be an absolute path."
  [source-path target-path document]
  {:pre [(string? source-path) (vector? target-path)]}
  (let [path (ss/split source-path #"/")]
    (cond
      ;; Did the path start with `/` or references another document?
      (or (= "" (nth path 0))
          document)
      ;; Absolute path, starts from the root of the document
      (->> path
           (remove ss/blank?) ;; Drop the "", if any
           (map keyword)
           (into []))

      ;; Relative path, starts from the _parent_ of `target-path`
      :else
      (->> (concat (pop target-path)
                   (map keyword path))
           (into [])))))

(defn- matches?
  [{:keys [doc-id] target-path :path}
   {:keys [path values document]}]
  (let [;; In some special cases the source might be within another document
        doc-id         (if document
                         (<sub [::docs/doc-id-by-name document])
                         doc-id)
        matching-values (set values)
        source-path    (get-full-source-path path target-path document)
        current-value  (<sub [::value/current-field-value doc-id source-path])]
    (contains? matching-values current-value)))

(defn show-field?
  "Show or hide the field depending on `:show-when` & `:hide-when`."
  [{{:keys [show-when hide-when]} :schema
    :keys                         [doc-id path]}]
  (let [show-when (when (and show-when (flags/include-schema? show-when))
                    show-when)
        hide-when (when (and hide-when (flags/include-schema? hide-when))
                    hide-when)]
    (or (and (nil? show-when)
             (nil? hide-when))
        (and show-when
             (matches? (util/mmap doc-id path) show-when))
        (and hide-when
             (not (matches? (util/mmap doc-id path) hide-when))))))
