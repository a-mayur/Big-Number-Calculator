use malachite::base::num::arithmetic::traits::{
    BinomialCoefficient, DivExact, DivRem, DivisibleBy, ExtendedGcd, Factorial, Gcd, Lcm, Mod, ModInverse, ModPow,
    Parity, Pow, Square, UnsignedAbs,
};
use malachite::base::num::basic::traits::{One, Zero};
use malachite::base::num::conversion::traits::ToStringBase;
use malachite::base::num::logic::traits::{CountOnes, SignificantBits};
use malachite::{Integer, Natural};
use std::cmp::Ordering;
use std::f64::consts::{LN_10, LOG10_2, PI};
use std::str::FromStr;
use std::sync::atomic::{AtomicU64, Ordering::SeqCst};

type Res<T> = Result<T, String>;

const MAX_DIGITS: u64 = 10_000_000;
const DIVISION_SCALE: u64 = 100;

static GENERATION: AtomicU64 = AtomicU64::new(0);

pub fn cancel() {
    GENERATION.fetch_add(1, SeqCst);
}

struct Cancel(u64);

impl Cancel {
    fn start() -> Self {
        Cancel(GENERATION.fetch_add(1, SeqCst) + 1)
    }
    fn check(&self) -> Res<()> {
        if GENERATION.load(SeqCst) == self.0 { Ok(()) } else { Err("Cancelled".into()) }
    }
}

pub fn evaluate(op: &str, x: &str, y: &str, z: &str) -> Res<String> {
    let c = Cancel::start();
    match op {
        "ADD" => Ok(dec(x, "x")?.add(&dec(y, "y")?).to_string()),
        "SUBTRACT" => Ok(dec(x, "x")?.add(&dec(y, "y")?.neg()).to_string()),
        "MULTIPLY" => {
            let (a, b) = (dec(x, "x")?, dec(y, "y")?);
            check_digits(a.digits() + b.digits())?;
            Ok(a.mul(&b).to_string())
        }
        "DIVIDE" => {
            let (a, b) = (dec(x, "x")?, dec(y, "y")?);
            if b.m == 0 {
                return Err("Can't divide by zero".into());
            }
            Ok(a.div(&b, DIVISION_SCALE).to_string())
        }
        "COMPARE" => Ok(match dec(x, "x")?.cmp(&dec(y, "y")?) {
            Ordering::Less => "x < y",
            Ordering::Equal => "x = y",
            Ordering::Greater => "x > y",
        }
        .into()),
        "MOD" => {
            let m = positive(int(y, "y")?, "y")?;
            Ok(int(x, "x")?.mod_op(Integer::from(m)).to_string())
        }
        "MOD_POW" => mod_pow(int(x, "x")?, int(y, "y")?, positive(int(z, "z")?, "z")?),
        "MOD_INVERSE" => inverse(&int(x, "x")?, &positive(int(y, "y")?, "y")?).map(|v| v.to_string()),
        "GCD" => gcd(&numbers(&[x, y, z])?),
        "LCM" => Ok(numbers(&[x, y, z])?
            .into_iter()
            .fold(Natural::ONE, |acc, n| acc.lcm(n.unsigned_abs()))
            .to_string()),
        "IS_PRIME" => {
            let n = int(x, "x")?;
            Ok(if n >= 2 && is_prime(&n.unsigned_abs(), &c)? { "Prime" } else { "Not prime" }.into())
        }
        "NEXT_PRIME" => next_prime(int(x, "x")?, &c),
        "PREV_PRIME" => prev_prime(int(x, "x")?, &c),
        "SHIFT_LEFT" => shift(int(x, "x")?, int(y, "y")?),
        "SHIFT_RIGHT" => shift(int(x, "x")?, -int(y, "y")?),
        "POWER" => power(&dec(x, "x")?, y),
        "SQUARE" => power(&dec(x, "x")?, "2"),
        "CUBE" => power(&dec(x, "x")?, "3"),
        "FACTORIAL" => {
            let n = small(int(x, "x")?, "x")?;
            check_digits(log10_factorial(n))?;
            Ok(Natural::factorial(n).to_string())
        }
        "LN" => {
            let a = dec(x, "x")?;
            if a.m <= 0 {
                return Err("ln needs x greater than 0".into());
            }
            Ok(approx(a.log10_abs() * LN_10, 15))
        }
        "NCR" | "NPR" => {
            let (n, k) = (small(int(x, "x")?, "x")?, small(int(y, "y")?, "y")?);
            if k > n {
                return Ok("0".into());
            }
            let ln = log10_factorial(n) - log10_factorial(n - k);
            if op == "NCR" {
                check_digits(ln - log10_factorial(k))?;
                Ok(Natural::binomial_coefficient(Natural::from(n), Natural::from(k)).to_string())
            } else {
                check_digits(ln)?;
                Ok((Natural::binomial_coefficient(Natural::from(n), Natural::from(k)) * Natural::factorial(k)).to_string())
            }
        }
        "CATALAN" => {
            let n = small(int(x, "x")?, "x")?;
            let two_n = n.checked_mul(2).ok_or_else(too_big)?;
            check_digits(log10_factorial(two_n) - 2.0 * log10_factorial(n))?;
            Ok(Natural::binomial_coefficient(Natural::from(two_n), Natural::from(n))
                .div_exact(Natural::from(n + 1))
                .to_string())
        }
        "FIBONACCI" => {
            let n = small(int(x, "x")?, "x")?;
            check_digits(n as f64 * 0.208_987_640_249_978_7)?;
            Ok(fibonacci(n, &c)?.to_string())
        }
        "TO_BASE" => {
            let n = int(x, "x")?;
            let base = int(y, "y")?;
            if !(2..=36).contains(&base) {
                return Err("The base (y) must be from 2 to 36".into());
            }
            let base = u8::try_from(&base).unwrap();
            check_digits(n.significant_bits() as f64 / (base as f64).log2())?;
            Ok(n.to_string_base_upper(base))
        }
        "BITS" => bits(int(x, "x")?),
        "CHECK_DIGITS" => check_digit_text(x),
        _ => Err(format!("Unknown operation {op}")),
    }
}

