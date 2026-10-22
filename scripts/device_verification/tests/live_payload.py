import sys,time,os,signal
from pathlib import Path
if len(sys.argv)>3 and sys.argv[3]!='plain':
 child=os.fork()
 if child==0:
  signal.signal(signal.SIGTERM,signal.SIG_IGN);Path(sys.argv[3]).write_text(str(os.getpid()));time.sleep(15);os._exit(0)
time.sleep(float(sys.argv[1]))
raise SystemExit(int(sys.argv[2]))
