<?php

/**
 * Tests for the core API functions
 *
 * @copyright 2020 SiteCrafting, Inc.
 * @author    Coby Tamayo <ctamayo@sitecrafting.com>
 */

namespace Cumulus\Integration;

use SiteCrafting\Cumulus;

/**
 * Test the main Cumulus API functions.
 */
class ApiTest extends IntegrationTest {
  public function test_default_url() {
    $result = [
      'public_id' => 'test/cat',
      'format'    => 'jpg',
    ];
    $size = [
      'width'     => 150,
      'height'    => 150,
    ];

    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/w_150,h_150,c_lfill/test/cat.jpg",
      Cumulus\default_url('my-cloud', $size, $result)
    );
  }

  public function test_retina_url() {
    $img = [
      'cloudinary_data' => [
        'public_id'     => 'test/cat',
        'format'        => 'jpg',
      ],
      'params_by_size'  => [
        'thumbnail'     => [
          'edit_mode'   => 'scale',
        ],
      ],
    ];

    // Don't set a DPR param for the default size.
    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/w_150,h_150,c_lfill/test/cat.jpg",
      Cumulus\retina_url('my-cloud', 'thumbnail', $img, 1)
    );

    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/w_150,h_150,c_lfill,dpr_2/test/cat.jpg",
      Cumulus\retina_url('my-cloud', 'thumbnail', $img, 2)
    );
    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/w_150,h_150,c_lfill,dpr_3/test/cat.jpg",
      Cumulus\retina_url('my-cloud', 'thumbnail', $img, 3)
    );
  }

  public function test_retina_url_with_array_size() {
    $img = [
      'cloudinary_data' => [
        'public_id'     => 'test/cat',
        'format'        => 'jpg',
      ],
    ];

    $size = [
      'width'  => 500,
      'height' => 300,
    ];

    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/w_500,h_300,c_lfill,dpr_2/test/cat.jpg",
      Cumulus\retina_url('my-cloud', $size, $img, 2)
    );
  }

  public function test_retina_srcset_no_data() {
    $pid = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);

    $this->assertEquals('', Cumulus\retina_srcset($pid, 'thumbnail'));
  }

  public function test_retina_srcset_undefined_size() {
    $pid = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);
    update_post_meta($pid, 'cumulus_image', [
      'cloudinary_data' => [
        'public_id' => 'test/cat',
        'format'    => 'jpg',
      ],
    ]);

    $this->assertEquals('', Cumulus\retina_srcset($pid, 'foobarqux'));
  }

  public function test_retina_srcset() {
    $this->add_filter_temporarily('cumulus/settings', function() {
      return [
        'cloud_name' => 'my-cloud',
      ];
    });

    $pid = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);
    update_post_meta($pid, 'cumulus_image', [
      'cloudinary_data' => [
        'public_id'     => 'test/cat',
        'format'        => 'jpg',
      ],
      'params_by_size'  => [
        // srcset should honor these customizations at this size.
        'thumbnail'     => [
          'edit_mode'   => 'crop',
          'crop'        => [
            'x'         => 10,
            'y'         => 5,
            'w'         => 500,
            'h'         => 500,
          ],
        ],
        'medium'        => [
          'edit_mode'   => 'crop',
          'crop'        => [
            'x'         => 0,
            'y'         => 0,
            'w'         => 400,
            'h'         => 400,
          ],
        ],
        'large'         => [
          'edit_mode'   => 'scale',
        ],
      ],
    ]);

    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/x_10,y_5,w_500,h_500,c_crop/w_150,h_150,c_scale/test/cat.jpg 1x,"
      . "https://res.cloudinary.com/my-cloud/image/upload/x_10,y_5,w_500,h_500,c_crop/w_150,h_150,c_scale,dpr_2/test/cat.jpg 2x",
      Cumulus\retina_srcset($pid, 'thumbnail')
    );
    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/x_0,y_0,w_400,h_400,c_crop/w_300,h_300,c_scale/test/cat.jpg 1x,"
      . "https://res.cloudinary.com/my-cloud/image/upload/x_0,y_0,w_400,h_400,c_crop/w_300,h_300,c_scale,dpr_2/test/cat.jpg 2x",
      Cumulus\retina_srcset($pid, 'medium')
    );
    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/w_1024,h_1024,c_lfill/test/cat.jpg 1x,"
      . "https://res.cloudinary.com/my-cloud/image/upload/w_1024,h_1024,c_lfill,dpr_2/test/cat.jpg 2x",
      Cumulus\retina_srcset($pid, 'large')
    );
  }

  public function test_retina_srcset_missing_crop_data() {
    $this->add_filter_temporarily('cumulus/settings', function() {
      return [
        'cloud_name' => 'my-cloud',
      ];
    });

    $pid = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);
    update_post_meta($pid, 'cumulus_image', [
      'cloudinary_data' => [
        'public_id'     => 'test/cat',
        'format'        => 'jpg',
      ],
      // no params_by_size
    ]);

    $this->assertEquals('', Cumulus\retina_srcset($pid, 'thumbnail'));
  }

  public function test_retina_srcset_missing_size_data() {
    $this->add_filter_temporarily('cumulus/settings', function() {
      return [
        'cloud_name' => 'my-cloud',
      ];
    });

    $pid = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);
    update_post_meta($pid, 'cumulus_image', [
      'cloudinary_data' => [
        'public_id'     => 'test/cat',
        'format'        => 'jpg',
      ],
      // no data for this size
      'params_by_size'  => [],
    ]);

    $this->assertEquals('', Cumulus\retina_srcset($pid, 'thumbnail'));
  }

  public function test_retina_srcset_from_array() {
    $this->add_filter_temporarily('cumulus/settings', function() {
      return [
        'cloud_name' => 'my-cloud',
      ];
    });

    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/w_150,h_150,c_lfill/test/cat.jpg 1x,"
      . "https://res.cloudinary.com/my-cloud/image/upload/w_150,h_150,c_lfill,dpr_2/test/cat.jpg 2x",
      Cumulus\retina_srcset([
        'cloudinary_data' => [
          'public_id'     => 'test/cat',
          'format'        => 'jpg',
        ],
        'params_by_size'  => [
          'thumbnail'     => [
            'edit_mode'   => 'scale',
          ],
        ],
      ], 'thumbnail')
    );
  }

  public function test_retina_srcset_from_post() {
    $this->add_filter_temporarily('cumulus/settings', function() {
      return [
        'cloud_name' => 'my-cloud',
      ];
    });

    $pid = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);
    update_post_meta($pid, 'cumulus_image', [
      'cloudinary_data' => [
        'public_id' => 'test/cat',
        'format'    => 'jpg',
      ],
      'params_by_size'  => [
        'thumbnail'     => [
          'edit_mode'   => 'scale',
        ],
      ],
    ]);

    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/w_150,h_150,c_lfill/test/cat.jpg 1x,"
      . "https://res.cloudinary.com/my-cloud/image/upload/w_150,h_150,c_lfill,dpr_2/test/cat.jpg 2x",
      Cumulus\retina_srcset(get_post($pid), 'thumbnail')
    );
  }

  public function test_sizes() {
    $this->assertEquals(wp_get_registered_image_subsizes(), Cumulus\sizes());
  }

  public function test_sizes_with_filter() {
    $this->add_filter_temporarily('cumulus/sizes', function() {
      return ['thumbnail', 'medium'];
    });

    $this->assertEquals([
      'thumbnail' => [
        'width'   => 150,
        'height'  => 150,
        'crop'    => true,
      ],
      'medium'    => [
        'width'   => 300,
        'height'  => 300,
        'crop'    => false,
      ],
    ], Cumulus\sizes());
  }

  public function test_save_uploaded() {
    // Little hack to avoid a direct call to upload_attachment() - create
    // blog post instead.
    $pid = $this->factory->post->create([
      'post_type'  => 'post',
      'post_title' => 'Fake Image',
    ]);

    $this->add_filter_temporarily('cumulus/settings', function() {
      return ['cloud_name' => 'cumulustest'];
    });
    $this->add_filter_temporarily('cumulus/sizes', function() {
      return ['thumbnail', 'medium'];
    });

    $result = [
      'asset_id'          => '9e0d32c35631012fc75a65ab47feb2b1',
      'public_id'         => 'cumulus-test/centipede',
      'version'           => 1613522673,
      'version_id'        => '3323aaa59d61519e8589ab6cacfafcf0',
      'signature'         => '45dd0c194e7f609e6a8a892fffb73b9e87ac5214',
      'width'             => 3024,
      'height'            => 4032,
      'format'            => 'jpg',
      'resource_type'     => 'image',
      'created_at'        => '2021-02-17T00:44:33Z',
      'tags'              => [],
      'bytes'             => 8606598,
      'type'              => 'upload',
      'etag'              => '752f335d0019c7512e998d444bac2118',
      'placeholder'       => false,
      'url'               => 'http://res.cloudinary.com/cumulustest/image/upload/v1613522673/cumulus-test/centipede.jpg',
      'secure_url'        => 'https://res.cloudinary.com/cumulustest/image/upload/v1613522673/cumulus-test/centipede.jpg',
      'original_filename' => 'centipede',
    ];

    Cumulus\save_uploaded($pid, $result);

    $this->assertEquals([
      'cloudinary_id'   => 'cumulus-test/centipede',
      'urls_by_size'    => [
        'thumbnail'     => 'https://res.cloudinary.com/cumulustest/image/upload/w_150,h_150,c_lfill/cumulus-test/centipede.jpg',
        'medium'        => 'https://res.cloudinary.com/cumulustest/image/upload/w_300,h_300,c_lfill/cumulus-test/centipede.jpg',
        'full'          => 'https://res.cloudinary.com/cumulustest/image/upload/v1613522673/cumulus-test/centipede.jpg',
      ],
      'params_by_size'  => [
        'thumbnail'     => [
          'edit_mode'   => 'scale',
        ],
        'medium'        => [
          'edit_mode'   => 'scale',
        ],
      ],
      'cloudinary_data' => $result,
    ], get_post_meta($pid, 'cumulus_image', true));
  }

  public function test_save_uploaded_new_sizes() {
    // Little hack to avoid a direct call to upload_attachment() - create
    // blog post instead.
    $pid = $this->factory->post->create([
      'post_type'  => 'post',
      'post_title' => 'Fake Image',
    ]);

    $result = [
      'asset_id'          => '9e0d32c35631012fc75a65ab47feb2b1',
      'public_id'         => 'cumulus-test/centipede',
      'version'           => 1613522673,
      'version_id'        => '3323aaa59d61519e8589ab6cacfafcf0',
      'signature'         => '45dd0c194e7f609e6a8a892fffb73b9e87ac5214',
      'width'             => 3024,
      'height'            => 4032,
      'format'            => 'jpg',
      'resource_type'     => 'image',
      'created_at'        => '2021-02-17T00:44:33Z',
      'tags'              => [],
      'bytes'             => 8606598,
      'type'              => 'upload',
      'etag'              => '752f335d0019c7512e998d444bac2118',
      'placeholder'       => false,
      'url'               => 'http://res.cloudinary.com/cumulustest/image/upload/v1613522673/cumulus-test/centipede.jpg',
      'secure_url'        => 'https://res.cloudinary.com/cumulustest/image/upload/v1613522673/cumulus-test/centipede.jpg',
      'original_filename' => 'centipede',
    ];

    // Simulate an old upload with no image sizes declared.
    add_post_meta($pid, 'cumulus_image', [
      'cloudinary_data' => $result,
      'urls_by_size'    => [],
    ]);

    $this->add_filter_temporarily('cumulus/settings', function() {
      return ['cloud_name' => 'cumulustest'];
    });
    $this->add_filter_temporarily('cumulus/sizes', function() {
      return ['thumbnail', 'medium', 'custom'];
    });

    Cumulus\save_uploaded($pid, $result);

    $this->assertEquals([
      'cloudinary_id'   => 'cumulus-test/centipede',
      'urls_by_size'    => [
        'thumbnail'     => 'https://res.cloudinary.com/cumulustest/image/upload/w_150,h_150,c_lfill/cumulus-test/centipede.jpg',
        'medium'        => 'https://res.cloudinary.com/cumulustest/image/upload/w_300,h_300,c_lfill/cumulus-test/centipede.jpg',
        'full'          => 'https://res.cloudinary.com/cumulustest/image/upload/v1613522673/cumulus-test/centipede.jpg',
      ],
      'params_by_size'  => [
        'thumbnail'     => [
          'edit_mode'   => 'scale',
        ],
        'medium'        => [
          'edit_mode'   => 'scale',
        ],
      ],
      'cloudinary_data' => $result,
    ], get_post_meta($pid, 'cumulus_image', true));
  }
}
