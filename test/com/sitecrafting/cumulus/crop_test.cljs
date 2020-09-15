(ns com.sitecrafting.cumulus.crop-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.sitecrafting.cumulus.crop :as crop]))


(deftest test-size-name->label
  (is (= "cat" (crop/size-name->label "cat")))
  (is (= "a b c" (crop/size-name->label "a_b_c")))
  (is (= "a b c" (crop/size-name->label "a-b-c")))
  (is (= "a b c" (crop/size-name->label "a_b-c"))))