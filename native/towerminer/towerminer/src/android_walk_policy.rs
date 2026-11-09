// The register loop uses only baseline AArch64 integer instructions. Keep it
// on the measured GT5 identity; other architectures and phones retain Rust.
pub(crate) fn measured_device(model: &str, soc: &str, aarch64: bool) -> bool {
    aarch64 && model == "RMX3823" && soc == "SM8550"
}

#[cfg(target_os = "android")]
pub(crate) fn enabled() -> bool {
    static ENABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    *ENABLED.get_or_init(|| {
        measured_device(
            &std::env::var("GOZERO_DEVICE_MODEL").unwrap_or_default(),
            &std::env::var("GOZERO_SOC_MODEL").unwrap_or_default(),
            cfg!(target_arch = "aarch64"),
        )
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn exact_identity_and_architecture() {
        assert!(measured_device("RMX3823", "SM8550", true));
        assert!(!measured_device("RMX3823", "SM8550", false));
        assert!(!measured_device("RMX3820", "SM8550", true));
        assert!(!measured_device("RMX3823", "SM8650", true));
        assert!(!measured_device("", "", true));
    }
}
