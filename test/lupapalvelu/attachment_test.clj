(ns lupapalvelu.attachment-test
  (:use [lupapalvelu.attachment]
        [clojure.test]
        [midje.sweet])
  (:require [clojure.string :as s]
            [monger.core :as monger]
            [lupapalvelu.core :as core]
            [lupapalvelu.mongo :as mongo]))

(def ascii-pattern #"[a-zA-Z0-9\-\.]+")

(facts "Test file name encoding"
  (fact (encode-filename nil)                                 => nil)
  (fact (encode-filename "foo.txt")                           => "foo.txt")
  (fact (encode-filename (apply str (repeat 255 \x)))         => (apply str (repeat 255 \x)))
  (fact (encode-filename (apply str (repeat 256 \x)))         => (apply str (repeat 255 \x)))
  (fact (encode-filename (apply str (repeat 256 \x) ".txt"))  => (has-suffix ".txt"))
  (fact (encode-filename "\u00c4\u00e4kk\u00f6si\u00e4")      => (just ascii-pattern))
  (fact (encode-filename "/root/secret")                      => (just ascii-pattern))
  (fact (encode-filename "\\Windows\\cmd.exe")                => (just ascii-pattern))
  (fact (encode-filename "12345\t678\t90")                    => (just ascii-pattern))
  (fact (encode-filename "12345\n678\r\n90")                  => (just ascii-pattern)))

(facts "Test parse-attachment-type"
  (fact (parse-attachment-type "foo.bar")  => [:foo :bar])
  (fact (parse-attachment-type "foo.")     => nil)
  (fact (parse-attachment-type "")         => nil)
  (fact (parse-attachment-type nil)        => nil))

(def test-attachments [{:id "1", :latestVersion {:version {:major 9, :minor 7}}}])

(facts "Test attachment-latest-version"
  (fact (attachment-latest-version test-attachments "1")    => {:major 9, :minor 7})
  (fact (attachment-latest-version test-attachments "none") => nil?))

(def next-attachment-version #'lupapalvelu.attachment/next-attachment-version)

(facts "Facts about next-attachment-version"
  (fact (next-attachment-version {:major 1 :minor 1} {:role :authority})  => {:major 1 :minor 2})
  (fact (next-attachment-version {:major 1 :minor 1} {:role :dude})       => {:major 2 :minor 0}))

(def allowed-attachment-type-for? #'lupapalvelu.attachment/allowed-attachment-type-for?)

(facts "Facts about allowed-attachment-type-for?"
  (fact (allowed-attachment-type-for? :buildingPermit {:type-group "hakija" :type-id "valtakirja"})          => truthy)
  (fact (allowed-attachment-type-for? :buildingPermit {:type-group "paapiirustus" :type-id "asemapiirros"})  => truthy)
  (fact (allowed-attachment-type-for? :buildingPermit {:type-group "hakija" :type-id "asemapiirros"})        => falsey)
  (fact (allowed-attachment-type-for? :buildingPermit {})                                                    => falsey))

; The result of attachment-types-for has very strict format that is required by upload.html. The
; structure should be a vector that looks like this:
;
; [{:key :hakija
;   :types [{:key :valtakirja}
;           {:key :ote_kauppa_ja_yhdistysrekisterista}
;           {:key :ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta}]}
;  {:key :rakennuspaikan_hallinta
;   :types [{:key :jaljennos_myonnetyista_lainhuudoista}
;           ...

(def attachment-types-for #'lupapalvelu.attachment/attachment-types-for)

(facts "The result of attachment-types-for has very strict format that is required by upload.html"
  (fact (attachment-types-for :buildingPermit) => sequential?)
  (fact (first (attachment-types-for :buildingPermit)) => associative?)
  (fact (:group (first (attachment-types-for :buildingPermit))) => keyword?)
  (fact (:types (first (attachment-types-for :buildingPermit))) => sequential?)
  (fact (first (:types (first (attachment-types-for :buildingPermit)))) => associative?)
  (fact (:name (first (:types (first (attachment-types-for :buildingPermit))))) => keyword?))

;;
;; Integration tests:
;; - these require running MongoDB in localhost:27017
;; - these tests will close the monger clobal connection! 
;; - these should be in different file and run on different cycle:
;;

(def pena {:id "777777777777777777000020" ;; pena
           :username "pena"
           :enabled true
           :role "applicant"
           :personId "010203-0405"
           :firstName "Pena"
           :lastName "Panaani"
           :email "pena"
           :street "Paapankuja 12"
           :zip "010203"
           :city "Piippola"
           :phone "0102030405"
           :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"
                     :salt "$2a$10$hLCt8BvzrJScTOGQcXJ34e"
                     :apikey "602cb9e58426c613c8b85abc"}})

(defn db-init []
  (monger/connect!)
  (monger/use-db! (str "test-" (System/currentTimeMillis)))
  (mongo/insert mongo/users pena))

(defn db-clean []
  (monger/command {:dropDatabase 1})
  (.close monger/*mongodb-connection*))

(defn with-db []
  (fn [f]
    (try
      (db-init)
      (f)
    (finally
      (db-clean)))))

(use-fixtures :once (with-db))

(defn execute-as-pena [command-name command-data]
  (core/execute
    (assoc (core/command command-name command-data) :user pena)))

(defn create-doc []
  (execute-as-pena "create-application"
    {:x 12
     :y 34
     :street "s"
     :zip "z"
     :city "c"
     :schemas []}))

(defn add-attachment [doc-id]
  (update-or-create-attachment
    doc-id
    nil
    {:type-group "tg" :type-id "tid"}
    "file-id"
    "filename"
    "content-type"
    1111 ; size
    2222 ; created
    pena))

(deftest foo
  (let [doc-id (:id (create-doc))]
    (let [doc (mongo/by-id mongo/applications doc-id)]
      (is (not (nil? (:attachments doc))))
      (is (empty? (:attachments doc))))
    (let [att-id (add-attachment doc-id)
          doc (mongo/by-id mongo/applications doc-id)]
      (is (not (empty? (:attachments doc)))))))

(if false
  (run-tests))
