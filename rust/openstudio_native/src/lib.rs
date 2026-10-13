use jni::objects::{JIntArray, JObject};
use jni::sys::{jfloat, jint};
use jni::JNIEnv;

fn clamp_channel(value: f32) -> i32 { value.round().clamp(0.0, 255.0) as i32 }

fn adjust_pixel(pixel: u32, brightness: f32, contrast: f32, saturation: f32, warmth: f32) -> u32 {
    let a = ((pixel >> 24) & 0xff) as i32;
    let r = ((pixel >> 16) & 0xff) as f32;
    let g = ((pixel >> 8) & 0xff) as f32;
    let b = (pixel & 0xff) as f32;
    let offset = brightness * 255.0;
    let r = (r - 127.5) * contrast + 127.5 + offset + warmth;
    let g = (g - 127.5) * contrast + 127.5 + offset;
    let b = (b - 127.5) * contrast + 127.5 + offset - warmth;
    let luma = 0.213 * r + 0.715 * g + 0.072 * b;
    let r = luma + (r - luma) * saturation;
    let g = luma + (g - luma) * saturation;
    let b = luma + (b - luma) * saturation;
    ((a << 24) | (clamp_channel(r) << 16) | (clamp_channel(g) << 8) | clamp_channel(b)) as u32
}

#[no_mangle]
pub extern "system" fn Java_com_openstudio_photo_NativeImageProcessor_nativeApplyFilters(
    mut env: JNIEnv, _this: JObject, pixels: JIntArray,
    brightness: jfloat, contrast: jfloat, saturation: jfloat, warmth: jfloat,
) {
    let Ok(length) = env.get_array_length(&pixels) else { return; };
    let mut data: Vec<jint> = vec![0; length as usize];
    if env.get_int_array_region(&pixels, 0, &mut data).is_err() { return; }
    for pixel in data.iter_mut() {
        *pixel = adjust_pixel(*pixel as u32, brightness, contrast, saturation, warmth) as jint;
    }
    let _ = env.set_int_array_region(&pixels, 0, &data);
}

#[cfg(test)]
mod tests {
    use super::adjust_pixel;

    #[test]
    fn neutral_settings_preserve_pixel() {
        let pixel = 0xff336699;
        assert_eq!(adjust_pixel(pixel, 0.0, 1.0, 1.0, 0.0), pixel);
    }

    #[test]
    fn brightness_increases_rgb_without_changing_alpha() {
        let pixel = 0x7f204060;
        let result = adjust_pixel(pixel, 0.2, 1.0, 1.0, 0.0);
        assert_eq!((result >> 24) & 0xff, 0x7f);
        assert!(((result >> 16) & 0xff) > ((pixel >> 16) & 0xff));
        assert!(((result >> 8) & 0xff) > ((pixel >> 8) & 0xff));
        assert!((result & 0xff) > (pixel & 0xff));
    }

    #[test]
    fn output_channels_stay_in_byte_range() {
        let result = adjust_pixel(0xff010203, 2.0, 4.0, 3.0, 100.0);
        for shift in [0, 8, 16, 24] { assert!(((result >> shift) & 0xff) <= 255); }
    }
}
