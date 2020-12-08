<script>
jQuery(function($){
  function _template(selector) {
    return $(selector).html();
  }

  function _mountReactApp() {
    if (
      com && com.sitecrafting && com.sitecrafting.cumulus && com.sitecrafting.cumulus.core
      && typeof (com.sitecrafting.cumulus.core.init) === 'function'
    ) {
      com.sitecrafting.cumulus.core.init(CUMULUS_CONFIG);
    }
  }

  /**
   * Do a bunch of hack jQuery stuff to get the attachment data we need
   * and render the Edit Image Crops UI.
   */
  function _initCumulusCropsUi() {
    var $customizeImageCropsBtn = $('.cumulus-edit-crops-btn');

    // Check if the current Customize Image Crops button corresponds to the
    // image currently being viewed in the modal
    if ($customizeImageCropsBtn.length) {
      // We're already done.
      return;
    }

    $customizeImageCropsBtn = $('<button type="button"></button>')
      .addClass('button cumulus-edit-crops-btn')
      .text('<?= $data['customize_crops'] ?>');

    var $actions = $('.attachment-actions').first();

    // Get the attachment ID from the URL
    if (location.search) {
      var matches = /item=([0-9]+)/.exec(location.search);
      if (matches.length > 1) {
        // TODO for some reason it's sending multiple requests??
        $.ajax('/wp-json/cumulus/v1/attachment/' + matches[1], {
          success: function(data) {
            // Check again in case the UI was already loaded.
            if ($('.cumulus-edit-crops-btn').length) {
              return;
            }

            // Enrich the UI config with attachment-specific data.
            CUMULUS_CONFIG.attachment_id  = data.attachment_id;
            CUMULUS_CONFIG.full_url       = data.full_url;
            CUMULUS_CONFIG.full_width     = data.full_width;
            CUMULUS_CONFIG.full_height    = data.full_height;
            CUMULUS_CONFIG.filename       = data.filename;
            CUMULUS_CONFIG.params_by_size = data.params_by_size;

            $actions.append($customizeImageCropsBtn);
          },
        });
      }
    }

    $customizeImageCropsBtn.click(function() {
      var $modal = $(this).closest('.media-modal-content');
      $modal.addClass('media-modal-content--original').hide();

      var $cumulusUi = $('.media-modal-content--cumulus');

      if ($cumulusUi.length) {
        $cumulusUi.show();
      } else {
        // Build and inject modal container markup.
        $cumulusUi = $(_template('#cumulus-template-edit-media'))
        $modal.after($cumulusUi);

        // Mount our CLJS/React app into the container.
        _mountReactApp();

        $cumulusUi.find('.media-modal-close--cumulus').click(function(e) {
          $cumulusUi.hide();
          $modal.show();
          return false;
        });
      }
    });
  }

  var observer = new MutationObserver(function(mutationList, observer) {
    if ($('.attachment-actions').length) {
      // The DOM is now ready for use to inject our UI!
      _initCumulusCropsUi();
    }
  });

  // TODO fall back on DOMNodeInserted?

  // Watch for elements being inserted in the DOM, or attributes changing.
  observer.observe(document.body, { attributes: true, childList: true });
});
</script>
<script type="text/html" id="cumulus-template-edit-media">
  <div class="media-modal-content media-modal-content--cumulus">
    <div class="edit-attachment-frame hide-menu hide-router">
      <div class="edit-media-header">
        <button type="button" class="media-modal-close media-modal-close--cumulus">
          <span class="media-modal-icon">
            <span class="screen-reader-text"><?= $data['back_to_details'] ?></span>
          </span>
        </button>
      </div>
      <div class="media-frame-title"><h1><?= $data['customize_crops'] ?></h1></div>
      <div class="media-frame-content">
        <div id="cumulus-crop-ui"></div>
      </div>
    </div>
  </div>
</script>