fn too_big() -> String {
    format!("The result would be too large (the limit is {} digits)", group(MAX_DIGITS))
}

fn check_digits(digits: f64) -> Res<()> {
    if digits > MAX_DIGITS as f64 {
        Err(format!(
            "The result would have about {} digits (the limit is {})",
            group(digits.min(u64::MAX as f64) as u64),
            group(MAX_DIGITS)
        ))
    } else {
        Ok(())
    }
}

fn group(n: u64) -> String {
    let s = n.to_string();
    let mut out = String::new();
    for (i, ch) in s.chars().enumerate() {
        if i > 0 && (s.len() - i) % 3 == 0 {
            out.push(',');
        }
        out.push(ch);
    }
    out
}

struct Dec {
    m: Integer,
    s: u64,
}

fn dec(text: &str, name: &str) -> Res<Dec> {
    let t = text.trim();
    if t.is_empty() {
        return Err(format!("Enter a value for {name}"));
    }
    if t.len() as u64 > MAX_DIGITS + 2 {
        return Err(format!("{name} has more than {} digits", group(MAX_DIGITS)));
    }
    let (neg, body) = match t.as_bytes()[0] {
        b'-' => (true, &t[1..]),
        b'+' => (false, &t[1..]),
        _ => (false, t),
    };
    let (int_part, frac_part) = body.split_once('.').unwrap_or((body, ""));
    let valid = !(int_part.is_empty() && frac_part.is_empty())
        && int_part.bytes().all(|b| b.is_ascii_digit())
        && frac_part.bytes().all(|b| b.is_ascii_digit());
    if !valid {
        return Err(format!("{name} isn't a valid number"));
    }
    let frac = frac_part.trim_end_matches('0');
    let digits = format!("{int_part}{frac}");
    let m = if digits.is_empty() { Integer::ZERO } else { Integer::from_str(&digits).unwrap() };
    Ok(Dec { m: if neg { -m } else { m }, s: frac.len() as u64 })
}

