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

  function _reloadConfig() {
    if (
      com && com.sitecrafting && com.sitecrafting.cumulus && com.sitecrafting.cumulus.core
      && typeof (com.sitecrafting.cumulus.core.init) === 'function'
    ) {
      com.sitecrafting.cumulus.core.reload_config(CUMULUS_CONFIG);
    }
  }

  /**
   * Do a bunch of hack jQuery stuff to get the attachment data we need
   * and render the Edit Image Crops UI.
   */
  function _initCumulusCropsUi() {
    if (!$('.attachment-actions').length) {
      return;
    }
    var $customizeImageCropsBtn = $('.cumulus-edit-crops-btn');

    $customizeImageCropsBtn = $('<button type="button"></button>')
      .addClass('button cumulus-edit-crops-btn')
      .text('<?= $data['customize_crops'] ?>');

    // Get the attachment ID from the URL
    if (location.search) {
      var matches = /item=([0-9]+)/.exec(location.search);
      if (matches.length > 1) {
        $.ajax('/wp-json/cumulus/v1/attachment/' + matches[1], {
          success: function(data) {
            if (!data.uploaded) {
              // Cloudinary settings do not apply to this image; bail.
              return;
            }

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
            CUMULUS_CONFIG.urls_by_size   = data.urls_by_size;
            CUMULUS_CONFIG.nonce          = data.nonce;

            _reloadConfig();

            var $actions = $('.attachment-actions').first();
            if ($actions.length && !$actions.find('.cumulus-edit-crops-btn').length) {
              $actions.append($customizeImageCropsBtn);
            }
          },
        });
      }
    } else {
      console.log('TODO figure out how to deal with this situation...');
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

        $cumulusUi.find('.media-modal-close--cumulus').click(function(e) {
          $cumulusUi.hide();
          $modal.show();
          return false;
        });
      }

      // Mount our CLJS/React app into the container.
      _mountReactApp();
    });
  }

  var observer = new MutationObserver(function(mutationList, observer) {
    // The DOM is now ready for use to inject our UI!
    _initCumulusCropsUi();

    $('.edit-media-header button').each(function() {
      var $this = $(this);
      if ($this.data('cumulusWatching')) {
        return;
      }
      $(this).on('click', function() {
        _initCumulusCropsUi();
      });
      $this.data('cumulusWatching', 1);
    });
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