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
}
