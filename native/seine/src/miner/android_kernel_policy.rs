// Only the realme GT5 / SM8550 has a paired Android comparison. ISA support
// remains an independent requirement; neighbouring devices keep their default.
pub(super) fn prefer_sha3(model: &str, soc: &str, sha3_supported: bool) -> bool {
    model == "RMX3823" && soc == "SM8550" && sha3_supported
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn measured_identity_and_isa_are_both_required() {
        assert!(prefer_sha3("RMX3823", "SM8550", true));
        assert!(!prefer_sha3("RMX3823", "SM8550", false));
        assert!(!prefer_sha3("RMX3820", "SM8550", true));
        assert!(!prefer_sha3("RMX3823", "SM8650", true));
        assert!(!prefer_sha3("", "", true));
    }
}
