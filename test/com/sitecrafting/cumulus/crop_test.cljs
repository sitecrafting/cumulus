(ns com.sitecrafting.cumulus.crop-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.sitecrafting.cumulus.crop :as crop]))


(deftest test-size-name->label
  (is (= "cat" (crop/size-name->label "cat")))
  (is (= "a b c" (crop/size-name->label "a_b_c")))
  (is (= "a b c" (crop/size-name->label "a-b-c")))
  (is (= "a b c" (crop/size-name->label "a_b-c"))))

(deftest test-edit-mode
  (let [db {:img-config {:params_by_size
                         {:thumbnail {:edit_mode "crop"}
                          :medium {:edit_mode "crop"}
                          :large {:edit_mode "scale"}}}}
        db-for-size (fn [s] (assoc-in db [:current-size :size_name] s))]
    (is (= :crop (crop/db->edit-mode (db-for-size "thumbnail"))))
    (is (= :crop (crop/db->edit-mode (db-for-size "medium"))))
    (is (= :scale (crop/db->edit-mode (db-for-size "large"))))))

(deftest test-db->transform-params
  (let [db {:img-config {:cloud "my-cloud"
                         :filename "test/cat.jpg"
                         :params_by_size {:thumbnail
                                          {:edit_mode "crop"
                                           ;; We update crop data on each UI update.
                                           ;; The full transforms vector can be derived from this
                                           ;; (along with target-size)
                                           :crop {:w 300 :h 300 :x 1000 :y 500}}
                                          :medium
                                          {:edit_mode "crop"
                                           ;; Ditto above.
                                           :crop  {:w 600 :h 600 :x 2000 :y 1000}}
                                          :large
                                          {:edit_mode "scale"
                                            ;; Auto-scale transforms are derived directly
                                            ;; from target-size.
                                           }}}}]
    (is (= {:cloud "my-cloud"
            :filename "test/cat.jpg"
            :transforms [{:w 300 :h 300 :x 1000 :y 500 :c "crop"}
                         {:w 150 :h 150 :c "scale"}]}
           (crop/db->transform-params (assoc db :current-size {:size_name :thumbnail
                                                               :width 150
                                                               :height 150}))))
    (is (= {:cloud "my-cloud"
            :filename "test/cat.jpg"
            :transforms [{:w 600 :h 600 :x 2000 :y 1000 :c "crop"}
                         {:w 300 :h 300 :c "scale"}]}
           (crop/db->transform-params (assoc db :current-size {:size_name :medium
                                                               :width 300
                                                               :height 300}))))
    (is (= {:cloud "my-cloud"
            :filename "test/cat.jpg"
            :transforms [{:w 1024 :h 1024 :c "lfill"}]}
           (crop/db->transform-params (assoc db :current-size {:size_name :large
                                                               :width 1024
                                                               :height 1024}))))))

(deftest test-update-transform-params
  (let [db {:img-config {:params_by_size {:custom_card {:edit_mode "crop"
                                                        :crop "THIS SHOULD GET OVERWRITTEN"}
                                          :custom_hero {:edit_mode "scale"}}}}
        db-for-size (fn [m] (assoc db :current-size m))]
    (is (= {:edit_mode "crop"
            :crop {:x 0 :y 1 :w 300 :h 150}}
           (get-in
            (crop/update-transform-params (db-for-size {:size_name "custom_card"})
                                          [:_ :crop {:x 0 :y 1 :w 300 :h 150}])
            [:img-config :params_by_size :custom_card])))
    (is (= {:edit_mode "crop"
            :crop {:x 100 :y 100 :w 2000 :h 1000}}
           (get-in
            (crop/update-transform-params (db-for-size {:size_name "custom_hero"})
                                          [:_ :crop {:x 100 :y 100 :w 2000 :h 1000}])
            [:img-config :params_by_size :custom_hero])))
    (is (= {:edit_mode "scale"}
           (get-in
            (crop/update-transform-params (db-for-size {:size_name "custom_hero"})
                                          [:_ :scale])
            [:img-config :params_by_size :custom_hero])))))