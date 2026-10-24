//! What ash knows about the computer it runs on: which Windows, for a
//! report (spec 0005), and how much memory, for the game's default share
//! of it.
//!
//! Two Windows calls, each read-only, in the only `unsafe` code ash-core
//! has. Anywhere else, or when a call fails, the answer is "unknown" rather
//! than an error: nothing here is worth stopping for.

/// "Windows 11 Pro 24H2 (build 26200)", or what of it can be read.
pub fn windows_version() -> String {
    let product = imp::current_version("ProductName");
    let display = imp::current_version("DisplayVersion");
    let build = imp::current_version("CurrentBuildNumber");
    describe(product.as_deref(), display.as_deref(), build.as_deref())
}

/// The computer's physical memory in MB, if it can be read.
pub fn total_memory_mb() -> Option<u64> {
    imp::total_memory_mb()
}

/// Windows 11 still calls itself "Windows 10" in `ProductName`; its build
/// number, 22000 and up, is what tells them apart.
fn describe(product: Option<&str>, display: Option<&str>, build: Option<&str>) -> String {
    let eleven = build.and_then(|b| b.parse::<u32>().ok()).is_some_and(|b| b >= 22000);
    let mut name = product.unwrap_or("Windows").to_owned();
    if eleven {
        name = name.replacen("Windows 10", "Windows 11", 1);
    }
    let mut out = name;
    if let Some(display) = display {
        out.push(' ');
        out.push_str(display);
    }
    if let Some(build) = build {
        out.push_str(&format!(" (build {build})"));
    }
    out
}

#[cfg(windows)]
mod imp {
    use windows_sys::Win32::System::Registry::{RegGetValueW, HKEY_LOCAL_MACHINE, RRF_RT_REG_SZ};
    use windows_sys::Win32::System::SystemInformation::{GlobalMemoryStatusEx, MEMORYSTATUSEX};

    fn wide(text: &str) -> Vec<u16> {
        text.encode_utf16().chain(std::iter::once(0)).collect()
    }

    /// A string under `HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion`.
    pub fn current_version(value: &str) -> Option<String> {
        let key = wide(r"SOFTWARE\Microsoft\Windows NT\CurrentVersion");
        let name = wide(value);
        let mut buffer = [0u16; 256];
        let mut bytes = std::mem::size_of_val(&buffer) as u32;
        // SAFETY: the key and value names are NUL-terminated and outlive the
        // call; the buffer and its size in bytes describe the same memory.
        let status = unsafe {
            RegGetValueW(
                HKEY_LOCAL_MACHINE,
                key.as_ptr(),
                name.as_ptr(),
                RRF_RT_REG_SZ,
                std::ptr::null_mut(),
                buffer.as_mut_ptr().cast(),
                &mut bytes,
            )
        };
        if status != 0 {
            return None;
        }
        let chars = (bytes as usize / 2).min(buffer.len());
        let text = String::from_utf16_lossy(&buffer[..chars]);
        let text = text.trim_end_matches('\0').trim();
        (!text.is_empty()).then(|| text.to_owned())
    }

    pub fn total_memory_mb() -> Option<u64> {
        // SAFETY: MEMORYSTATUSEX is plain data, zero is a valid value for
        // every field, and dwLength is set as the call requires.
        let mut status: MEMORYSTATUSEX = unsafe { std::mem::zeroed() };
        status.dwLength = std::mem::size_of::<MEMORYSTATUSEX>() as u32;
        // SAFETY: `status` is a live, correctly sized MEMORYSTATUSEX.
        let ok = unsafe { GlobalMemoryStatusEx(&mut status) };
        (ok != 0).then_some(status.ullTotalPhys / (1024 * 1024))
    }
}

#[cfg(not(windows))]
mod imp {
    pub fn current_version(_value: &str) -> Option<String> {
        None
    }

    pub fn total_memory_mb() -> Option<u64> {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn windows_11_is_named_by_its_build_whatever_product_name_says() {
        assert_eq!(
            describe(Some("Windows 10 Pro"), Some("24H2"), Some("26200")),
            "Windows 11 Pro 24H2 (build 26200)"
        );
        assert_eq!(
            describe(Some("Windows 10 Home"), Some("22H2"), Some("19045")),
            "Windows 10 Home 22H2 (build 19045)"
        );
        assert_eq!(describe(None, None, None), "Windows");
    }

    #[cfg(windows)]
    #[test]
    fn this_computer_can_be_read() {
        assert!(windows_version().starts_with("Windows"), "{}", windows_version());
        assert!(total_memory_mb().is_some_and(|mb| mb >= 1024));
    }
}
