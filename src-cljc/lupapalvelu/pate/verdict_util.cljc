(ns lupapalvelu.pate.verdict-util
  "Utility CLJC functions for verdicts and verdict templates"
  (:require [sade.strings :as ss]
            [sade.util :as util]))

(defn loc-item
  "Returns the corresponding item from the verdict template settings for the given
  `value`. The value is interpreted either id or code field. Returns nil if no match is
  found."
  [verdict reference-kw value]
  (let [refs (get-in verdict [:references reference-kw])]
    (when value
      (or (util/find-by-id value refs)
          (util/find-by-key :code value refs)))))

(defn loc-item-text
  "Returns the corresponding localisation from the verdict template settings for the given
  `value`. The value is interpreted either id or code field. Returns `value` if no
  localisation was found."
  [verdict reference-kw value lang]
  (get (loc-item verdict reference-kw value)
       (keyword lang)
       value))

(defn blocks-template?
  "Returns true if the template blocker (reason not to use the verdict template to create a new verdict)
  should actually block the user or just warn them against using the template"
  [blocker]
  (and blocker
       (util/not=as-kw blocker :kuulutus-date-missing)))

(defn category-supports-giver-names?
  [category]
  (util/includes-as-kw? [:r :p] category))

(defn verdict-supports-giver-names?
  "Only :r and :p categoried verdicts support verdict-giver names"
  [verdict]
  (category-supports-giver-names? (:category verdict)))

(defn template-viranhaltija?
  [verdict]
  (util/=as-kw :viranhaltija (-> verdict :template :giver)))

(defn valid-verdict-giver?
  "Checks if a verdict giver is valid for the given giver type.
   For viranhaltija: must have both first-name AND last-name
   For lautakunta: must have fi, sv, and en values
   Other/unknown giver types always pass validation."
  [giver-type item]
  (cond
    (util/=as-kw giver-type :viranhaltija)
    (and (ss/not-blank? (:first-name item))
         (ss/not-blank? (:last-name item)))

    (util/=as-kw giver-type :lautakunta)
    (and (ss/not-blank? (:fi item))
         (ss/not-blank? (:sv item))
         (ss/not-blank? (:en item)))

    :else true))
