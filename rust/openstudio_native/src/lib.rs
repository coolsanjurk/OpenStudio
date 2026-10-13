use jni::objects::{JIntArray, JObject};
use jni::sys::{jfloat, jint};
use jni::JNIEnv;

fn clamp_channel(value: f32) -> i32 {
    value.round().clamp(0.0, 255.0) as i32
}

/// Apply brightness, contrast, saturation and warmth to ARGB pixels in-place.
/// This runs entirely on-device; no network or file access is performed.
#[no_mangle]
pub extern "system" fn Java_com_openstudio_photo_NativeImageProcessor_nativeApplyFilters(
    mut env: JNIEnv,
    _this: JObject,
    pixels: JIntArray,
    brightness: jfloat,
    contrast: jfloat,
    saturation: jfloat,
    warmth: jfloat,
) {
    let Ok(length) = env.get_array_length(&pixels) else { return; };
    let mut data: Vec<jint> = vec![0; length as usize];
    if env.get_int_array_region(&pixels, 0, &mut data).is_err() {
        return;
    }

    for pixel in data.iter_mut() {
        let argb = *pixel as u32;
        let a = ((argb >> 24) & 0xff) as i32;
        let r = ((argb >> 16) & 0xff) as f32;
        let g = ((argb >> 8) & 0xff) as f32;
        let b = (argb & 0xff) as f32;

        // Match the app's existing brightness/contrast/warmth intent.
        let offset = brightness * 255.0;
        let r = (r - 127.5) * contrast + 127.5 + offset + warmth;
        let g = (g - 127.5) * contrast + 127.5 + offset;
        let b = (b - 127.5) * contrast + 127.5 + offset - warmth;

        // Luma-based saturation adjustment.
        let luma = 0.213 * r + 0.715 * g + 0.072 * b;
        let r = luma + (r - luma) * saturation;
        let g = luma + (g - luma) * saturation;
        let b = luma + (b - luma) * saturation;

        *pixel = ((a << 24)
            | (clamp_channel(r) << 16)
            | (clamp_channel(g) << 8)
            | clamp_channel(b)) as jint;
    }

    let _ = env.set_int_array_region(&pixels, 0, &data);
}
