#!/bin/zsh
# Makes (or resets) the owner's account on the Worker: asks the e-mail and a PIN of four to six digits (hidden),
# writes u:<email> with the PIN as PBKDF2 (the same as account.js), signs every TV out of it (a new PIN after a leaked
# one must also end the sign-ins made with it), lifts its block, and sets the ADMIN_EMAIL secret. Failed PIN tries
# live in the Worker's Guard and clear themselves within 15 minutes. Signing in never makes the owner's account, so
# nobody can claim that e-mail first.
# Run it in a real terminal: it reads the keyboard. Needs `npx wrangler login` once.
set -e
cd "$(dirname "$0")"
NS=$(sed -n 's/^id = "\([0-9a-f]*\)"$/\1/p' wrangler.toml | head -1)
read -r "E?E-mail адміна: "
E=$(printf '%s' "$E" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')
[[ "$E" == *@*.* ]] || { echo "Це не e-mail." >&2; exit 1; }
read -rs "P?PIN (4-6 цифр, не показується): "; echo
[[ "$P" =~ '^[0-9]{4,6}$' ]] || { echo "PIN має бути з 4-6 цифр." >&2; exit 1; }
umask 077
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
PIN="$P" node -e '
const c = require("crypto");
const salt = c.randomBytes(16).toString("hex");
const hash = c.pbkdf2Sync(process.env.PIN, salt, 100000, 32, "sha256").toString("hex");
process.stdout.write(JSON.stringify({ salt, hash, created: Date.now() }));' > "$TMP"
unset P
npx -y wrangler@4 kv key put --namespace-id "$NS" --remote "u:$E" --path "$TMP" >/dev/null
# Each sign-in is t:<hash> -> e-mail, indexed as x:<email>:<hash> (account.js newToken).
for X in $(npx -y wrangler@4 kv key list --namespace-id "$NS" --remote --prefix "x:$E:" \
    | node -e 'let s="";process.stdin.on("data",(d)=>s+=d).on("end",()=>JSON.parse(s.slice(s.indexOf("["))).forEach((k)=>console.log(k.name)))'); do
  npx -y wrangler@4 kv key delete --namespace-id "$NS" --remote "t:${X##*:}" >/dev/null
  npx -y wrangler@4 kv key delete --namespace-id "$NS" --remote "$X" >/dev/null
done
npx -y wrangler@4 kv key delete --namespace-id "$NS" --remote "b:$E" >/dev/null 2>&1 || true
printf '%s' "$E" | npx -y wrangler@4 secret put ADMIN_EMAIL >/dev/null
echo "Готово: $E тепер адмін. Увійди на телевізорі з цим PIN."
