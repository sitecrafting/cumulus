(ns com.sitecrafting.cumulus.crop-test
  (:require
   [clojure.test :refer [deftest are is testing]]
   [com.sitecrafting.cumulus.crop :as crop]))


(deftest test-scaling-factor

  (testing "it returns the current full-size / rendered ratio"
    (let [cofx {:dimensions {:rendered-width 500
                             :natural-width 1000}}]
      (is (= 2 (crop/scaling-factor cofx))))))

(deftest test-aspect-ratio

  (testing "it honors hard/soft crops"
    (testing "with hard crop"
      (is (= 1.618
             (crop/aspect-ratio
              {:current-size {:hard   true
                              :width  1618
                              :height 1000}}))))
    (testing "when height is non-positive"
      (is (nil? (crop/aspect-ratio
                 {:current-size {:hard   true
                                 :width  1000
                                 :height 0}})))
      (is (nil? (crop/aspect-ratio
                 {:current-size {:hard  true
                                 :width 1000}})))
      (is (nil? (crop/aspect-ratio
                 {:current-size {:hard   true
                                 :width  1000
                                 :height -1}}))))
    (testing "with soft crop"
      (is (nil? (crop/aspect-ratio
                 {:current-size {:hard   false
                                 :width  1618
                                 :height 1000}}))))))

(deftest test-current-size

  (testing "it honors hard/soft crops"

    (let [size {:size_name "custom"
                :width 1000
                :height 1000
                :hard true}
          db {:current-size size}]
      (testing "with hard crop"
        (is (= size (crop/current-size db))))
      (testing "with soft crop"
        (is (= {:size_name "custom"
                :width 1000
                :height 0
                :hard false}
               (crop/current-size
                     (assoc-in db [:current-size :hard] false))))))))

(deftest test-crop-params

  (testing "it returns the normal :crop-params by default"
    (let [db {:crop-params {:x 0 :y 1 :w 2 :h 3}
              :current-size {:hard true}}]
      (is (= {:x 0 :y 1 :w 2 :h 3} (crop/crop-params db)))))

  (testing "it enforces minimum dimensions"
    (let [db {:crop-params {:x 100 :y 100 :w 450 :h 450}
              :dimensions {:natural-width 500
                           :natural-height 500}
              :current-size {:size_name "custom"
                             :width "this gets overwritten"
                             :height "this gets overwritten"
                             :hard true}}
          with-size (fn [[w h]]
                      (-> db
                          (assoc-in [:current-size :width] w)
                          (assoc-in [:current-size :height] h)))]
      (testing "when under minimum width"
        ;; bumps up to (min natural-width width)
        (is (= {:x 0 :y 100 :w 500 :h 450}
               (crop/crop-params (with-size [750 300])))))
      (testing "when under minimum height"
        ;; bumps up to (min natural-height height)
        (is (= {:x 100 :y 0 :w 450 :h 500}
               (crop/crop-params (with-size [300 750])))))
      (testing "when under both minima"
        (is (= {:x 0 :y 0 :w 500 :h 500}
               (crop/crop-params (with-size [750 750]))))))))

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

(deftest test-check-dimensions

  (testing "it reports dimension errors"
    (let [bigger {:size_name "custom" :width 750 :height 750}
          smaller {:size_name "custom" :width 300 :height 300}
          db {:crop-params {:x 100 :y 100 :w 500 :h 500}
              :dimensions {:natural-width 500 :natural-height 500}
              :current-size {}}
          with-size #(assoc %1 :current-size %2)]
      (is (= {:global "Your image is too small to be displayed at this size. Distortion will occur."}
             (:errors (crop/check-dimensions
                       (with-size db bigger)))))
      (is (= {} (:errors (crop/check-dimensions
                          (with-size (assoc db :errors {:global "OH NO"}) smaller))))))))

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
        (is (= {:x 0 :y 0 :w 150 :h 150} (:crop-params updated))))

      ;; Only update edit-mode if we're changing the actual size being edited.
      ;; This is because switching from one crop mode to the other also reloads
      ;; the image, which dispatches ::update-current-size
      (let [updated (crop/update-current-size
                     (assoc db :edit-mode :crop)
                     [:_ {:size_name "large"}])]
        (is (= :crop (:edit-mode updated)))))))