fn int(text: &str, name: &str) -> Res<Integer> {
    let d = dec(text, name)?;
    if d.s != 0 {
        return Err(format!("{name} must be a whole number"));
    }
    Ok(d.m)
}

fn positive(n: Integer, name: &str) -> Res<Natural> {
    if n <= 0 {
        return Err(format!("{name} must be greater than 0"));
    }
    Ok(n.unsigned_abs())
}

fn small(n: Integer, name: &str) -> Res<u64> {
    if n < 0 {
        return Err(format!("{name} must be 0 or more"));
    }
    u64::try_from(&n).map_err(|_| too_big())
}

fn numbers(fields: &[&str]) -> Res<Vec<Integer>> {
    let mut out = Vec::new();
    for field in fields {
        for token in field.split(|ch: char| ch == ',' || ch.is_whitespace()).filter(|t| !t.is_empty()) {
            out.push(int(token, token)?);
        }
    }
    if out.len() < 2 {
        return Err("Enter at least two numbers, in x, y and z or as a list like 12, 18, 30".into());
    }
    Ok(out)
}

fn pow10(n: u64) -> Integer {
    Integer::from(10u32).pow(n)
}

impl Dec {
    fn neg(self) -> Dec {
        Dec { m: -self.m, s: self.s }
    }

    fn aligned(&self, o: &Dec) -> (Integer, Integer) {
        let s = self.s.max(o.s);
        (&self.m * pow10(s - self.s), &o.m * pow10(s - o.s))
    }

    fn add(&self, o: &Dec) -> Dec {
        let (a, b) = self.aligned(o);
        Dec { m: a + b, s: self.s.max(o.s) }
    }

    fn mul(&self, o: &Dec) -> Dec {
        Dec { m: &self.m * &o.m, s: self.s + o.s }
    }

    fn div(&self, o: &Dec, scale: u64) -> Dec {
        let num = &self.m * pow10(scale + o.s);
        let den = &o.m * pow10(self.s);
        let away = (num < 0) != (den < 0);
        let (q, r) = num.div_rem(&den);
        let q = if (r.unsigned_abs() << 1u32) >= den.unsigned_abs() {
            if away { q - Integer::ONE } else { q + Integer::ONE }
        } else {
            q
        };
        Dec { m: q, s: scale }
    }

    fn cmp(&self, o: &Dec) -> Ordering {
        let (a, b) = self.aligned(o);
        a.cmp(&b)
    }

    fn digits(&self) -> f64 {
        self.m.significant_bits() as f64 * LOG10_2 + 1.0
    }

    fn log10_abs(&self) -> f64 {
        log10(&(&self.m).unsigned_abs()) - self.s as f64
    }
}

impl std::fmt::Display for Dec {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        let digits = (&self.m).unsigned_abs().to_string();
        if self.m < 0 {
            f.write_str("-")?;
        }
        let s = self.s as usize;
        if s == 0 {
            return f.write_str(&digits);
        }
        let padded = if digits.len() <= s { "0".repeat(s + 1 - digits.len()) + &digits } else { digits };
        let (whole, frac) = padded.split_at(padded.len() - s);
        let frac = frac.trim_end_matches('0');
        f.write_str(whole)?;
        if !frac.is_empty() {
            write!(f, ".{frac}")?;
        }
        Ok(())
    }
}

fn log10(n: &Natural) -> f64 {
    let bits = n.significant_bits();
    if bits == 0 {
        return f64::NEG_INFINITY;
    }
    let drop = bits.saturating_sub(64);
    let top = u64::try_from(&(n >> drop)).unwrap();
    (top as f64).log10() + drop as f64 * LOG10_2
}

