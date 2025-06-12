(ns lupapalvelu.batchrun.rami-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.batchrun.rami :as rami]
            [monger.operators :refer :all]))

(def make-note #'rami/make-note)

(def ts 123456789)

(deftest make-note-test
  (with-redefs [sade.core/now (constantly ts)]
    (testing "no extra fields"
     (is (= {:note "hello" :created ts}
            (make-note "hello")))
     (is (= {:note nil :created ts}
            (make-note nil))))
   (testing "with extra fields"
     (is (= {:note "hello" :created ts :world "foo" :bar 10}
            (make-note "hello" :world "foo" :bar 10)))
     (is (= {:note "hello" :created ts :bar 10}
            (make-note "hello" :world nil :bar 10))))))

(def with-book #'rami/with-bookkeeping-note)

(deftest with-bookkeeping-note-test
  (with-redefs [sade.core/now (constantly ts)]
   (testing "no extra fields"
     (is (= {$push { :_sheriff-notes {:note "hello" :created ts}}}
            (with-book {} "hello")))
     (is (= {$set  {:hii "hoo"}
             $push {:queue          10
                    :_sheriff-notes {:note "hello" :created ts}}}
            (with-book {$set  {:hii "hoo"}
                        $push {:queue 10}}
              "hello"))))
   (testing "with extra fields"
     (is (= {$push { :_sheriff-notes {:note  "hello" :created ts
                                      :world "foo"    :bar     10}}}
            (with-book {} "hello" :world "foo" :bar 10)))
     (is (= {$set  {:hii "hoo"}
             $push {:queue          10
                    :_sheriff-notes {:note "hello" :created ts
                                     :bar  10}}}
            (with-book {$set  {:hii "hoo"}
                        $push {:queue 10}}
              "hello" :world nil :bar 10))))))

(def find-note  #'rami/find-start-note)
(def start-text @#'rami/start-text)

(deftest find-start-note-test
  (testing "not found"
    (doseq [m [nil {} {:_sheriff-notes [{:note "hello"}]}]]
      (is (nil? (find-note m)))))
  (testing "found"
    (is (= {:note start-text :foo "bar" :created ts}
           (find-note {:_sheriff-notes [{}
                                        {:note start-text :foo "bar"
                                         :created ts}]})))))

(def rami-oauth? #'rami/rami-oauth?)

(deftest rami-oauth?-test
  (testing "not authed"
    (is (false? (rami-oauth? {} "123-R")))
    (is (false? (rami-oauth? {:oauth-roles []} "123-R")))
    (is (false? (rami-oauth? {:oauth-roles [{:client-type "hello"
                                             :organization "123-R"}]}
                             "123-R")))
    (is (false? (rami-oauth? {:oauth-roles [{:client-type "bi-app"
                                             :organization "123-R"
                                             :role "bar"}]}
                             "123-R" "foo")))
    (is (false? (rami-oauth? {:oauth-roles [{:client-type "bi-app"
                                             :role "foo"}]}
                             nil "foo"))))
  (testing "authed"
    (is (true? (rami-oauth? {:oauth-roles [{:client-type "bi-app"
                                            :organization "123-R"}]}
                            "123-R")))
    (is (true? (rami-oauth? {:oauth-roles [{}
                                           {:client-type "bi-app"
                                            :organization "123-R"
                                            :role "reporter"}]}
                            "123-R" "reporter")))))
