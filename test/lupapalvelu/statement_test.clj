(ns lupapalvelu.statement-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lupapalvelu.statement :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]))

(def test-app-R  {:application {:municipality 753 :permitType "R"}})
(def test-app-P  {:application {:municipality 753 :permitType "P"}})
(def test-app-YA {:application {:municipality 753 :permitType "YA"}})
(def test-app-YM {:application {:municipality 753 :permitType "YM"}})
(def statuses-small ["puollettu" "ei-puollettu" "ehdollinen"])
(def statuses-large ["ei-huomautettavaa" "ehdollinen" "puollettu"
                     "ei-puollettu" "ei-lausuntoa" "lausunto"
                     "kielteinen" "palautettu" "poydalle" "ei-tiedossa"])

  ;; permit type R


(fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.3"
  (possible-statement-statuses
    (assoc test-app-R :organization
                      (delay {:krysp {:R {:version "2.1.5" :url "krysp-url"}}}))) => (just statuses-small :in-any-order))

(fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.5"
  (possible-statement-statuses
    (assoc test-app-R :organization
                      (delay {:krysp {:R {:version "2.1.6" :url "krysp-url"}}}))) => (just statuses-large :in-any-order))

  ;; permit type P


(fact "get-possible-statement-statuses, permit type P, krysp yhteiset version 2.1.3"
  (possible-statement-statuses
    (assoc test-app-P :organization
                      (delay {:krysp {:P {:version "2.1.5" :url "krysp-url"}}}))) => (just statuses-small :in-any-order))

(fact "get-possible-statement-statuses, permit type P, krysp yhteiset version 2.1.5"
  (possible-statement-statuses
    (assoc test-app-P :organization
                      (delay {:krysp {:P {:version "2.2.0" :url "krysp-url"}}}))) => (just statuses-large :in-any-order))

(fact "get-possible-statement-statuses, permit type P, no KRYSP version nor url"
  (possible-statement-statuses (assoc test-app-P :organization
                                                 (delay {}))) => (just statuses-large :in-any-order))

(fact "get-possible-statement-statuses, permit type P, krysp yhteiset version 2.1.5 no url"
  (possible-statement-statuses
    (assoc test-app-P :organization
                      (delay {:krysp {:P {:version "2.1.5"}}}))) => (just statuses-large :in-any-order))

  ;; permit type YA


(fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.3"
  (possible-statement-statuses
    (assoc test-app-YA :organization (delay {:krysp {:YA {:version "2.1.3" :url "krysp-url"}}}))) => (just statuses-small :in-any-order))

(fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.5"
  (possible-statement-statuses
    (assoc test-app-YA :organization (delay {:krysp {:YA {:version "2.2.0" :url "krysp-url"}}}))) => (just statuses-small :in-any-order))

  ;; permit type YM


(fact "get-possible-statement-statuses, permit type YM, no krysp versions defined"
  (possible-statement-statuses
    (assoc test-app-YM :organization (delay {}))) => (just statuses-small :in-any-order))

(defn dummy-application [statement]
  {:statements [statement]})

(defspec validate-statement-owner-pass 5
  (prop/for-all [email (ssg/generator ssc/Email)]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc-in [:person :email] email))
                      command   {:data {:statementId (:id statement)} :user {:email email}
                                 :application (dummy-application statement)}]
                  (nil? (statement-owner command)))))

(defspec validate-statement-owner-fail 5
  (prop/for-all [[email1 email2] (gen/such-that (partial apply not=) (gen/tuple (ssg/generator ssc/Email) (ssg/generator ssc/Email)))]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc-in [:person :email] email1))
                      command   {:data {:statementId (:id statement)} :user {:email email2}
                                 :application (dummy-application statement)}]
                  (-> (statement-owner command)
                      :ok false?))))

(defspec validate-statement-given-pass 5
  (prop/for-all [state (gen/elements [:given :replyable :replied])]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc :state state))]
                  (nil? (statement-given {:data {:statementId (:id statement)}
                                          :application (dummy-application statement)})))))

