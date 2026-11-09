//! Offline paired diagnostic: both paths reuse exactly the same physical arena.
//! Separate process allocations can change cache behaviour on a phone.
#![allow(dead_code)]
#[path = "../src/walk.rs"]
mod walk;
mod android_walk_policy {
    pub static ENABLED: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
    pub fn enabled() -> bool { ENABLED.load(std::sync::atomic::Ordering::Relaxed) }
}
fn main() {
    assert!(cfg!(all(target_arch="aarch64",target_os="android")));
    use std::sync::atomic::Ordering;
    let mut arena=vec![0u64;walk::PAD_BYTES/8];
    let pads=[arena.as_mut_ptr()];
    let seeds:Vec<_>=(0..64u64).map(|i| {
        let mut s=[0u8;32];
        let mut n=i.wrapping_add(0x9e3779b97f4a7c15);
        for chunk in s.chunks_exact_mut(8) {
            n^=n>>12;n^=n<<25;n^=n>>27;
            chunk.copy_from_slice(&n.wrapping_mul(0x2545f4914f6cdd1d).to_le_bytes());
        }
        [s]
    }).collect();
    let mut output=[[0u8;32];1];
    let mut scratch=jetsam_poseidon2b::towerwalk::Scratch::new();
    for seed in &seeds {
        let expected=jetsam_poseidon2b::towerwalk::towerwalk_digest_with(&mut scratch,&seed[0]);
        for mode in [false,true] {
            android_walk_policy::ENABLED.store(mode,Ordering::Relaxed);
            unsafe { walk::walk::<1,false,{walk::K_BASE}>(&pads,seed,&mut output); }
            assert_eq!(output[0],expected);
        }
    }
    println!("PARITY PASS 64 seeds, both paths, same arena");
    for (round,mode) in [false,true,true,false,true,false,false,true].into_iter().enumerate() {
        android_walk_policy::ENABLED.store(mode,Ordering::Relaxed);
        for i in 0..128 { unsafe {walk::walk::<1,false,{walk::K_BASE}>(&pads,&seeds[i%64],&mut output);} }
        let start=std::time::Instant::now();
        for i in 0..2048 {
            unsafe {walk::walk::<1,false,{walk::K_BASE}>(&pads,&seeds[i%64],&mut output);}
            std::hint::black_box(&output);
        }
        println!("PAIRED round={round} register={mode} hashes=2048 hps={:.4}",2048.0/start.elapsed().as_secs_f64());
    }
}
