(ns lupapalvelu.permit
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.core :refer [fail]]
            [taoensso.timbre :as timbre :refer [errorf]]))

(defonce ^:private permit-type-defs (atom {}))
(defn permit-types [] @permit-type-defs)

(def poikkeamislupa :poikkeamislupa)
(def suunnittelutarveratkaisu :suunnittelutarveratkaisu)

(defn register-mapper [permit-type mapper-key mapper-fn]
  {:pre [(contains? (permit-types) permit-type)
         (keyword? mapper-key)
         (fn? mapper-fn)]}
  (swap! permit-type-defs assoc-in [permit-type mapper-key] mapper-fn))

;;
;; Enum
;;

(defmacro defpermit [permit-name description m]
  `(do
     (def ~permit-name ~(str description) ~(str permit-name))
     (swap! permit-type-defs assoc ~permit-name ~m)))

(defpermit R  "Rakennusluvat"
  {:subtypes         []
   :sftp-user-key    :rakennus-ftp-user
   :sftp-directory   "/rakennus"})

(defpermit YA "Yleisten alueiden luvat"
  {:subtypes         []
   :sftp-user-key    :yleiset-alueet-ftp-user
   :sftp-directory   "/yleiset_alueet"})

(defpermit Y  "Ymparistoluvat"
  {:subtypes       []
   :sftp-user-key  nil
   :sftp-directory nil}) ;; TODO

(defpermit P  "Poikkeusluvat"
  {:subtypes         [poikkeamislupa suunnittelutarveratkaisu]
   :sftp-user-key    :poikkari-ftp-user
   :sftp-directory   "/poikkeusasiat"})

;;
;; Helpers
;;
(defn- get-metadata [permit-type k & [default]]
  (if permit-type
    (-> (permit-types) (get (name permit-type)) (get k default))
    default))

(defn permit-subtypes [permit-type]
  (get-metadata permit-type :subtypes []))

(defn get-sftp-directory [permit-type]
  (get-metadata permit-type :sftp-directory))

(defn get-sftp-user-key [permit-type]
  (get-metadata permit-type :sftp-user-key))

(defn get-application-mapper [permit-type]
  (get-metadata permit-type :app-krysp-mapper))

(defn get-review-mapper [permit-type]
  (get-metadata permit-type :review-krysp-mapper))

(defn permit-type
  "gets the permit-type of application"
  [application]
  {:post [(not= % nil)]}
  (:permitType application))

;;
;; Validate
;;

(defn validate-permit-type-is-not [validator-permit-type]
  (fn [_ application]
    (let [application-permit-type (permit-type application)]
      (when (= (keyword application-permit-type) (keyword validator-permit-type))
        (fail :error.invalid-permit-type :permit-type validator-permit-type)))))

(defn validate-permit-type-is [validator-permit-type]
  (fn [_ application]
    (let [application-permit-type (permit-type application)]
      (when-not (= (keyword application-permit-type) (keyword validator-permit-type))
        (fail :error.invalid-permit-type :permit-type validator-permit-type)))))

(defn is-valid-subtype [permitSubtype {permitType :permitType}]
  (when-not (some #(= permitSubtype %) (permit-subtypes permitType))
    (fail :error.permit-has-no-such-subtype)))


(defn validate-permit-has-subtypes [_ {permitType :permitType}]
    (when (empty? (permit-subtypes permitType))
      (fail :error.permit-has-no-subtypes)))
