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
 * Test central logic of how URLs are computed.
 */
class UrlTest extends IntegrationTest {
  public function test_src_with_false() {
    $this->assertFalse(apply_filters(
      'wp_get_attachment_image_src',
      false,
      123,
      'thumbnail'
    ));
  }

  public function test_src_with_missing_meta() {
    $id = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);

    $this->assertEquals(
      ['/path/to/image.jpg', 500, 500, true],
      apply_filters(
        'wp_get_attachment_image_src',
        ['/path/to/image.jpg', 500, 500, true],
        $id,
        'thumbnail'
      )
    );
  }

  public function test_src_with_meta() {
    $id = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);

    add_post_meta($id, 'cumulus_image', [
      'urls_by_size' => [
        'thumbnail'  => 'https://res.cloudinary.com/test/w_150,h_150/image.jpg',
        'full'       => 'https://res.cloudinary.com/test/image.jpg',
      ],
    ]);

    $this->assertEquals(
      ['https://res.cloudinary.com/test/w_150,h_150/image.jpg', 500, 500, true],
      apply_filters(
        'wp_get_attachment_image_src',
        ['/path/to/image.jpg', 500, 500, true],
        $id,
        'thumbnail'
      )
    );
  }

  public function test_src_with_array_size() {
    $id = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);

    add_post_meta($id, 'cumulus_image', [
      'urls_by_size' => [
        '900x901'    => 'https://res.cloudinary.com/test/w_900,h_901/image.jpg',
        'full'       => 'https://res.cloudinary.com/test/image.jpg',
      ],
    ]);

    $this->assertEquals(
      ['https://res.cloudinary.com/test/w_900,h_901/image.jpg', 900, 901, true],
      apply_filters(
        'wp_get_attachment_image_src',
        ['/path/to/image.jpg', 900, 901, true],
        $id,
        [900, 901]
      )
    );
  }

  public function test_srcset() {
    $this->add_filter_temporarily('cumulus/settings', function() {
      return [
        'cloud_name' => 'my-cloud',
      ];
    });

    $id = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);

    $cumulus_img = [
      'params_by_size' => [
        '900x901'    => 'https://res.cloudinary.com/test/w_900,h_901/image.jpg',
        'full'       => 'https://res.cloudinary.com/test/image.jpg',
      ],
      'cloudinary_data' => [
        'public_id' => 'test/image',
        'format' => 'jpg',
      ],
    ];
    add_post_meta($id, 'cumulus_image', $cumulus_img);

    // Under normal circumstances, $sources gets overridden and rebuilt from
    // scratch to use the `x` descriptor, for Retina URLs.
    $sources = [];

    $size = [1024, 682];
    $src  = 'https://example.com/image-1024x682.jpg';
    $meta = [
      'cumulus_image' => $cumulus_img,
    ];

    $this->assertEquals([
      [
        'url'        => 'https://res.cloudinary.com/my-cloud/image/upload/w_1024,h_682,c_lfill/test/image.jpg',
        'descriptor' => 'x',
        'value'      => 1,
      ],
      [
        'url'        => 'https://res.cloudinary.com/my-cloud/image/upload/w_1024,h_682,c_lfill,dpr_2/test/image.jpg',
        'descriptor' => 'x',
        'value'      => 2,
      ]
    ], apply_filters('wp_calculate_image_srcset', $sources, $size, $src, $meta, $id));
  }
}
