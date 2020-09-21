(ns com.sitecrafting.cumulus.crop-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.sitecrafting.cumulus.crop :as crop]))


(deftest test-size-name->label
  (is (= "cat" (crop/size-name->label "cat")))
  (is (= "a b c" (crop/size-name->label "a_b_c")))
  (is (= "a b c" (crop/size-name->label "a-b-c")))
  (is (= "a b c" (crop/size-name->label "a_b-c"))))

(deftest test-update-current-size

  (testing "it fires the ::update-crop-params or ::destroy-cropper effect"
    (let [cofx {:db {:current-size {:size_name "thumbnail"}
                     :img-config {:params_by_size {:thumbnail
                                                   {:edit_mode "crop"
                                                    :crop {:x 0 :y 0 :w 150 :h 150}}
                                                   :medium
                                                   {:edit_mode "scale"
                                                    :crop {:x 100 :y 100 :w 600 :h 300}}}}}}]
      (let [result (crop/update-current-size cofx [:_ {:size_name "thumbnail"}])]
        (is (= [{:x 0 :y 0 :w 150 :h 150}]
               (::crop/update-crop-params result)))
        (is (nil?
             (::crop/destroy-cropper result))))
      (let [result (crop/update-current-size cofx [:_ {:size_name "medium"}])]
        (is (nil?
             (::crop/update-crop-params result)))
        (is (= []
               (::crop/destroy-cropper result))))))

  (testing "it updates :current-size with the passed map"
    (let [cofx {:db {:current-size {:size_name "thumbnail"
                                    :width 150
                                    :height 150}}}]
      (let [size {:size_name "medium" :width 300 :height 300}]
        (is (= size
               (:current-size (:db (crop/update-current-size cofx [:_ size]))))))
      (let [size {:size_name "large" :width 1024 :height 1024}]
        (is (= size
               (:current-size (:db (crop/update-current-size cofx [:_ size]))))))))

  (testing "it updates :edit-mode and :crop-params with the saved settings for the given size"
    (let [cofx {:db {:edit-mode "this gets overwritten"
                     :img-config {:params_by_size {:thumbnail {:edit_mode "crop"
                                                               :crop {:x 0 :y 0 :w 150 :h 150}}
                                                   :medium {:edit_mode "scale"}
                                                   :large {:edit_mode "scale"}}}
                     :crop-params nil
                     :current-size {:size_name "large"
                                    :width 1024
                                    :height 1024}}}]
      (let [updated (:db (crop/update-current-size cofx [:_ {:size_name "medium"}]))]
        (is (= :scale (:edit-mode updated)))
        (is (nil? (:crop-params updated))))
      (let [updated (:db (crop/update-current-size cofx [:_ {:size_name "thumbnail"}]))]
        (is (= :crop (:edit-mode updated)))
        (is (= {:x 0 :y 0 :w 150 :h 150} (:crop-params updated)))))))

(deftest test-db->update-sizes-config

  (testing "it derives request data from :current-size, :edit-mode, and :crop-params"
    (let [db {:img-config {:attachment_id 25
                           :params_by_size {:thumbnail {:edit_mode "crop"
                                                        :crop {:x 0 :y 0 :w 150 :h 150}}
                                            :medium {:edit_mode "scale"}
                                            :large {:edit_mode "scale"}}}
              :edit-mode :crop
              :crop-params {:x 50 :y 200 :w 2000 :h 2000}
              :current-size {:size_name "large"
                             :width 1024
                             :height 1024}}]

      (is (= {:attachment_id 25
              :params_by_size {:thumbnail {:edit_mode "crop"
                                           :crop {:x 0 :y 0 :w 150 :h 150}}
                               :medium {:edit_mode "scale"}
                               :large {:edit_mode "crop"
                                       :crop {:x 50 :y 200 :w 2000 :h 2000}}}}
             (crop/db->update-sizes-config db)))

      (is (= {:attachment_id 25
              :params_by_size {:thumbnail {:edit_mode "crop"
                                           :crop {:x 50 :y 200 :w 2000 :h 2000}}
                               :medium {:edit_mode "scale"}
                               :large {:edit_mode "scale"}}}
             (crop/db->update-sizes-config (assoc db
                                               :current-size {:size_name "thumbnail"}))))

      (is (= {:attachment_id 25
              :params_by_size {:thumbnail {:edit_mode "scale"}
                               :medium {:edit_mode "scale"}
                               :large {:edit_mode "scale"}}}
             (crop/db->update-sizes-config (assoc db
                                               :current-size {:size_name "thumbnail"}
                                               :edit-mode :scale)))))))

(deftest test-unsaved-changes?

  (testing "when the user has updated edit mode"
    (is (crop/unsaved-changes? {:img-config {:params_by_size {:thumbnail
                                                              {:edit_mode "crop"
                                                               :crop {:x 0 :y 0 :w 150 :h 150}}}}
                                :edit-mode :scale
                                :crop-params {}
                                :current-size {:size_name "thumbnail"}})))

  (testing "when the user has updated crop params"
    (is (crop/unsaved-changes? {:img-config {:params_by_size {:thumbnail
                                                              {:edit_mode "crop"
                                                               :crop {:x 0 :y 0 :w 300 :h 300}}}}
                                :edit-mode :crop
                                :crop-params {:x 50 :y 100 :w 300 :h 300}
                                :current-size {:size_name "thumbnail"}})))

  (testing "when there are NO unsaved changes"
    (is (false? (crop/unsaved-changes? {:img-config {:params_by_size {:thumbnail
                                                                      {:edit_mode "crop"
                                                                       :crop {:x 0 :y 0 :w 300 :h 300}}}}
                                        :edit-mode :crop
                                        :crop-params {:x 0 :y 0 :w 300 :h 300}
                                        :current-size {:size_name "thumbnail"}})))))

(deftest test-save-current-size
  (let [cofx {:db {:img-config {:attachment_id 25
                                :params_by_size {:thumbnail
                                                 {:edit_mode "crop"
                                                  :crop {:x 0 :y 0 :w 150 :h 150}}}}
                   :edit-mode :crop
                   :crop-params {:x 50 :y 50 :w 300 :h 300}
                   :current-size {:size_name "thumbnail"}}}]

    ;; Saving updates the params by current-size,
    ;; AND sends an API request
    (is (= {:db {:img-config {:attachment_id 25
                              :params_by_size {:thumbnail
                                               {:edit_mode "crop"
                                                :crop {:x 50 :y 50 :w 300 :h 300}}}}
                 :edit-mode :crop
                 :crop-params {:x 50 :y 50 :w 300 :h 300}
                 :current-size {:size_name "thumbnail"}}
            ::crop/save-current-size! {:attachment_id 25
                                       :params_by_size {:thumbnail
                                                        {:edit_mode "crop"
                                                         :crop {:x 50 :y 50 :w 300 :h 300}}}}}
           (crop/save-current-size cofx)))))

(deftest test-reset-current-size
  (let [db {:current-size {:size_name "large"}}]
    ;; Resetting simply delegates to ::update-current-size with... current-size
    (is (= {:dispatch [::crop/update-current-size {:size_name "large"}]}
           (crop/reset-current-size {:db db})))))