fn log10_factorial(n: u64) -> f64 {
    if n < 2 {
        return 0.0;
    }
    let n = n as f64;
    (n * n.ln() - n + 0.5 * (2.0 * PI * n).ln()) / LN_10
}

fn approx(v: f64, sig: usize) -> String {
    if v == 0.0 {
        return "≈ 0".into();
    }
    let sci = format!("{:.*e}", sig - 1, v);
    let (mantissa, exp) = sci.split_once('e').unwrap();
    let exp: i64 = exp.parse().unwrap();
    let digits: String = mantissa.chars().filter(|ch| ch.is_ascii_digit()).collect();
    let mut m = Integer::from_str(&digits).unwrap();
    if v < 0.0 {
        m = -m;
    }
    let shift = exp - (sig as i64 - 1);
    let d = if shift >= 0 { Dec { m: m * pow10(shift as u64), s: 0 } } else { Dec { m, s: (-shift) as u64 } };
    format!("≈ {d}")
}

fn power(a: &Dec, y: &str) -> Res<String> {
    let b = dec(y, "y")?;
    if b.s != 0 {
        return approx_power(a, &b, y);
    }
    let n = b.m;
    if a.m == 0 {
        if n < 0 {
            return Err("0 can't be raised to a negative power".into());
        }
        return Ok(if n == 0 { "1" } else { "0" }.into());
    }
    if a.s == 0 && (&a.m).unsigned_abs() == 1u32 {
        return Ok(if a.m < 0 && n.odd() { "-1" } else { "1" }.into());
    }
    let count = y.trim().parse::<f64>().unwrap_or(f64::INFINITY).abs();
    check_digits(count * a.log10_abs().max(0.0) + count * a.s as f64)?;
    let e = u64::try_from(&(&n).unsigned_abs()).map_err(|_| too_big())?;
    let p = Dec { m: (&a.m).pow(e), s: a.s * e };
    Ok(if n >= 0 { p.to_string() } else { Dec { m: Integer::ONE, s: 0 }.div(&p, DIVISION_SCALE).to_string() })
}

fn approx_power(a: &Dec, b: &Dec, y: &str) -> Res<String> {
    if a.m < 0 {
        return Err("A negative number to a fractional power isn't a real number".into());
    }
    if a.m == 0 {
        if b.m < 0 {
            return Err("0 can't be raised to a negative power".into());
        }
        return Ok("0".into());
    }
    let l = y.trim().parse::<f64>().unwrap_or(f64::INFINITY) * a.log10_abs();
    if !l.is_finite() {
        return Err("The result is too large to estimate".into());
    }
    let e = l.floor();
    let sig = (15 - (e.abs() + 1.0).log10() as i64).clamp(6, 15) as usize;
    if e.abs() < 15.0 {
        return Ok(approx(10f64.powf(l), sig));
    }
    let mantissa = format!("{:.*}", sig - 1, 10f64.powf(l - e));
    let mantissa = mantissa.trim_end_matches('0').trim_end_matches('.');
    Ok(format!("≈ {mantissa}e{}", e as i64))
}

fn shift(n: Integer, by: Integer) -> Res<String> {
    if n == 0 {
        return Ok("0".into());
    }
    if by >= 0 {
        let k = u64::try_from(&by).map_err(|_| too_big())?;
        check_digits((n.significant_bits() as f64 + k as f64) * LOG10_2)?;
        return Ok((n << k).to_string());
    }
    Ok(match u64::try_from(&-by) {
        Ok(k) => (n >> k).to_string(),
        Err(_) => if n < 0 { "-1" } else { "0" }.into(),
    })
}

fn reduce(n: &Integer, m: &Natural) -> Natural {
    n.mod_op(Integer::from(m)).unsigned_abs()
}

fn inverse(n: &Integer, m: &Natural) -> Res<Natural> {
    if *m == 1u32 {
        return Ok(Natural::ZERO);
    }
    reduce(n, m).mod_inverse(m).ok_or_else(|| "No inverse: x and y share a common factor".into())
}