(deftest test-db->update-sizes-config

  (testing "it derives request data from :current-size, :edit-mode, and :crop-params"
    (let [urls-by-size {:thumbnail
                        (str "https://res.cloudinary.com"
                             "/my-bucket/image/upload"
                             "/w_400,h_400,c_crop"
                             "/w_150,h_150,c_scale"
                             "/test/cat.jpg")
                        :medium
                        (str "https://res.cloudinary.com"
                             "/my-bucket/image/upload"
                             "/w_300,h_300,c_lfill"
                             "/test/cat.jpg")
                        :large
                        (str "https://res.cloudinary.com"
                             "/my-bucket/image/upload"
                             "/w_1024,h_1024,c_lfill"
                             "/test/cat.jpg")}
          sizes [{:size_name "thumbnail"
                  :width 150
                  :height 150}
                 {:size_name "medium"
                  :width 300
                  :height 300}
                 {:size_name "large"
                  :width 1024
                  :height 1024}]
          config {:attachment_id 25
                  :bucket "my-bucket"
                  :filename "test/cat.jpg"
                  :urls_by_size urls-by-size
                  :params_by_size {:thumbnail {:edit_mode "crop"
                                               :crop {:x 0 :y 0 :w 400 :h 400}}
                                   :medium {:edit_mode "scale"}
                                   :large {:edit_mode "scale"}}}
          db {:img-config config
              :crop-params nil
              :edit-mode :scale
              :current-size {:size_name "large"
                             :width 1024
                             :height 1024}
              :sizes sizes}]

      ;; updating large to a 2000x2000 (+ 50,200) crop
      (is (= (assoc-in config
                       [:urls_by_size :large]
                       (str "https://res.cloudinary.com"
                            "/my-bucket/image/upload"
                            "/w_1024,h_1024,c_lfill"
                            "/test/cat.jpg"))
             (crop/db->update-sizes-config db)))

      ;; updating current size to thumbnail and changing the crop size
      (is (= (-> config
                 (assoc-in
                  [:urls_by_size :thumbnail]
                  (str "https://res.cloudinary.com"
                       "/my-bucket/image/upload"
                       "/x_50,y_200,w_1000,h_1000,c_crop"
                       "/w_150,h_150,c_scale"
                       "/test/cat.jpg"))
                 (assoc-in
                  [:params_by_size :thumbnail]
                  {:edit_mode "crop"
                   :crop {:x 50 :y 200 :w 1000 :h 1000}}))
             (crop/db->update-sizes-config
              (assoc db
                     :edit-mode :crop
                     :current-size {:size_name "thumbnail"
                                    :width 150
                                    :height 150}
                     :crop-params {:x 50 :y 200 :w 1000 :h 1000}))))

      ;; updating thumbnail to scale mode
      (is (= (-> config
                 (assoc-in
                  [:urls_by_size :thumbnail]
                  (str "https://res.cloudinary.com"
                       "/my-bucket/image/upload"
                       "/w_150,h_150,c_lfill"
                       "/test/cat.jpg"))
                 (assoc-in
                  [:params_by_size :thumbnail]
                  {:edit_mode "scale"})
                 (get :params_by_size))
             (:params_by_size (crop/db->update-sizes-config
                               (assoc db
                                      :crop-params nil
                                      :current-size {:size_name "thumbnail"
                                                     :width 150
                                                     :height 150}
                                      :edit-mode :scale))))))))

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

(deftest test-cloudinary-params

  (testing "it accounts for config, current-size, crop-params, and edit-mode"
    (is (= {:mode :crop
            :bucket "my-bucket"
            :filename "test/cat.jpg"
            :x 123
            :y 456
            :w 1000
            :h 500
            :target-size [150 150]}
           (crop/cloudinary-params
            {:edit-mode :crop
             :img-config {:bucket "my-bucket"
                          :filename "test/cat.jpg"
                          :params_by_size "THIS SHOULD BE IGNORED"
                          :any-other-stuff "should also be ignored"}
             :crop-params {:x 123
                           :y 456
                           :w 1000
                           :h 500}
             :current-size {:width 150 :height 150 :hard true}}))))

  (testing "it handles soft cropping"
    ;; An intuitive example with nice, round numbers
    (is (= {:mode :crop
            :bucket "my-bucket"
            :filename "test/cat.jpg"
            :x 123
            :y 456
            ;; Crop params stay the same...
            :w 100
            :h 800
            ;; ...but target-size gets shortened down to a height of 400.
            :target-size [50 400]}
           (crop/cloudinary-params
            {:edit-mode :crop
             :img-config {:bucket "my-bucket"
                          :filename "test/cat.jpg"
                          :params_by_size "IGNORED"}
             :crop-params {:x 123
                           :y 456
                           :w 100
                           :h 800}
             :current-size {;; This is the limiting factor in the
                            ;; target-size calculation.
                            :width 100
                            :height 400
                            :hard false}})))
    ;; A more realistic example, this time without the need for narrowing
    ;; to get to the target width.
    (is (= {:mode :crop
            :bucket "my-bucket"
            :filename "test/cat.jpg"
            :x 2000
            :y 300
            :w 1980
            :h 2220
            ;; h is short enough already, so no narrowing,
            ;; but we do shorten so that the resulting image
            ;; doesn't get stretched
            :target-size [1980 2220]}
           (crop/cloudinary-params
            {:edit-mode :crop
             :img-config {:bucket "my-bucket"
                          :filename "test/cat.jpg"
                          :params_by_size "IGNORED"}
             :crop-params {:x 2000
                           :y 300
                           :w 1980
                           :h 2220}
             :current-size {;; This is the limiting factor in the
                            ;; target-size calculation.
                            :width 1980
                            :height 9999
                            :hard false}})))))