(defspec validate-statement-given-fail 5
  (prop/for-all [state (gen/elements [:requested :draft :unknown-state])]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc :state state))]
                  (-> (statement-given {:data {:statementId (:id statement)}
                                        :application (dummy-application statement)})
                      :ok false?))))

(defspec validate-statement-replyable-pass 1
  (prop/for-all [state (gen/elements [:replyable])]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc :state state))]
                  (nil? (statement-replyable {:data {:statementId (:id statement)}
                                              :application (dummy-application statement)})))))

(defspec validate-statement-replyable-fail 10
  (prop/for-all [state (gen/elements [:requested :draft :given :replied :unknown-state])]
                (let [statement (-> (ssg/generate Statement)
                                    (assoc :state state))]
                  (-> (statement-replyable {:data {:statementId (:id statement)}
                                            :application (dummy-application statement)})
                      :ok false?))))

(def id-1 (ssg/generate ssc/ObjectIdStr))
(def id-2 (ssg/generate ssc/ObjectIdStr))
(def id-a (ssg/generate ssc/ObjectIdStr))
(def id-b (ssg/generate ssc/ObjectIdStr))

(facts "update-statement"
  (fact "update-draft"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a)
        (dissoc :modified)
        (update-draft "some text" "puollettu" id-a id-1 false))
    => (contains #{[:text "some text"]
                   [:status "puollettu"]
                   [:modify-id anything]
                   [:editor-id id-1]
                   [:state :draft]
                   [:modified anything]}))

  (fact "update-draft - statement in attachment"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a)
        (dissoc :modified)
        (update-draft nil "puollettu" id-a id-1 true))
    => (contains #{[:status "puollettu"]
                   [:modify-id anything]
                   [:editor-id id-1]
                   [:state :draft]
                   [:modified anything]
                   [:in-attachment true]}))

  (fact "update-draft - wrong modify-id"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a)
        (update-draft "some text" "puollettu" id-b id-1 false))
    => (throws Exception))

  (fact "update-draft - updated statement is missing person should produce validation error"
    (-> (ssg/generate Statement)
        (dissoc :person)
        (update-draft "some text" "puollettu" id-b id-1 false))
    => (throws Exception))

  (fact "give-statement"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a)
        (dissoc :given)
        (give-statement "some text" "puollettu" id-a id-1 false (now)))
    => (contains #{[:text "some text"]
                   [:status "puollettu"]
                   [:modify-id anything]
                   [:editor-id id-1]
                   [:state :given]
                   [:given anything]}))

  (fact "give-statement - statement in attachment"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a)
        (dissoc :given)
        (give-statement "some text" "puollettu" id-a id-1 true (now)))
    => (contains #{[:text "some text"]
                   [:status "puollettu"]
                   [:modify-id anything]
                   [:editor-id id-1]
                   [:state :given]
                   [:given anything]
                   [:in-attachment true]}))

  (fact "update-reply-draft"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a :editor-id id-1 :state :announced :text "statement text")
        (assoc-in [:reply :saateText] "saate")
        (dissoc :modified)
        (update-reply-draft "reply text" true id-a id-2))
    => (contains #{[:text "statement text"]
                   [:modify-id anything]
                   [:editor-id id-1]
                   [:state :replyable]
                   [:modified anything]
                   [:reply {:editor-id id-2
                            :nothing-to-add true
                            :text "reply text"
                            :saateText "saate"}]}))

  (fact "update-reply-draft - nil values"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a :reply {:saateText "saate"})
        (update-reply-draft nil nil id-a id-2))
    => (contains #{[:reply {:editor-id id-2
                            :nothing-to-add false
                            :saateText "saate"}]}))

  (fact "reply-statement"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a :state :announced)
        (dissoc :reply)
        (reply-statement "reply text" false id-a id-2))
    => (contains #{[:state :replied]
                   [:reply {:editor-id id-2
                            :nothing-to-add false
                            :text "reply text"}]}))

  (fact "request for reply"
    (-> (ssg/generate Statement)
        (assoc :modify-id id-a)
        (dissoc :reply)
        (request-for-reply "covering note for reply" id-1))
    => (contains #{[:reply {:editor-id id-1
                            :nothing-to-add false
                            :saateText "covering note for reply"}]})))