fn mod_pow(base: Integer, exp: Integer, m: Natural) -> Res<String> {
    if m == 1u32 {
        return Ok("0".into());
    }
    let b = if exp < 0 { inverse(&base, &m)? } else { reduce(&base, &m) };
    Ok(b.mod_pow(exp.unsigned_abs(), m).to_string())
}

fn paren(i: &Integer) -> String {
    if *i < 0 { format!("({i})") } else { i.to_string() }
}

fn gcd(nums: &[Integer]) -> Res<String> {
    if let [a, b] = nums {
        let (g, s, t) = a.extended_gcd(b);
        return Ok(format!("{g}\n\nBézout: {} × {} + {} × {} = {g}", paren(a), paren(&s), paren(b), paren(&t)));
    }
    Ok(nums.iter().fold(Natural::ZERO, |acc, n| acc.gcd(n.unsigned_abs())).to_string())
}

fn small_primes() -> Vec<u32> {
    (2u32..1000).filter(|&n| (2..n).take_while(|d| d * d <= n).all(|d| n % d != 0)).collect()
}

fn is_prime(n: &Natural, c: &Cancel) -> Res<bool> {
    if *n < 2u32 {
        return Ok(false);
    }
    for p in small_primes() {
        if *n == p {
            return Ok(true);
        }
        if n.divisible_by(Natural::from(p)) {
            return Ok(false);
        }
    }
    if *n < 1_000_000u32 {
        return Ok(true);
    }
    let n1 = n - Natural::ONE;
    let r = n1.trailing_zeros().unwrap();
    let d = &n1 >> r;
    let mut seed = 0x9E37_79B9_7F4A_7C15u64 ^ n.significant_bits();
    let random = (0..20).map(move |_| {
        seed ^= seed << 13;
        seed ^= seed >> 7;
        seed ^= seed << 17;
        seed | 2
    });
    'bases: for a in [2u64, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37].into_iter().chain(random) {
        c.check()?;
        let a = Natural::from(a) % n;
        if a < 2u32 {
            continue;
        }
        let mut x = a.mod_pow(&d, n);
        if x == 1u32 || x == n1 {
            continue;
        }
        for _ in 1..r {
            x = x.square() % n;
            if x == n1 {
                continue 'bases;
            }
        }
        return Ok(false);
    }
    Ok(true)
}

fn next_prime(n: Integer, c: &Cancel) -> Res<String> {
    if n < 2 {
        return Ok("2".into());
    }
    let mut p = n.unsigned_abs() + Natural::ONE;
    if p.even() {
        p += Natural::ONE;
    }
    while !is_prime(&p, c)? {
        c.check()?;
        p += Natural::from(2u32);
    }
    Ok(p.to_string())
}

fn prev_prime(n: Integer, c: &Cancel) -> Res<String> {
    if n <= 2 {
        return Err("There's no prime below 2".into());
    }
    if n == 3 {
        return Ok("2".into());
    }
    let mut p = n.unsigned_abs() - Natural::ONE;
    if p.even() {
        p -= Natural::ONE;
    }
    while !is_prime(&p, c)? {
        c.check()?;
        p -= Natural::from(2u32);
    }
    Ok(p.to_string())
}

fn fibonacci(n: u64, c: &Cancel) -> Res<Natural> {
    let (mut a, mut b) = (Natural::ZERO, Natural::ONE);
    for i in (0..u64::BITS - n.leading_zeros()).rev() {
        c.check()?;
        let f2k = &a * ((&b << 1u32) - &a);
        let f2k1 = (&a).square() + (&b).square();
        (a, b) = if (n >> i) & 1 == 1 { (f2k1.clone(), f2k + f2k1) } else { (f2k, f2k1) };
    }
    Ok(a)
}

