;; Core logic for cropping and resizing
(ns com.sitecrafting.cumulus.crop
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [com.sitecrafting.cumulus.cloudinary :as cloud]
   [re-frame.core :as rf]))


(comment
  ;; Evaluate any of these forms in your editor

  @(rf/subscribe [::crop-params])

  js/CUMULUS_CONFIG
  @(rf/subscribe [::img-config])
  ;; => {
  ;;     :attachment_id 26,
  ;;     :sizes
  ;;     [{:size_name "thumbnail", :width 150, :height 150}
  ;;      {:size_name "medium", :width 300, :height 300}
  ;;      {:size_name "medium_large", :width 768, :height 0}
  ;;      {:size_name "large", :width 1024, :height 1024}
  ;;      {:size_name "post-thumbnail", :width 1200, :height 9999}
  ;;      {:size_name "twentytwenty-fullscreen", :width 1980, :height 9999}],
  ;;     :bucket "ctamayo",
  ;;     :cloud "ctamayo",
  ;;     :params_by_size
  ;;     {:thumbnail {:edit_mode "crop", :crop {:x 1582, :y 81, :w 2421, :h 2421}},
  ;;      :medium {:edit_mode "crop", :crop {:x 1435, :y 164, :w 2419, :h 2419}},
  ;;      :large {:edit_mode "scale"}},
  ;;     :filename "cumulus-test/grasshopper.jpg",
  ;;     :full_url "https://res.cloudinary.com/ctamayo/image/upload/v1607377933/cumulus-test/grasshopper.jpg",
  ;;     :full_width 4032,
  ;;     :full_height 3024,
  ;;     :WP_DEBUG true}

  ;;
  )



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                       ;;
  ;;    Database config    ;;
 ;;                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(rf/reg-event-db
 ::init-db
 (fn [_ [_ config]]
   (let [config (keywordize-keys config)
         sizes (:sizes config)
         current-size (first sizes)
         {:keys [size_name]} current-size
         params (get-in config [:params_by_size (keyword size_name)])
         mode (keyword (:edit_mode params))
         crop (:crop params)]
     {:debug? (:WP_DEBUG config)
      :img-config config
      :current-size current-size
      :edit-mode mode
      :dimensions nil
      :crop-params crop
      :sizes sizes})))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                       ;;
  ;;     Core functions    ;;
 ;;                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Purely functional logic governing crops, sizes, params, etc.
;;

(defn- size->name [size]
  (keyword (:size_name size)))

(defn aspect-ratio [{:keys [current-size]}]
  (let [{:keys [width height]} current-size]
    (when (> height 0) (/ width height))))

(defn scaling-factor
  "Compute the natural -> rendered scaling factor for an accurate crop area."
  [{:keys [dimensions]}]
  (/ (:natural-width dimensions) (:rendered-width dimensions)))

(defn update-current-size [{:keys [img-config] :as db} [_ new-size]]
  (let [saved-size (get-in img-config [:params_by_size (size->name new-size)])
        ;; Default to scale mode
        saved-edit-mode (keyword (:edit_mode saved-size "scale"))
        saved-crop (:crop saved-size)]
    (assoc db
           :current-size new-size
           :edit-mode saved-edit-mode
           :crop-params saved-crop)))

(defn saved-params [{:keys [img-config current-size]}]
  (get-in img-config [:params_by_size (size->name current-size)]))

;; Compute the params to persist to the database given the current
;; edit mode.
(defmulti params-to-save #(:edit-mode % :scale))

(defmethod params-to-save :scale [_]
  {:edit_mode "scale"})

(defmethod params-to-save :crop [{:keys [crop-params]}]
  {:edit_mode "crop"
   :crop crop-params})

(defn cloudinary-params [{:keys [current-size
                                 edit-mode
                                 img-config
                                 crop-params]}]
  (merge {:mode edit-mode
          :target-size [(:width current-size)
                        (:height current-size)]}
         (select-keys img-config [:bucket :folder :filename])
         crop-params))

(defn set-crop-params [db [_ params]]
  (assoc db :crop-params params))

(defn unsaved-changes? [db]
  (not= (saved-params db) (params-to-save db)))

(defn db->update-sizes-config
  [{:keys [img-config current-size] :as db}]
  (assoc-in img-config
            [:params_by_size (size->name current-size)]
            (params-to-save db)))

(defn save-current-size [{:keys [db]}]
  (let [config (db->update-sizes-config db)]
    {:db (assoc db :img-config config)
     ::save-current-size! config}))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                              ;;
  ;;     Subscriptions/Effects    ;;
 ;;                              ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(rf/reg-event-db ::update-current-size update-current-size)
(rf/reg-event-fx ::save-current-size save-current-size)

(rf/reg-sub ::img-config :img-config)
(rf/reg-sub ::current-size :current-size)
(rf/reg-sub ::saved-params saved-params)
(rf/reg-sub ::params-to-save params-to-save)
(rf/reg-sub ::debug? :debug?)

(rf/reg-fx
 ::save-current-size!
 (fn [{:keys [attachment_id params_by_size]}]
   (-> (js/fetch (str "/wp-json/cumulus/v1/attachment/" attachment_id)
                 #js {:method "POST"
                      :headers #js {"content-type" "application/json"}
                      :body (js/JSON.stringify (clj->js params_by_size))})
       (.then (fn [response]
                #_(js/console.log response))))))

;; Edit mode (whether we're scaling vs. manually cropping)

(rf/reg-sub ::edit-mode :edit-mode)
(rf/reg-event-db ::update-edit-mode (fn [db [_ mode]]
                                      (assoc db :edit-mode mode)))

;; Dimensions

;; Inject current natural and rendered dimensions into db;
;; initialize crop params from saved data.
(rf/reg-event-fx
 ::image-loaded
 [(rf/inject-cofx :dimensions)]
 (fn [{:keys [dimensions] {size :current-size :as db} :db}]
   {:db (assoc db :dimensions dimensions)
    :dispatch [::update-current-size size]}))

(rf/reg-sub ::aspect-ratio aspect-ratio)
(rf/reg-sub ::dimensions :dimensions)
(rf/reg-sub ::scaling-factor scaling-factor)
(rf/reg-sub ::crop-params :crop-params)
(rf/reg-sub ::unsaved-changes? unsaved-changes?)
(rf/reg-event-db ::set-crop-params set-crop-params)

(rf/reg-sub ::full-url #(get-in % [:img-config :full_url]))

;; Compute the end result: the Cloudinary URL for our custom crop.

(rf/reg-sub ::cloudinary-params cloudinary-params)
(rf/reg-sub ::cloudinary-url #(cloud/url (cloudinary-params %)))