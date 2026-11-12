mod engine;

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

fn read(env: &mut JNIEnv, s: &JString) -> Result<String, String> {
    env.get_string(s).map(String::from).map_err(|e| e.to_string())
}

#[no_mangle]
pub extern "system" fn Java_info_mayuragarkar_calculator_engine_Native_evaluate<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    op: JString<'l>,
    x: JString<'l>,
    y: JString<'l>,
    z: JString<'l>,
) -> jstring {
    let result = (|| {
        let (op, x, y, z) = (read(&mut env, &op)?, read(&mut env, &x)?, read(&mut env, &y)?, read(&mut env, &z)?);
        std::panic::catch_unwind(|| engine::evaluate(&op, &x, &y, &z))
            .unwrap_or_else(|_| Err("Internal error in the calculation engine".into()))
    })();
    match result {
        Ok(text) => env.new_string(text).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Err(message) => {
            let _ = env.throw_new("info/mayuragarkar/calculator/engine/CalcError", message);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_info_mayuragarkar_calculator_engine_Native_cancel<'l>(_env: JNIEnv<'l>, _class: JClass<'l>) {
    engine::cancel();
}
