(ns com.sitecrafting.cumulus.core
  (:require
   [com.sitecrafting.cumulus.crop :as crop]
   [reagent.dom :as dom]
   [re-frame.core :as rf]))


;; start is called by init and after code reloading finishes
(defn ^:dev/after-load mount-root []
  (rf/clear-subscription-cache!)
  (dom/render [crop/crop-ui]
              (js/document.getElementById "cumulus-crop-ui")))

(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (rf/dispatch-sync [::crop/init-db (js->clj js/CUMULUS_CONFIG)])
  (mount-root))
