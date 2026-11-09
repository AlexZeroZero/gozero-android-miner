use jetsam_core::Block128;
use jetsam_poseidon2b::batch::FixedFieldNonceBatch;
use jetsam_poseidon2b::native::domain::TAG_POWHDR;
use jetsam_poseidon2b::towerwalk::towerwalk_digest;
#[no_mangle]
pub unsafe extern "C" fn jtm_hash(fields:*const u8, nonce:*const u8, out:*mut u8)->i32 {
    if fields.is_null() || nonce.is_null() || out.is_null() { return 1; }
    let f=std::slice::from_raw_parts(fields,256);
    let n=std::slice::from_raw_parts(nonce,16);
    let values:[Block128;16]=std::array::from_fn(|i|Block128::from(u128::from_le_bytes(f[i*16..i*16+16].try_into().unwrap())));
    let mut h=FixedFieldNonceBatch::new(TAG_POWHDR,&values,0);
    let mut seed=[[0u8;32];1];h.hash_into(u128::from_le_bytes(n.try_into().unwrap()),&mut seed);
    let digest=towerwalk_digest(&seed[0]);std::ptr::copy_nonoverlapping(digest.as_ptr(),out,32);0
}