fn bits(n: Integer) -> Res<String> {
    let tz = |v: &Natural| v.trailing_zeros().map_or("–".into(), |t| t.to_string());
    if n >= 0 {
        let u = n.unsigned_abs();
        return Ok(format!("Bit length: {}\nOnes: {}\nTrailing zeros: {}", u.significant_bits(), (&u).count_ones(), tz(&u)));
    }
    let abs = n.unsigned_abs();
    let width = ((&abs - Natural::ONE).significant_bits() + 1).div_ceil(8) * 8;
    check_digits(width as f64)?;
    let tc = (Natural::ONE << width) - abs;
    Ok(format!(
        "Two's complement width: {width} bits\nOnes: {}\nTrailing zeros: {}\n\n{:0>w$}",
        (&tc).count_ones(),
        tz(&tc),
        tc.to_string_base(2),
        w = width as usize
    ))
}

fn verdict(name: &str, ok: bool, expected: &str) -> String {
    if ok { format!("{name}: valid") } else { format!("{name}: invalid (check digit should be {expected})") }
}

fn check_digit_text(text: &str) -> Res<String> {
    let s: String = text.chars().filter(|ch| !ch.is_whitespace() && *ch != '-').collect::<String>().to_ascii_uppercase();
    let b = s.as_bytes();
    if s.is_empty() {
        return Err("Enter a card number, ISBN or IBAN in x".into());
    }
    if (15..=34).contains(&b.len())
        && b[..2].iter().all(u8::is_ascii_uppercase)
        && b[2..4].iter().all(u8::is_ascii_digit)
        && b.iter().all(u8::is_ascii_alphanumeric)
    {
        return Ok(iban(&s));
    }
    let digits_only = b.iter().all(u8::is_ascii_digit);
    let isbn10_shape = b.len() == 10 && b[..9].iter().all(u8::is_ascii_digit) && (b[9].is_ascii_digit() || b[9] == b'X');
    if !digits_only && !isbn10_shape {
        return Err("Check digits work on digits (card numbers, ISBNs) or an IBAN".into());
    }
    let d: Vec<u32> = b.iter().map(|&ch| if ch == b'X' { 10 } else { (ch - b'0') as u32 }).collect();
    let mut lines = Vec::new();
    if b.len() == 10 {
        let sum: u32 = d[..9].iter().zip((2..=10).rev()).map(|(v, w)| v * w).sum();
        let want = (11 - sum % 11) % 11;
        lines.push(verdict("ISBN-10", d[9] == want, &if want == 10 { "X".into() } else { want.to_string() }));
    }
    if b.len() == 13 {
        let sum: u32 = d[..12].iter().enumerate().map(|(i, v)| if i % 2 == 0 { *v } else { v * 3 }).sum();
        let want = (10 - sum % 10) % 10;
        lines.push(verdict("ISBN-13 / EAN-13", d[12] == want, &want.to_string()));
    }
    if digits_only && b.len() >= 2 {
        let sum: u32 = d[..d.len() - 1]
            .iter()
            .rev()
            .enumerate()
            .map(|(i, &v)| if i % 2 == 0 { if v * 2 > 9 { v * 2 - 9 } else { v * 2 } } else { v })
            .sum();
        let want = (10 - sum % 10) % 10;
        lines.push(verdict("Luhn (cards, IMEI)", d[d.len() - 1] == want, &want.to_string()));
    }
    Ok(lines.join("\n"))
}

fn iban(s: &str) -> String {
    let mod97 = |t: &str| {
        t.bytes().fold(0u32, |r, ch| match ch {
            b'0'..=b'9' => (r * 10 + (ch - b'0') as u32) % 97,
            _ => (r * 100 + (ch - b'A' + 10) as u32) % 97,
        })
    };
    let ok = mod97(&format!("{}{}", &s[4..], &s[..4])) == 1;
    let want = 98 - mod97(&format!("{}{}00", &s[4..], &s[..2]));
    if ok { "IBAN: valid".into() } else { format!("IBAN: invalid (check digits should be {want:02})") }
}
