(ns com.sitecrafting.cumulus.crop-test
  (:require
   [clojure.test :refer [deftest are is testing]]
   [com.sitecrafting.cumulus.crop :as crop]))


(deftest test-scaling-factor

  (testing "it returns the current full-size / rendered ratio"
    (let [cofx {:dimensions {:rendered-width 500
                             :natural-width 1000}}]
      (is (= 2 (crop/scaling-factor cofx))))))

(deftest test-saved-params

  (testing "it returns the saved params for the current size"
    (let [sm {:x 0 :y 0 :w 150 :h 150}
          med {:x 10 :y 15 :w 200 :h 200}
          db {:img-config {:params_by_size
                           {:small sm
                            :medium med}}
              :current-size {:size_name "small"}}]
      (is (= sm (crop/saved-params db)))
      (is (= med (crop/saved-params
                  (assoc db :current-size {:size_name "medium"})))))))

(deftest test-update-current-size

  (testing "it updates :current-size with the passed map"
    (let [db {:current-size {:size_name "thumbnail"
                             :width 150
                             :height 150}}]
      (let [size {:size_name "medium" :width 300 :height 300}]
        (is (= size
               (:current-size (crop/update-current-size db [:_ size])))))
      (let [size {:size_name "large" :width 1024 :height 1024}]
        (is (= size
               (:current-size (crop/update-current-size db [:_ size])))))))

  (testing "it updates :edit-mode and :crop-params with the saved settings for the given size"
    (let [db {:edit-mode "this gets overwritten"
              :img-config {:params_by_size {:thumbnail {:edit_mode "crop"
                                                        :crop {:x 0 :y 0 :w 150 :h 150}}
                                            :medium {:edit_mode "scale"}
                                            :large {:edit_mode "scale"}}}
              :crop-params nil
              :current-size {:size_name "large"
                             :width 1024
                             :height 1024}}]
      (let [updated (crop/update-current-size db [:_ {:size_name "medium"}])]
        (is (= :scale (:edit-mode updated)))
        (is (nil? (:crop-params updated))))
      (let [updated (crop/update-current-size db [:_ {:size_name "thumbnail"}])]
        (is (= :crop (:edit-mode updated)))
        (is (= {:x 0 :y 0 :w 150 :h 150} (:crop-params updated)))))))

(deftest test-db->update-sizes-config

  (testing "it derives request data from :current-size, :edit-mode, and :crop-params"
    (let [db {:img-config {:attachment_id 25
                           :params_by_size {:thumbnail {:edit_mode "crop"
                                                        :crop {:x 0 :y 0 :w 300 :h 150}}
                                            :medium {:edit_mode "scale"}
                                            :large {:edit_mode "scale"}}}
              :edit-mode :crop
              :crop-params {:x 50 :y 200 :w 2000 :h 2000}
              :current-size {:size_name "large"
                             :width 1024
                             :height 1024}}]

      (is (= {:attachment_id 25
              :params_by_size {:thumbnail {:edit_mode "crop"
                                           :crop {:x 0 :y 0 :w 300 :h 150}}
                               :medium {:edit_mode "scale"}
                               :large {:edit_mode "crop"
                                       :crop {:x 50 :y 200 :w 2000 :h 2000}}}}
             (crop/db->update-sizes-config db)))

      (is (= {:attachment_id 25
              :params_by_size {:thumbnail {:edit_mode "crop"
                                           :crop {:x 50 :y 200 :w 2000 :h 2000}}
                               :medium {:edit_mode "scale"}
                               :large {:edit_mode "scale"}}}
             (crop/db->update-sizes-config
              (assoc db :current-size {:size_name "thumbnail"}))))

      (is (= {:attachment_id 25
              :params_by_size {:thumbnail {:edit_mode "scale"}
                               :medium {:edit_mode "scale"}
                               :large {:edit_mode "scale"}}}
             (crop/db->update-sizes-config
              (assoc db
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

(deftest test-cloudinary-params

  (testing "it accounts for config, current-size, crop-params, and edit-mode"
    (is (= {:mode :crop
            :bucket "my-bucket"
            :folder "test"
            :filename "test/cat.jpg"
            :x 123
            :y 456
            :w 1000
            :h 500
            :target-size [150 150]}
           (crop/cloudinary-params
            {:edit-mode :crop
             :img-config {:bucket "my-bucket"
                          :folder "test"
                          :filename "test/cat.jpg"
                          :params_by_size "THIS SHOULD BE IGNORED"
                          :any-other-stuff "should also be ignored"}
             :crop-params {:x 123
                           :y 456
                           :w 1000
                           :h 500}
             :current-size {:width 150 :height 150}})))))