(ns crux.kv-test
  (:require [clojure.test :as t]
            [crux.fixtures :as f :refer [*kv*]]
            [crux.byte-utils :as bu]
            [crux.kv :as cr])
  (:import [java.net URI]))

(t/use-fixtures :each f/with-kv-store)

(def test-eid 1)

(t/deftest test-can-get-at-now
  (cr/-put *kv* [[test-eid :foo "Bar4"]])
  (t/is (= "Bar4" (cr/-get-at *kv* test-eid :foo)))
  (cr/-put *kv* [[test-eid :foo "Bar5"]])
  (t/is (= "Bar5" (cr/-get-at *kv* test-eid :foo)))

  ;; Insert into past
  (cr/-put *kv* [[test-eid :foo "foo1"]] #inst "2000-02-02")
  (t/is (= "Bar5" (cr/-get-at *kv* test-eid :foo))))

(t/deftest test-can-get-at-now-for-old-entry
  (cr/-put *kv* [[test-eid :foo "Bar3"]] #inst "2010-02-02")
  (t/is (= "Bar3" (cr/-get-at *kv* test-eid :foo))))

(t/deftest test-can-get-at-t
  (cr/-put *kv* [[test-eid :foo "Bar3"]] #inst "1901-01-31")
  (t/is (= "Bar3" (cr/-get-at *kv* test-eid :foo #inst "1901-02-01")))

  (cr/-put *kv* [[test-eid :foo "Bar4"]] #inst "1901-02-02")
  (cr/-put *kv* [[test-eid :foo "Bar5"]] #inst "1901-02-03")
  (cr/-put *kv* [[test-eid :foo "Bar6"]] #inst "1901-02-04")

  (t/is (= "Bar3" (cr/-get-at *kv* test-eid :foo #inst "1901-02-01")))
  (t/is (= "Bar4" (cr/-get-at *kv* test-eid :foo #inst "1901-02-02")))
  (t/is (= "Bar6" (cr/-get-at *kv* test-eid :foo #inst "1901-02-05"))))

(t/deftest test-can-get-nil-before-range
  (cr/-put *kv* [[test-eid :foo "Bar3"]] #inst "1901-02-02")
  (cr/-put *kv* [[test-eid :foo "Bar4"]] #inst "1901-02-03")
  (t/is (not (cr/-get-at *kv* test-eid :foo #inst "1901-01-31"))))

(t/deftest test-can-get-nil-outside-of-range
  (cr/-put *kv* [[test-eid :foo "Bar3"]] #inst "1986-10-22")
  (cr/-put *kv* [[test-eid :tar "Bar4"]] #inst "1986-10-22")
  (t/is (not (cr/-get-at *kv* test-eid :tar #inst "1986-10-21"))))

(t/deftest test-next-entity-id
  (let [eid (cr/next-entity-id *kv*)]
    (dotimes [n 1000]
      (cr/next-entity-id *kv*))

    (t/is (= (+ eid 1001) (cr/next-entity-id *kv*)))))

(t/deftest test-write-and-fetch-entity
  (let [person (first f/people)]
    (cr/-put *kv* [person] #inst "1986-10-22")
    (t/is (= person
             (cr/entity *kv* (:crux.kv/id person))))))

(t/deftest test-fetch-entity-at-t
  (let [person (first f/people)]
    (cr/-put *kv* [(assoc person :name "Fred")] #inst "1986-10-22")
    (cr/-put *kv* [(assoc person :name "Freda")] #inst "1986-10-24")
    (t/is (= "Fred"
             (:name (cr/entity *kv* (:crux.kv/id person) #inst "1986-10-23"))))
    (t/is (= "Freda"
             (:name (cr/entity *kv* (:crux.kv/id person)))))))

(t/deftest test-transact-schema-attribute
  (cr/-put *kv* [[test-eid :new-ident "foo1"]])
  (t/is (= "foo1" (cr/-get-at *kv* test-eid :new-ident)))

  (cr/-put *kv* [[test-eid :new-ident2 1]])
  (t/is (= 1 (cr/-get-at *kv* test-eid :new-ident2)))

  (cr/-put *kv* [[2 :new-ident2 "stringversion"]])
  (t/is (= "stringversion" (cr/-get-at *kv* 2 :new-ident2))))

(t/deftest test-retract-attribute
  (cr/-put *kv* [[test-eid :foo "foo1"]] #inst "1986-10-22")
  (cr/-put *kv* [[test-eid :foo nil]])
  (t/is (not (cr/-get-at *kv* test-eid :foo)))
  (t/is (= "foo1" (cr/-get-at *kv* test-eid :foo #inst "1986-10-22"))))

(t/deftest test-get-attributes
  (cr/-put *kv* [[test-eid :foo :bar]])
  (t/is (= [:foo] (keys (:attributes @(:state *kv*)))))
  (reset! (:state *kv*) nil)
  (t/is (= :bar (cr/-get-at *kv* test-eid :foo)))
  (t/is (= [:foo] (keys (:attributes @(:state *kv*))))))

(t/deftest test-write-entity-id-as-keyword
  (cr/-put *kv* [[:a-keyword-1 :foo :bar]])
  (t/is (= :bar (cr/-get-at *kv* :a-keyword-1 :foo))))

(t/deftest test-primitives
  (cr/-put *kv* [[test-eid :foo "foo1"]])
  (t/is (= "foo1" (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo 1]])
  (t/is (= 1 (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo (byte 1)]])
  (t/is (= 1 (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo (short 1)]])
  (t/is (= 1 (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo (int 1)]])
  (t/is (= 1 (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo 1.0]])
  (t/is (= 1.0 (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo (float 1.0)]])
  (t/is (= 1.0 (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo 1M]])
  (t/is (= 1M (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo (biginteger 1)]])
  (t/is (= (biginteger 1) (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo #inst "2001-01-01"]])
  (t/is (= #inst "2001-01-01" (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo true]])
  (t/is (= true (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo #uuid "fbee1a5e-273b-4d70-9fde-be76be89209a"]])
  (t/is (=  #uuid "fbee1a5e-273b-4d70-9fde-be76be89209a"
            (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo (byte-array [1 2])]])
  (t/is (bu/bytes=? (byte-array [1 2]) (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo (URI. "http://google.com/")]])
  (t/is (= (URI. "http://google.com/") (cr/-get-at *kv* test-eid :foo)))

  (cr/-put *kv* [[test-eid :foo [1 2]]])
  (t/is (= [1 2] (cr/-get-at *kv* test-eid :foo))))

(t/deftest test-store-and-retrieve-meta
  (t/is (nil? (cr/read-meta *kv* :foo)))
  (cr/store-meta *kv* :foo {:bar 2})
  (t/is (= {:bar 2} (cr/read-meta *kv* :foo))))

(t/deftest test-can-get-at-business-time-filter-for-tx-time
  (cr/-put *kv* [[test-eid :foo "Foo1"]] #inst "2000-02-02" #inst "2000-02-02")
  (cr/-put *kv* [[test-eid :foo "Foo2"]] #inst "2000-02-04" #inst "2000-02-04")
  (cr/-put *kv* [[test-eid :foo "Foo3"]] #inst "2000-02-06" #inst "2000-02-06")

    ;; Perform a correction in the past
  (cr/-put *kv* [[test-eid :foo "Foo4"]] #inst "2000-02-04" #inst "2000-02-07")

  (t/testing "as-of business time holds"
    (t/is (= "Foo3" (cr/-get-at *kv* test-eid :foo))))

  (t/testing "Correction is made for business time"
    (t/is (= "Foo4" (cr/-get-at *kv* test-eid :foo #inst "2000-02-04"))))

  (t/testing "Can fetch original via prior to correction using tx-time"
    (t/is (= "Foo4" (cr/-get-at *kv* test-eid :foo #inst "2000-02-04")))
    (t/is (= "Foo2" (cr/-get-at *kv* test-eid :foo #inst "2000-02-04" #inst "2000-02-06")))))

(t/deftest test-can-iterate-all-entity-ids
  (cr/-put *kv* [[1 :foo "Bar1"]])
  (cr/-put *kv* [[2 :foo "Bar2"]])
  (t/is (= (set [1 2]) (cr/entity-ids *kv*))))

(t/deftest test-can-query-against-values
  (cr/-put *kv* [[1 :foo "Bar1"]])
  (cr/-put *kv* [[2 :foo "Bar2"]])
  (t/is (= (list 1) (cr/entity-ids-for-value *kv* :foo "Bar1")))
  (t/is (= (list 2) (cr/entity-ids-for-value *kv* :foo "Bar2")))

  (t/testing "Multiple values"
    (cr/-put *kv* [[3 :foo "BarX"]])
    (cr/-put *kv* [[4 :foo "BarX"]])
    (t/is (= (list 3 4) (sort (cr/entity-ids-for-value *kv* :foo "BarX")))))

  (t/testing "Keyword values"
    (cr/-put *kv* [[5 :foo :barY]])
    (t/is (= (list 5) (sort (cr/entity-ids-for-value *kv* :foo :barY))))))

(t/deftest test-can-perform-range-search-with-logs
  (cr/-put *kv* [[:id1 :foo 10]
                 [:id2 :foo -2]])

  (t/testing "Min"
    (t/is (= '(:id1) (cr/entity-ids-for-range-value *kv* :foo 9 nil (java.util.Date.)))))

  (t/testing "Min with a minus number against a positive number"
    ;; -3 is before 10, so 10 should show up.
    (t/is (= '#{:id1 :id2} (set (cr/entity-ids-for-range-value *kv* :foo -3 nil (java.util.Date.))))))

  (t/testing "Min with a minus number against a minus number"
    (t/is (= #{:id1 :id2} (set (cr/entity-ids-for-range-value *kv* :foo -3 nil (java.util.Date.))))))

  (t/testing "LT"
    (t/is (= #{:id2} (set (cr/entity-ids-for-range-value *kv* :foo nil 8 (java.util.Date.)))))
    (t/is (= #{:id1 :id2} (set (cr/entity-ids-for-range-value *kv* :foo nil 11 (java.util.Date.))))))

  (t/testing "LT with a minus number against a minus number"
    (t/is (= '(:id2) (cr/entity-ids-for-range-value *kv* :foo nil -1 (java.util.Date.))))))
